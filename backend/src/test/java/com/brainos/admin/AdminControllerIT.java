package com.brainos.admin;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.auth.token.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIT {

    private static final String JWT_SECRET =
            "test-only-admin-controller-jwt-secret-at-least-32-bytes";

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
        registry.add("brainos.auth.jwt.secret", () -> JWT_SECRET);
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TokenService tokens;

    @MockitoBean RefreshTokenStore refreshTokens;

    @BeforeEach
    void resetState() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM chat_message");
        jdbc.update("DELETE FROM chat_session");
        jdbc.update("DELETE FROM kb_document");
        jdbc.update("DELETE FROM knowledge_base");
        jdbc.update("DELETE FROM sys_user WHERE id IN (901, 902)");
        jdbc.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = 1");
        insertUser(901, "admin-owner", "ADMIN", "ENABLED");
        insertUser(902, "ordinary-user", "USER", "ENABLED");
        reset(refreshTokens);
    }

    @Test
    void adminCanCreateUpdateDisableAndFilterAuditWithoutPasswordExposure() throws Exception {
        String admin = bearer(901, "admin-owner", UserRole.ADMIN, UserStatus.ENABLED);

        MvcResult result = json(
                        post("/api/v1/admin/users"),
                        admin,
                        Map.of(
                                "username", "  Baron.Member ",
                                "displayName", "Baron Member",
                                "password", "SecurePass8",
                                "role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("baron.member"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();
        long createdId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mvc.perform(get("/api/v1/admin/users")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items[0].id").value(createdId));

        json(
                        put("/api/v1/admin/users/{id}", createdId),
                        admin,
                        Map.of(
                                "displayName", "知识运营",
                                "password", "ChangedPass9",
                                "role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("知识运营"));
        verify(refreshTokens).revokeAll(createdId);

        json(
                        patch("/api/v1/admin/users/{id}/status", createdId),
                        admin,
                        Map.of("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mvc.perform(get("/api/v1/admin/audit-logs")
                        .param("userId", "901")
                        .param("action", "user_status_change")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("admin-owner"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(Long.toString(createdId)))
                .andExpect(jsonPath("$.data.items[0].summary").value("停用用户"));

        mvc.perform(get("/api/v1/admin/audit-logs")
                        .param("username", "ordinary-user")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void refusesToDisableOrDemoteLastEnabledAdministrator() throws Exception {
        String admin = bearer(901, "admin-owner", UserRole.ADMIN, UserStatus.ENABLED);

        json(
                        patch("/api/v1/admin/users/901/status"),
                        admin,
                        Map.of("status", "DISABLED"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        json(
                        put("/api/v1/admin/users/901"),
                        admin,
                        Map.of("displayName", "Administrator", "role", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void ordinaryUserCannotAccessAdministrationEndpoints() throws Exception {
        String user = bearer(902, "ordinary-user", UserRole.USER, UserStatus.ENABLED);

        mvc.perform(get("/api/v1/admin/users").header("Authorization", user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private org.springframework.test.web.servlet.ResultActions json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String bearer,
            Object body)
            throws Exception {
        return mvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private void insertUser(long id, String username, String role, String status) {
        jdbc.update(
                "INSERT INTO sys_user "
                        + "(id, username, password_hash, display_name, role, status) "
                        + "VALUES (?, ?, 'unused', ?, ?, ?)",
                id,
                username,
                username,
                role,
                status);
    }

    private String bearer(
            long id, String username, UserRole role, UserStatus status) {
        String token = tokens.createAccessToken(
                new UserAccount(id, username, "unused", username, role, status));
        return "Bearer " + token;
    }
}
