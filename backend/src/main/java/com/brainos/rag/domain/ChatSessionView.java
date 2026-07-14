package com.brainos.rag.domain;

import com.brainos.rag.model.ChatModelType;
import java.time.Instant;

public record ChatSessionView(
        long id,
        String title,
        long knowledgeBaseId,
        ChatModelType chatModel,
        long userId,
        Instant createdAt,
        Instant updatedAt) {}
