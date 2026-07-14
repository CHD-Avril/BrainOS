package com.brainos.knowledge.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBaseControllerIT {

    private static final String TEST_JWT_SECRET =
            "test-only-knowledge-base-jwt-secret-at-least-32-bytes";

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("brainos")
                    .withUsername("brainos")
                    .withPassword("test-only-password")
                    .withReuse(false);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("brainos.auth.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenService tokenService;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        jdbc.update("delete from chat_message");
        jdbc.update("delete from chat_session");
        jdbc.update("delete from kb_document");
        jdbc.update("delete from knowledge_base");
        jdbc.update("delete from sys_user where id in (41, 42)");
        insertUser(41L, "knowledge-user", "USER");
        insertUser(42L, "knowledge-admin", "ADMIN");
    }

    @Test
    void userCanCreateListGetUpdateCountAndDelete() throws Exception {
        String bearer = bearer(41L, "knowledge-user", UserRole.USER);

        MvcResult created = create(bearer, "  产品知识库  ", "  产品资料  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("产品知识库"))
                .andExpect(jsonPath("$.data.description").value("产品资料"))
                .andExpect(jsonPath("$.data.createdBy").value(41L))
                .andExpect(jsonPath("$.data.documentCount").value(0))
                .andExpect(jsonPath("$.data.readyDocumentCount").value(0))
                .andReturn();
        long id = createdId(created);

        insertDocument(id, "READY", "a".repeat(64));
        insertDocument(id, "PARSING", "b".repeat(64));

        mvc.perform(get("/api/v1/knowledge-bases").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].documentCount").value(2))
                .andExpect(jsonPath("$.data[0].readyDocumentCount").value(1));

        mvc.perform(get("/api/v1/knowledge-bases/{id}", id).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentCount").value(2))
                .andExpect(jsonPath("$.data.readyDocumentCount").value(1));

        json(put("/api/v1/knowledge-bases/{id}", id), bearer,
                        Map.of("name", "  研发知识库  ", "description", "  新描述  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("研发知识库"))
                .andExpect(jsonPath("$.data.description").value("新描述"));

        mvc.perform(delete("/api/v1/knowledge-bases/{id}", id)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mvc.perform(get("/api/v1/knowledge-bases/{id}", id).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void adminCanManageSharedKnowledgeBases() throws Exception {
        String bearer = bearer(42L, "knowledge-admin", UserRole.ADMIN);

        create(bearer, "管理制度", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdBy").value(42L));
    }

    @Test
    void duplicateNamesReturnConflict() throws Exception {
        String bearer = bearer(41L, "knowledge-user", UserRole.USER);
        create(bearer, "共享资料", null).andExpect(status().isOk());

        create(bearer, " 共享资料 ", "重复")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void invalidCreateAndUpdateReturnValidationEnvelope() throws Exception {
        String bearer = bearer(41L, "knowledge-user", UserRole.USER);

        create(bearer, "a", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        create(bearer, "有效名称", "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        long id = createdId(create(bearer, "有效名称", null).andExpect(status().isOk()).andReturn());
        json(put("/api/v1/knowledge-bases/{id}", id), bearer,
                        Map.of("name", "x".repeat(61)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void anonymousRequestsAreUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"共享知识\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions create(String bearer, String name, String description) throws Exception {
        Object body = description == null
                ? Map.of("name", name)
                : Map.of("name", name, "description", description);
        return json(post("/api/v1/knowledge-bases"), bearer, body);
    }

    private ResultActions json(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String bearer,
            Object body)
            throws Exception {
        return mvc.perform(request.header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private long createdId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();
    }

    private void insertUser(long id, String username, String role) {
        jdbc.update(
                "insert into sys_user "
                        + "(id, username, password_hash, display_name, role, status) "
                        + "values (?, ?, ?, ?, ?, 'ENABLED')",
                id,
                username,
                "unused",
                username,
                role);
    }

    private void insertDocument(long knowledgeBaseId, String status, String sha256) {
        jdbc.update(
                "insert into kb_document "
                        + "(knowledge_base_id, original_name, storage_path, mime_type, size_bytes, "
                        + "sha256, status, uploaded_by) values (?, ?, ?, ?, ?, ?, ?, ?)",
                knowledgeBaseId,
                sha256.substring(0, 1) + ".txt",
                "/tmp/" + sha256.substring(0, 1) + ".txt",
                "text/plain",
                1L,
                sha256,
                status,
                41L);
    }

    private String bearer(long id, String username, UserRole role) {
        String token = tokenService.createAccessToken(new UserAccount(
                id, username, "unused", username, role, UserStatus.ENABLED));
        return "Bearer " + token;
    }
}
