package com.brainos.admin.user;

import com.brainos.auth.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(@NotNull UserStatus status) {}
