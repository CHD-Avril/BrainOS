package com.brainos.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("brainos")
                    .withUsername("brainos")
                    .withPassword("integration-only")
                    .withReuse(false);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(false);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("brainos.auth.jwt.secret", () -> "integration-only-jwt-secret-at-least-32-bytes");
        registry.add("brainos.admin.password", () -> "integration-only-admin-password");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void clearRefreshTokens() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    void loginMeRefreshReplayLogoutAndRejectedRefreshFormOneRealFlow() throws Exception {
        JsonNode login = data(postJson("/api/v1/auth/login", Map.of(
                        "username", "admin", "password", "integration-only-admin-password"))
                .andExpect(status().isOk())
                .andReturn());
        String firstAccess = login.path("accessToken").asText();
        String firstRefresh = login.path("refreshToken").asText();
        assertPasswordFree(login.path("user"));

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + firstAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        JsonNode rotated = data(postJson("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh))
                .andExpect(status().isOk())
                .andReturn());
        String rotatedAccess = rotated.path("accessToken").asText();
        String rotatedRefresh = rotated.path("refreshToken").asText();
        assertPasswordFree(rotated.path("user"));

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + rotatedAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("refreshToken", rotatedRefresh))))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", rotatedRefresh))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, Object body)
            throws Exception {
        return mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static void assertPasswordFree(JsonNode user) {
        org.assertj.core.api.Assertions.assertThat(user.has("password")).isFalse();
        org.assertj.core.api.Assertions.assertThat(user.has("passwordHash")).isFalse();
    }
}
