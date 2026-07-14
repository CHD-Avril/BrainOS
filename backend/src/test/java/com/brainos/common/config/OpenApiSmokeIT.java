package com.brainos.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSmokeIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("brainos")
            .withUsername("brainos")
            .withPassword("test-only-password")
            .withReuse(false);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "brainos.auth.jwt.secret",
                () -> "test-only-openapi-jwt-secret-at-least-32-bytes");
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired MockMvc mvc;

    @Test
    void documentsAllDefenseApisWithJwtSchemeAndWithoutCredentialValues() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json)
                .contains("BrainOS Enterprise Knowledge Base API")
                .contains("bearerAuth")
                .contains("/api/v1/auth/login")
                .contains("/api/v1/knowledge-bases/{knowledgeBaseId}/documents")
                .contains("/api/v1/chat/sessions/{sessionId}/messages/stream")
                .contains("/api/v1/dashboard/summary")
                .contains("/api/v1/admin/users")
                .contains("/api/v1/admin/audit-logs")
                .doesNotContain("QWEN_API_KEY")
                .doesNotContain("DEEPSEEK_API_KEY")
                .doesNotContain("BRAINOS_JWT_SECRET")
                .doesNotContain("BrainOS@123");
    }
}
