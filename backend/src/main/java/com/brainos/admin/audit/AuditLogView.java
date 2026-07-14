package com.brainos.admin.audit;

import java.time.Instant;

public record AuditLogView(
        long id,
        Long userId,
        String username,
        String action,
        String targetType,
        String targetId,
        String result,
        String summary,
        Instant createdAt) {}
