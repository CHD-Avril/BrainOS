package com.brainos.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.TokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import(DashboardControllerIT.FixedClockConfiguration.class)
class DashboardControllerIT {

    private static final Instant NOW = Instant.parse("2026-07-14T04:00:00Z");
    private static final String TEST_JWT_SECRET =
            "test-only-dashboard-jwt-secret-at-least-32-bytes";

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
        registry.add("brainos.auth.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokenService;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM chat_message");
        jdbc.update("DELETE FROM chat_session");
        jdbc.update("DELETE FROM kb_document");
        jdbc.update("DELETE FROM knowledge_base");
        jdbc.update("DELETE FROM sys_user WHERE id = 301");
        jdbc.update("""
                INSERT INTO sys_user (
                    id, username, password_hash, display_name, role, status
                ) VALUES (301, 'dashboard-user', 'unused', 'Dashboard User', 'USER', 'ENABLED')
                """);
        jdbc.update("""
                INSERT INTO knowledge_base (id, name, description, created_by)
                VALUES (401, '人力资源', NULL, 301), (402, '产品资料', NULL, 301)
                """);
        insertDocuments();
        insertMessages();
    }

    @Test
    void exposesRealSummarySevenNaturalDaysAndFiveRecentDocuments() throws Exception {
        String bearer = bearer();

        mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseCount").value(2))
                .andExpect(jsonPath("$.data.documentCount").value(6))
                .andExpect(jsonPath("$.data.chunkCount").value(11))
                .andExpect(jsonPath("$.data.questionCount").value(3));

        mvc.perform(get("/api/v1/dashboard/trends")
                        .param("days", "7")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].date").value("2026-07-08"))
                .andExpect(jsonPath("$.data[0].count").value(0))
                .andExpect(jsonPath("$.data[5].date").value("2026-07-13"))
                .andExpect(jsonPath("$.data[5].count").value(2))
                .andExpect(jsonPath("$.data[6].date").value("2026-07-14"))
                .andExpect(jsonPath("$.data[6].count").value(0));

        mvc.perform(get("/api/v1/dashboard/recent-documents")
                        .param("limit", "5")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].originalName").value("latest.txt"))
                .andExpect(jsonPath("$.data[0].knowledgeBaseName").value("产品资料"))
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[4].originalName").value("second.txt"));
    }

    @Test
    void emptyBusinessStateReturnsZerosAndEmptyLists() throws Exception {
        jdbc.update("DELETE FROM chat_message");
        jdbc.update("DELETE FROM chat_session");
        jdbc.update("DELETE FROM kb_document");
        jdbc.update("DELETE FROM knowledge_base");
        String bearer = bearer();

        mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.knowledgeBaseCount").value(0))
                .andExpect(jsonPath("$.data.documentCount").value(0))
                .andExpect(jsonPath("$.data.chunkCount").value(0))
                .andExpect(jsonPath("$.data.questionCount").value(0));
        mvc.perform(get("/api/v1/dashboard/trends").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].count").value(0))
                .andExpect(jsonPath("$.data[6].count").value(0));
        mvc.perform(get("/api/v1/dashboard/recent-documents").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void anonymousDashboardRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void insertDocuments() {
        insertDocument(501, 401, "oldest.txt", "READY", 2, "2026-07-09 10:00:00");
        insertDocument(502, 401, "second.txt", "PARSING", 99, "2026-07-10 10:00:00");
        insertDocument(503, 401, "third.txt", "READY", 4, "2026-07-11 10:00:00");
        insertDocument(504, 402, "fourth.txt", "FAILED", 0, "2026-07-12 10:00:00");
        insertDocument(505, 402, "fifth.txt", "INDEXING", 0, "2026-07-13 10:00:00");
        insertDocument(506, 402, "latest.txt", "READY", 5, "2026-07-14 10:00:00");
    }

    private void insertDocument(
            long id,
            long knowledgeBaseId,
            String name,
            String status,
            int chunks,
            String updatedAt) {
        jdbc.update(
                """
                INSERT INTO kb_document (
                    id, knowledge_base_id, original_name, storage_path, mime_type, size_bytes,
                    sha256, status, chunk_count, uploaded_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'text/plain', 1, ?, ?, ?, 301, ?, ?)
                """,
                id,
                knowledgeBaseId,
                name,
                "/tmp/" + name,
                String.format("%064x", id),
                status,
                chunks,
                updatedAt,
                updatedAt);
    }

    private void insertMessages() {
        jdbc.update("""
                INSERT INTO chat_session (
                    id, title, knowledge_base_id, chat_model, user_id, created_at, updated_at
                ) VALUES (601, '测试会话', 401, 'QWEN', 301,
                          '2026-07-07 09:00:00', '2026-07-14 09:00:00')
                """);
        insertMessage(701, "USER", "outside", "2026-07-07 23:59:59");
        insertMessage(702, "USER", "question one", "2026-07-13 08:00:00");
        insertMessage(703, "ASSISTANT", "answer", "2026-07-13 08:00:01");
        insertMessage(704, "USER", "question two", "2026-07-13 18:00:00");
    }

    private void insertMessage(long id, String role, String content, String createdAt) {
        jdbc.update(
                "INSERT INTO chat_message (id, session_id, role, content, created_at) "
                        + "VALUES (?, 601, ?, ?, ?)",
                id,
                role,
                content,
                createdAt);
    }

    private String bearer() {
        String token = tokenService.createAccessToken(new UserAccount(
                301L,
                "dashboard-user",
                "unused",
                "Dashboard User",
                UserRole.USER,
                UserStatus.ENABLED));
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedDashboardClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
        }
    }
}
