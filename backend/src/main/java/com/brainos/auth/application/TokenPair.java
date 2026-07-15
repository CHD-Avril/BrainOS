package com.brainos.auth.application;

import com.brainos.auth.domain.UserSummary;

public record TokenPair(String accessToken, String refreshToken, UserSummary user) {}
