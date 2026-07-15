package com.brainos.rag.domain;

import java.time.Instant;

public record ChatMessageRow(
        long id,
        long sessionId,
        String role,
        String content,
        String citationsJson,
        Instant createdAt) {}
