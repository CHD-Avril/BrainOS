package com.brainos.knowledge.api;

import jakarta.validation.constraints.NotNull;

public record KnowledgeBaseCreateRequest(
        @NotNull String name,
        String description) {}
