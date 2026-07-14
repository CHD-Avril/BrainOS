package com.brainos.knowledge.api;

import jakarta.validation.constraints.NotNull;

public record KnowledgeBaseUpdateRequest(
        @NotNull String name,
        String description) {}
