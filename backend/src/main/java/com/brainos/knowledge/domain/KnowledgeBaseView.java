package com.brainos.knowledge.domain;

import java.time.Instant;

public record KnowledgeBaseView(
        long id,
        String name,
        String description,
        long createdBy,
        long documentCount,
        long readyDocumentCount,
        Instant createdAt,
        Instant updatedAt) {}
