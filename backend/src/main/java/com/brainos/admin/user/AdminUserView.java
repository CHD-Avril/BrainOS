package com.brainos.admin.user;

import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import java.time.Instant;

public record AdminUserView(
        long id,
        String username,
        String displayName,
        UserRole role,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {}
