package com.brainos.common.api;

import com.brainos.auth.application.AuthenticationFailedException;
import com.brainos.auth.token.InvalidRefreshTokenException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        return response(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        return response(ErrorCode.CONFLICT);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(ApiException exception) {
        return response(exception.errorCode());
    }

    @ExceptionHandler({AuthenticationFailedException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailure(RuntimeException exception) {
        return response(ErrorCode.UNAUTHORIZED);
    }

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode, UUID.randomUUID().toString()));
    }
}
