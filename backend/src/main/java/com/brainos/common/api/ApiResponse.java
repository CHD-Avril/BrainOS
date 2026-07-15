package com.brainos.common.api;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(String code, String message, T data, String traceId, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, UUID.randomUUID().toString(), Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode error, String traceId) {
        return new ApiResponse<>(error.code(), error.message(), null, traceId, Instant.now());
    }
}
