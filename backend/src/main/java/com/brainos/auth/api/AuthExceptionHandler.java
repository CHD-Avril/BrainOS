package com.brainos.auth.api;

import com.brainos.auth.application.AuthenticationFailedException;
import com.brainos.auth.token.InvalidRefreshTokenException;
import com.brainos.common.api.ApiResponse;
import com.brainos.common.api.ErrorCode;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler({AuthenticationFailedException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailure(RuntimeException exception) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status())
                .body(ApiResponse.failure(
                        ErrorCode.UNAUTHORIZED, UUID.randomUUID().toString()));
    }
}
