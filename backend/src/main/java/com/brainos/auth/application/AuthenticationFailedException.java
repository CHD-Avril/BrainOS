package com.brainos.auth.application;

public final class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("用户名或密码错误");
    }
}
