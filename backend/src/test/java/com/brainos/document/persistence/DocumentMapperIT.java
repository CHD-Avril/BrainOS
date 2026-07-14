package com.brainos.document.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.domain.KnowledgeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest
class DocumentMapperIT {

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
        registry.add("brainos.auth.jwt.secret", () -> "test-only-document-jwt-secret-at-least-32-bytes");
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired DocumentRepository documents;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM chat_message");
        jdbc.update("DELETE FROM chat_session");
        jdbc.update("DELETE FROM kb_document");
        jdbc.update("DELETE FROM knowledge_base");
        jdbc.update("DELETE FROM sys_user WHERE id = 61");
        jdbc.update("""
                INSERT INTO sys_user (
                    id, username, password_hash, display_name, role, status
                ) VALUES (61, 'document-user', 'unused', 'Document User', 'USER', 'ENABLED')
                """);
        jdbc.update("""
                INSERT INTO knowledge_base (id, name, description, created_by)
                VALUES (71, '文档测试库', NULL, 61)
                """);
    }

    @Test
    void insertsAndReadsParsingDocument() {
        KnowledgeDocument document = document("a".repeat(64));

        documents.create(document);
        DocumentView stored = documents.findById(document.id()).orElseThrow();

        assertThat(document.id()).isPositive();
        assertThat(stored.knowledgeBaseId()).isEqualTo(71L);
        assertThat(stored.originalName()).isEqualTo("policy.txt");
        assertThat(stored.mimeType()).isEqualTo("text/plain");
        assertThat(stored.status()).isEqualTo(DocumentStatus.PARSING);
        assertThat(stored.chunkCount()).isZero();
        assertThat(stored.failureReason()).isNull();
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.updatedAt()).isNotNull();
        assertThat(documents.existsByKnowledgeBaseAndSha256(71L, "a".repeat(64))).isTrue();
    }

    @Test
    void databaseConstraintRejectsDuplicateHashInsideKnowledgeBase() {
        documents.create(document("b".repeat(64)));

        assertThatThrownBy(() -> documents.create(document("b".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static KnowledgeDocument document(String sha256) {
        return new KnowledgeDocument(
                null,
                71L,
                "policy.txt",
                "/tmp/policy.txt",
                "text/plain",
                12L,
                sha256,
                DocumentStatus.PARSING,
                61L);
    }
}
