package com.brainos.rag.api;

import com.brainos.rag.model.ChatModelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatSessionCreateRequest(
        @Positive long knowledgeBaseId, @NotNull ChatModelType chatModel) {}
