package com.brainos.dashboard;

import com.brainos.document.domain.DocumentStatus;
import java.time.Instant;

public record RecentDocument(
        long id,
        long knowledgeBaseId,
        String knowledgeBaseName,
        String originalName,
        DocumentStatus status,
        Instant updatedAt) {}
