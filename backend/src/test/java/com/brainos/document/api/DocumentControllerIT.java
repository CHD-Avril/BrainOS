package com.brainos.document.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.TokenService;
import com.brainos.document.application.DocumentIndexingService;
import com.brainos.document.application.DocumentUploadService;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerIT {

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
        registry.add("brainos.auth.jwt.secret", () -> "test-only-document-api-jwt-secret-at-least-32-bytes");
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired MockMvc mvc;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DocumentUploadService uploads;
    @MockitoBean DocumentIndexingService indexing;

    private DocumentView view;
    private String bearer;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sys_user WHERE id = 9");
        jdbc.update("""
                INSERT INTO sys_user (
                    id, username, password_hash, display_name, role, status
                ) VALUES (9, 'document-user', 'unused', 'Document User', 'USER', 'ENABLED')
                """);
        view = document();
        bearer = "Bearer " + tokens.createAccessToken(new UserAccount(
                9L, "document-user", "unused", "Document User", UserRole.USER, UserStatus.ENABLED));
        when(indexing.list(7L)).thenReturn(List.of(view));
        when(indexing.get(7L, 44L)).thenReturn(view);
        when(indexing.retry(7L, 44L, 9L)).thenReturn(view);
    }

    @Test
    void authenticatedUserCanUploadPollRetryAndDelete() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.txt", "text/plain", "企业制度".getBytes());
        when(uploads.upload(7L, file, 9L)).thenReturn(view);

        mvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", 7L)
                        .file(file)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(44L))
                .andExpect(jsonPath("$.data.status").value("PARSING"));
        mvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", 7L)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalName").value("policy.txt"));
        mvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", 7L, 44L)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(44L));
        mvc.perform(post("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/retry", 7L, 44L)
                        .header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", 7L, 44L)
                        .header("Authorization", bearer))
                .andExpect(status().isOk());

        verify(indexing).delete(7L, 44L, 9L);
    }

    @Test
    void anonymousDocumentRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", 7L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private static DocumentView document() {
        return new DocumentView(
                44L,
                7L,
                "policy.txt",
                "/tmp/policy.txt",
                "text/plain",
                12L,
                "a".repeat(64),
                DocumentStatus.PARSING,
                0,
                null,
                9L,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
