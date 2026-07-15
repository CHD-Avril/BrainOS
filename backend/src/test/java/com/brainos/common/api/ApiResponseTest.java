package com.brainos.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void successContainsStableEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ready");
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void failureContainsErrorAndProvidedTraceId() {
        ApiResponse<Void> response = ApiResponse.failure(ErrorCode.CONFLICT, "trace-123");

        assertThat(response.code()).isEqualTo("CONFLICT");
        assertThat(response.message()).isEqualTo("Resource conflict");
        assertThat(response.data()).isNull();
        assertThat(response.traceId()).isEqualTo("trace-123");
        assertThat(response.timestamp()).isNotNull();
    }
}
