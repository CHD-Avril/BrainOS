package com.brainos.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.common.api.ApiException;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import com.brainos.knowledge.domain.KnowledgeBaseView;
import com.brainos.rag.domain.ChatMessageEntity;
import com.brainos.rag.domain.ChatSessionEntity;
import com.brainos.rag.domain.ChatSessionView;
import com.brainos.rag.model.ChatModelType;
import com.brainos.rag.persistence.ChatMessageMapper;
import com.brainos.rag.persistence.ChatSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatSessionServiceTest {

    private final ChatSessionMapper sessions = mock(ChatSessionMapper.class);
    private final ChatMessageMapper messages = mock(ChatMessageMapper.class);
    private final KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
    private final ChatSessionService service =
            new ChatSessionService(sessions, messages, knowledgeBases, new ObjectMapper());

    @Test
    void createsSessionBoundToKnowledgeBaseModelAndOwner() {
        when(knowledgeBases.findById(7L)).thenReturn(Optional.of(knowledge()));
        doAnswer(invocation -> {
                    invocation.getArgument(0, ChatSessionEntity.class).assignId(11L);
                    return null;
                })
                .when(sessions)
                .create(any(ChatSessionEntity.class));
        when(sessions.findOwned(11L, 9L)).thenReturn(Optional.of(session("新会话")));

        ChatSessionView created = service.create(7L, ChatModelType.QWEN, 9L);

        assertThat(created.knowledgeBaseId()).isEqualTo(7L);
        assertThat(created.chatModel()).isEqualTo(ChatModelType.QWEN);
        assertThat(created.userId()).isEqualTo(9L);
    }

    @Test
    void userCannotReadAnotherUsersSession() {
        when(sessions.findOwned(11L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(11L, 10L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void firstQuestionSetsUnicodeSafeTitleAndPersistsUserMessage() {
        when(sessions.findOwned(11L, 9L)).thenReturn(Optional.of(session("新会话")));
        String question = "企业员工年假制度具体规定是什么以及如何申请审批请详细说明";

        service.saveUserMessage(11L, 9L, question);

        verify(sessions).rename(11L, 9L, "企业员工年假制度具体规定是什么以及如何申请审批请");
        verify(messages).create(any(ChatMessageEntity.class));
        verify(sessions).touch(11L);
    }

    @Test
    void deleteChecksOwnershipBeforeCascadeDelete() {
        when(sessions.findOwned(11L, 9L)).thenReturn(Optional.of(session("制度")));
        when(sessions.deleteOwned(11L, 9L)).thenReturn(1);

        service.delete(11L, 9L);

        verify(sessions).deleteOwned(11L, 9L);
    }

    private static ChatSessionView session(String title) {
        return new ChatSessionView(
                11L, title, 7L, ChatModelType.QWEN, 9L, Instant.EPOCH, Instant.EPOCH);
    }

    private static KnowledgeBaseView knowledge() {
        return new KnowledgeBaseView(
                7L, "员工制度", null, 1L, 0L, 0L, Instant.EPOCH, Instant.EPOCH);
    }
}
