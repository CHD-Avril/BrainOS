package com.brainos.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.rag.domain.ChatSessionView;
import com.brainos.rag.model.ChatModelRouter;
import com.brainos.rag.model.ChatModelType;
import com.brainos.rag.model.RagPromptFactory;
import com.brainos.rag.retrieval.CitationCandidate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class RagChatServiceTest {

    private final ChatSessionService sessions = mock(ChatSessionService.class);
    private final RagPlanningService planning = mock(RagPlanningService.class);
    private final RagPromptFactory prompts = mock(RagPromptFactory.class);
    private final ChatModelRouter models = mock(ChatModelRouter.class);
    private final RagChatService service = new RagChatService(sessions, planning, prompts, models);

    @Test
    void streamsGroundedAnswerThenPersistsCompletedAssistantMessage() {
        CitationCandidate citation = citation();
        Prompt prompt = mock(Prompt.class);
        when(sessions.required(11L, 9L)).thenReturn(session());
        when(planning.plan(7L, "年假规定")).thenReturn(RagAnswerPlan.grounded(List.of(citation)));
        when(prompts.create("年假规定", List.of(citation))).thenReturn(prompt);
        when(models.stream(ChatModelType.QWEN, prompt)).thenReturn(Flux.just("每年", "5天"));

        StepVerifier.create(service.ask(11L, 9L, "年假规定"))
                .assertNext(event -> assertThat(event.type()).isEqualTo("start"))
                .assertNext(event -> assertThat(event.content()).isEqualTo("每年"))
                .assertNext(event -> assertThat(event.content()).isEqualTo("5天"))
                .assertNext(event -> assertThat(event.citations()).containsExactly(citation))
                .assertNext(event -> assertThat(event.type()).isEqualTo("done"))
                .verifyComplete();

        verify(sessions).saveUserMessage(11L, 9L, "年假规定");
        verify(sessions).saveAssistantMessage(11L, 9L, "每年5天", List.of(citation));
    }

    @Test
    void returnsStableFallbackWithoutCallingModel() {
        when(sessions.required(11L, 9L)).thenReturn(session());
        when(planning.plan(7L, "不存在的规定"))
                .thenReturn(RagAnswerPlan.fallback(RagPlanningService.NO_RELIABLE_EVIDENCE));

        StepVerifier.create(service.ask(11L, 9L, "不存在的规定"))
                .expectNextMatches(event -> event.type().equals("start"))
                .expectNextMatches(event -> RagPlanningService.NO_RELIABLE_EVIDENCE.equals(event.content()))
                .expectNextMatches(event -> event.type().equals("citations"))
                .expectNextMatches(event -> event.type().equals("done"))
                .verifyComplete();

        verify(models, never()).stream(any(), any());
        verify(sessions).saveAssistantMessage(
                11L, 9L, RagPlanningService.NO_RELIABLE_EVIDENCE, List.of());
    }

    @Test
    void modelFailureEmitsSanitizedErrorAndDoesNotPersistPartialAnswer() {
        CitationCandidate citation = citation();
        Prompt prompt = mock(Prompt.class);
        when(sessions.required(11L, 9L)).thenReturn(session());
        when(planning.plan(7L, "年假规定")).thenReturn(RagAnswerPlan.grounded(List.of(citation)));
        when(prompts.create("年假规定", List.of(citation))).thenReturn(prompt);
        when(models.stream(ChatModelType.QWEN, prompt))
                .thenReturn(Flux.concat(Flux.just("部分"), Flux.error(new RuntimeException("secret"))));

        StepVerifier.create(service.ask(11L, 9L, "年假规定"))
                .expectNextMatches(event -> event.type().equals("start"))
                .expectNextMatches(event -> "部分".equals(event.content()))
                .assertNext(event -> {
                    assertThat(event.type()).isEqualTo("error");
                    assertThat(event.message()).doesNotContain("secret");
                })
                .verifyComplete();

        verify(sessions, never()).saveAssistantMessage(eq(11L), eq(9L), any(), any());
    }

    @Test
    void rejectsForeignSessionBeforeCreatingStreamOrSavingQuestion() {
        when(sessions.required(11L, 10L)).thenThrow(new ApiException(ErrorCode.NOT_FOUND));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.ask(11L, 10L, "年假规定"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(sessions, never()).saveUserMessage(anyLong(), anyLong(), any());
        verify(planning, never()).plan(anyLong(), any());
    }

    private static ChatSessionView session() {
        return new ChatSessionView(
                11L, "年假", 7L, ChatModelType.QWEN, 9L, Instant.EPOCH, Instant.EPOCH);
    }

    private static CitationCandidate citation() {
        return new CitationCandidate(7L, 21L, "chunk-1", "员工手册.pdf", 3, 0, "年假5天", 0.91d);
    }
}
