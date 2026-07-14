package com.brainos.rag.application;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import com.brainos.rag.domain.ChatMessageEntity;
import com.brainos.rag.domain.ChatMessageRow;
import com.brainos.rag.domain.ChatMessageView;
import com.brainos.rag.domain.ChatSessionDetail;
import com.brainos.rag.domain.ChatSessionEntity;
import com.brainos.rag.domain.ChatSessionView;
import com.brainos.rag.model.ChatModelType;
import com.brainos.rag.persistence.ChatMessageMapper;
import com.brainos.rag.persistence.ChatSessionMapper;
import com.brainos.rag.retrieval.CitationCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ChatSessionService {

    private static final String NEW_SESSION_TITLE = "新会话";

    private final ChatSessionMapper sessions;
    private final ChatMessageMapper messages;
    private final KnowledgeBaseRepository knowledgeBases;
    private final ObjectMapper objectMapper;

    public ChatSessionService(
            ChatSessionMapper sessions,
            ChatMessageMapper messages,
            KnowledgeBaseRepository knowledgeBases,
            ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.messages = messages;
        this.knowledgeBases = knowledgeBases;
        this.objectMapper = objectMapper;
    }

    public ChatSessionView create(long knowledgeBaseId, ChatModelType model, long userId) {
        if (model == null || knowledgeBases.findById(knowledgeBaseId).isEmpty()) {
            throw new ApiException(model == null ? ErrorCode.VALIDATION_ERROR : ErrorCode.NOT_FOUND);
        }
        ChatSessionEntity session =
                new ChatSessionEntity(null, NEW_SESSION_TITLE, knowledgeBaseId, model, userId);
        sessions.create(session);
        return required(session.getId(), userId);
    }

    public List<ChatSessionView> list(long userId) {
        return sessions.findAllByUserId(userId);
    }

    public ChatSessionDetail get(long sessionId, long userId) {
        ChatSessionView session = required(sessionId, userId);
        List<ChatMessageView> history = messages.findAllBySessionId(sessionId).stream()
                .map(this::toView)
                .toList();
        return new ChatSessionDetail(session, history);
    }

    public ChatSessionView required(long sessionId, long userId) {
        return sessions.findOwned(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    public ChatSessionView rename(long sessionId, long userId, String rawTitle) {
        required(sessionId, userId);
        String title = normalizeTitle(rawTitle);
        if (sessions.rename(sessionId, userId, title) != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return required(sessionId, userId);
    }

    @Transactional
    public void delete(long sessionId, long userId) {
        required(sessionId, userId);
        if (sessions.deleteOwned(sessionId, userId) != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public void saveUserMessage(long sessionId, long userId, String question) {
        ChatSessionView session = required(sessionId, userId);
        String content = normalizeQuestion(question);
        if (NEW_SESSION_TITLE.equals(session.title())) {
            sessions.rename(sessionId, userId, titleFromQuestion(content));
        }
        messages.create(new ChatMessageEntity(null, sessionId, "USER", content, null));
        sessions.touch(sessionId);
    }

    @Transactional
    public void saveAssistantMessage(
            long sessionId,
            long userId,
            String content,
            List<CitationCandidate> citations) {
        required(sessionId, userId);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("assistant message must not be blank");
        }
        messages.create(new ChatMessageEntity(
                null, sessionId, "ASSISTANT", content, serializeCitations(citations)));
        sessions.touch(sessionId);
    }

    private ChatMessageView toView(ChatMessageRow row) {
        return new ChatMessageView(
                row.id(),
                row.sessionId(),
                row.role(),
                row.content(),
                deserializeCitations(row.citationsJson()),
                row.createdAt());
    }

    private String serializeCitations(List<CitationCandidate> citations) {
        if (citations == null || citations.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(new CitationPayload(1, citations));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("引用序列化失败", exception);
        }
    }

    private List<CitationCandidate> deserializeCitations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            CitationPayload payload = objectMapper.readValue(json, CitationPayload.class);
            if (payload.version() != 1 || payload.items() == null) {
                throw new IllegalStateException("不支持的引用版本");
            }
            return List.copyOf(payload.items());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("引用数据损坏", exception);
        }
    }

    private static String normalizeTitle(String rawTitle) {
        if (rawTitle == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        String title = rawTitle.trim();
        if (title.isEmpty() || title.length() > 60) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return title;
    }

    private static String normalizeQuestion(String rawQuestion) {
        if (rawQuestion == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        String question = rawQuestion.trim();
        if (question.isEmpty() || question.length() > 1000) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return question;
    }

    private static String titleFromQuestion(String question) {
        int codePoints = question.codePointCount(0, question.length());
        if (codePoints <= 24) return question;
        int end = question.offsetByCodePoints(0, 24);
        return question.substring(0, end);
    }

    private record CitationPayload(int version, List<CitationCandidate> items) {}
}
