package com.brainos.common.api;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        return response(ErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(
            HttpMessageNotReadableException exception) {
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

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
                .body(ApiResponse.failure(errorCode, UUID.randomUUID().toString()));
    }
}
