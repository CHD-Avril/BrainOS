package com.brainos.admin.user;

import com.brainos.auth.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 32) String username,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull UserRole role) {}
