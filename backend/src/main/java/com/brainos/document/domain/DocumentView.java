package com.brainos.document.domain;

import java.time.Instant;

public record DocumentView(
        Long id,
        Long knowledgeBaseId,
        String originalName,
        String storagePath,
        String mimeType,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        int chunkCount,
        String failureReason,
        Long uploadedBy,
        Instant createdAt,
        Instant updatedAt) {}
