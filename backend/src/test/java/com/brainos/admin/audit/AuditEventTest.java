package com.brainos.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void removesCredentialBearingSummaryBeforePersistence() {
        AuditEvent event = new AuditEvent(
                1L,
                "USER_UPDATE",
                "USER",
                "2",
                "SUCCESS",
                "password=Secret123 authorization=Bearer token");

        assertThat(event.summary())
                .isEqualTo("内容已脱敏")
                .doesNotContain("Secret123", "Bearer", "password", "authorization");
    }
}
