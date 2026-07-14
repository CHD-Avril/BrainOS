package com.brainos.auth.domain;

public record UserSummary(long id, String username, String displayName, UserRole role) {}
