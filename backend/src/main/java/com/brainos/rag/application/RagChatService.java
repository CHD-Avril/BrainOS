package com.brainos.rag.application;

import com.brainos.rag.domain.ChatSessionView;
import com.brainos.rag.model.ChatModelRouter;
import com.brainos.rag.model.RagPromptFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RagChatService {

    private static final String GENERATION_ERROR = "回答生成失败，请稍后重试";

    private final ChatSessionService sessions;
    private final RagPlanningService planning;
    private final RagPromptFactory prompts;
    private final ChatModelRouter models;

    public RagChatService(
            ChatSessionService sessions,
            RagPlanningService planning,
            RagPromptFactory prompts,
            ChatModelRouter models) {
        this.sessions = sessions;
        this.planning = planning;
        this.prompts = prompts;
        this.models = models;
    }

    public Flux<ChatStreamEvent> ask(long sessionId, long userId, String rawQuestion) {
        ChatSessionView session = sessions.required(sessionId, userId);
        sessions.saveUserMessage(sessionId, userId, rawQuestion);
        RagAnswerPlan plan = planning.plan(session.knowledgeBaseId(), rawQuestion);
        Flux<ChatStreamEvent> answer = plan.isFallback()
                ? fallback(sessionId, userId, plan)
                : grounded(session, userId, rawQuestion, plan);
        return Flux.concat(Flux.just(ChatStreamEvent.start()), answer)
                .onErrorResume(ignored -> Flux.just(ChatStreamEvent.error(GENERATION_ERROR)));
    }

    private Flux<ChatStreamEvent> fallback(
            long sessionId, long userId, RagAnswerPlan plan) {
        return Flux.defer(() -> {
            sessions.saveAssistantMessage(sessionId, userId, plan.fallback(), plan.citations());
            return Flux.just(
                    ChatStreamEvent.delta(plan.fallback()),
                    ChatStreamEvent.citations(plan.citations()),
                    ChatStreamEvent.done());
        });
    }

    private Flux<ChatStreamEvent> grounded(
            ChatSessionView session, long userId, String question, RagAnswerPlan plan) {
        StringBuilder answer = new StringBuilder();
        AtomicBoolean emitted = new AtomicBoolean();
        Flux<ChatStreamEvent> deltas = Flux.defer(() -> models.stream(
                        session.chatModel(), prompts.create(question.trim(), plan.citations())))
                .filter(part -> part != null && !part.isEmpty())
                .doOnNext(part -> {
                    answer.append(part);
                    emitted.set(true);
                })
                .map(ChatStreamEvent::delta);
        Flux<ChatStreamEvent> completion = Flux.defer(() -> {
            if (!emitted.get() || answer.toString().isBlank()) {
                return Flux.just(ChatStreamEvent.error(GENERATION_ERROR));
            }
            sessions.saveAssistantMessage(
                    session.id(), userId, answer.toString(), plan.citations());
            return Flux.just(
                    ChatStreamEvent.citations(plan.citations()), ChatStreamEvent.done());
        });
        return deltas.concatWith(completion);
    }
}
