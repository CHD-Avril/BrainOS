package com.brainos.auth.token;

public final class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("刷新令牌无效");
    }
}
