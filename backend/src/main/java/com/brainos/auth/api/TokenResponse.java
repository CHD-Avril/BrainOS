package com.brainos.auth.api;

import com.brainos.auth.application.TokenPair;
import com.brainos.auth.domain.UserSummary;

public record TokenResponse(String accessToken, String refreshToken, UserSummary user) {

    public static TokenResponse from(TokenPair tokenPair) {
        return new TokenResponse(
                tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.user());
    }
}
