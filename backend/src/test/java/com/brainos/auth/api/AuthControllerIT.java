package com.brainos.auth.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    private static final String TEST_JWT_SECRET =
            "test-only-task-four-jwt-secret-at-least-32-bytes";

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("brainos")
                    .withUsername("brainos")
                    .withPassword("test-only-password")
                    .withReuse(false);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(false);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("brainos.auth.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    TokenService tokenService;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
        jdbc.update("delete from sys_user where id = ?", 42L);
        jdbc.update(
                "insert into sys_user "
                        + "(id, username, password_hash, display_name, role, status) "
                        + "values (?, ?, ?, ?, ?, ?)",
                42L,
                "task4-user",
                passwordEncoder.encode("Task4-password"),
                "Task 4 User",
                "USER",
                "ENABLED");
    }

    @Test
    void anonymousCannotReadCurrentUser() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void ordinaryUserCannotReachAdminProbe() throws Exception {
        String bearer = tokenService.createAccessToken(userAccount());

        mvc.perform(get("/api/v1/admin/probe")
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void seededAdminLoginReturnsTokensAndPasswordFreeSummary() throws Exception {
        JsonNode data = login("admin", "test-only-admin-password");

        assertThatFieldsAreExactly(data, Set.of("accessToken", "refreshToken", "user"));
        org.assertj.core.api.Assertions.assertThat(data.path("accessToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThatFieldsAreExactly(
                data.path("user"), Set.of("id", "username", "displayName", "role"));
        org.assertj.core.api.Assertions.assertThat(data.path("user").path("id").asLong())
                .isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(data.path("user").path("username").asText())
                .isEqualTo("admin");
        org.assertj.core.api.Assertions.assertThat(data.path("user").path("displayName").asText())
                .isEqualTo("系统管理员");
        org.assertj.core.api.Assertions.assertThat(data.path("user").path("role").asText())
                .isEqualTo("ADMIN");
        org.assertj.core.api.Assertions.assertThat(
                        jwtDecoder.decode(data.path("accessToken").asText()).getClaims().keySet())
                .containsExactlyInAnyOrder("sub", "role", "iat", "exp", "jti")
                .doesNotContain("username");
    }

    @Test
    void rejectedLoginsShareOneUnauthorizedEnvelope() throws Exception {
        assertUnauthorized(postJson(
                "/api/v1/auth/login",
                Map.of("username", "task4-user", "password", "wrong-password")));
        assertUnauthorized(postJson(
                "/api/v1/auth/login",
                Map.of("username", "missing-user", "password", "Task4-password")));

        jdbc.update("update sys_user set status = 'DISABLED' where id = ?", 42L);
        assertUnauthorized(postJson(
                "/api/v1/auth/login",
                Map.of("username", "task4-user", "password", "Task4-password")));
    }

    @Test
    void blankLoginAndRefreshFieldsAreValidationErrors() throws Exception {
        assertValidationFailure(postJson(
                "/api/v1/auth/login", Map.of("username", "", "password", "")));
        assertValidationFailure(postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", " ")));
    }

    @Test
    void refreshRotatesTokenAndRejectsReplay() throws Exception {
        JsonNode login = login("task4-user", "Task4-password");
        String oldRefreshToken = login.path("refreshToken").asText();

        MvcResult rotation = postJson(
                        "/api/v1/auth/refresh", Map.of("refreshToken", oldRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("task4-user"))
                .andReturn();
        String rotatedRefreshToken = objectMapper.readTree(rotation.getResponse().getContentAsString())
                .path("data")
                .path("refreshToken")
                .asText();

        org.assertj.core.api.Assertions.assertThat(rotatedRefreshToken)
                .isNotEqualTo(oldRefreshToken);
        assertUnauthorized(postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", oldRefreshToken)));
    }

    @Test
    void logoutRequiresAccessTokenAndRevokesRefreshToken() throws Exception {
        JsonNode login = login("task4-user", "Task4-password");
        String accessToken = login.path("accessToken").asText();
        String refreshToken = login.path("refreshToken").asText();

        assertUnauthorized(postJson(
                "/api/v1/auth/logout", Map.of("refreshToken", refreshToken)));

        postJson(
                        "/api/v1/auth/logout",
                        Map.of("refreshToken", refreshToken),
                        accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertUnauthorized(postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", refreshToken)));
    }

    @Test
    void currentUserUsesDatabaseUsernameAndReturnsPasswordFreeSummary() throws Exception {
        String accessToken = login("task4-user", "Task4-password")
                .path("accessToken")
                .asText();
        jdbc.update(
                "update sys_user set username = ?, display_name = ? where id = ?",
                "renamed-user",
                "Renamed User",
                42L);

        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(42L))
                .andExpect(jsonPath("$.data.username").value("renamed-user"))
                .andExpect(jsonPath("$.data.displayName").value("Renamed User"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void invalidAccessTokensRemainAnonymousWithOneEnvelope() throws Exception {
        Instant now = Instant.now();
        JwtEncoder wrongEncoder = encoderFor(
                "different-test-only-jwt-secret-at-least-32-bytes");
        String invalidSignature = signedToken(
                wrongEncoder, "42", "USER", now, now.plus(Duration.ofHours(1)));
        String expired = signedToken(
                jwtEncoder,
                "42",
                "USER",
                now.minus(Duration.ofHours(3)),
                now.minus(Duration.ofHours(1)));
        String invalidSubject = signedToken(
                jwtEncoder, "not-a-number", "USER", now, now.plus(Duration.ofHours(1)));
        String missingRole = signedToken(
                jwtEncoder, "42", null, now, now.plus(Duration.ofHours(1)));
        String unknownUser = signedToken(
                jwtEncoder, "999", "USER", now, now.plus(Duration.ofHours(1)));
        String staleRole = signedToken(
                jwtEncoder, "42", "ADMIN", now, now.plus(Duration.ofHours(1)));

        for (String token : Set.of(
                "not-a-jwt",
                invalidSignature,
                expired,
                invalidSubject,
                missingRole,
                unknownUser,
                staleRole)) {
            assertUnauthorized(mvc.perform(get("/api/v1/auth/me")
                    .header("Authorization", "Bearer " + token)));
        }

        String disabledUser = tokenService.createAccessToken(userAccount());
        jdbc.update("update sys_user set status = 'DISABLED' where id = ?", 42L);
        assertUnauthorized(mvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + disabledUser)));
    }

    @Test
    void adminBearerPassesAuthorizationForMissingProbe() throws Exception {
        String adminAccessToken = login("admin", "test-only-admin-password")
                .path("accessToken")
                .asText();

        mvc.perform(get("/api/v1/admin/probe")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicListIsExactAndIncludesHealthAndApiDocumentation() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
        assertValidationFailure(postJson(
                "/api/v1/auth/login", Map.of("username", "", "password", "")));
        assertValidationFailure(postJson(
                "/api/v1/auth/refresh", Map.of("refreshToken", "")));
        assertUnauthorized(postJson(
                "/api/v1/auth/logout", Map.of("refreshToken", "anything")));
        assertUnauthorized(mvc.perform(get("/api/v1/auth/me")));
        assertUnauthorized(postJson(
                "/api/v1/auth/login/extra", Map.of("username", "admin", "password", "x")));
        assertUnauthorized(postJson(
                "/api/v1/auth/refresh/extra", Map.of("refreshToken", "x")));
        assertUnauthorized(postJson("/logout", Map.of()));
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = postJson(
                        "/api/v1/auth/login",
                        Map.of("username", username, "password", password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private ResultActions postJson(String path, Object body, String accessToken) throws Exception {
        return mvc.perform(post(path)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private static void assertUnauthorized(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private static void assertValidationFailure(ResultActions actions) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private String signedToken(
            JwtEncoder encoder,
            String subject,
            String role,
            Instant issuedAt,
            Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString());
        if (role != null) {
            claims.claim("role", role);
        }
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build()))
                .getTokenValue();
    }

    private static JwtEncoder encoderFor(String secret) {
        SecretKey key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private static void assertThatFieldsAreExactly(JsonNode node, Set<String> fields) {
        org.assertj.core.api.Assertions.assertThat(node.properties())
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrderElementsOf(fields);
    }

    private UserAccount userAccount() {
        return new UserAccount(
                42L,
                "task4-user",
                "unused",
                "Task 4 User",
                UserRole.USER,
                UserStatus.ENABLED);
    }
}
