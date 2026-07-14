package com.brainos.rag.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.rag.domain.ChatMessageEntity;
import com.brainos.rag.domain.ChatSessionEntity;
import com.brainos.rag.model.ChatModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ChatSessionMapperIT {

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
        registry.add("brainos.auth.jwt.secret", () -> "test-only-chat-jwt-secret-at-least-32-bytes");
        registry.add("brainos.admin.password", () -> "test-only-admin-password");
    }

    @Autowired ChatSessionMapper sessions;
    @Autowired ChatMessageMapper messages;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM chat_message");
        jdbc.update("DELETE FROM chat_session");
        jdbc.update("DELETE FROM kb_document");
        jdbc.update("DELETE FROM knowledge_base");
        jdbc.update("DELETE FROM sys_user WHERE id IN (81, 82)");
        jdbc.update("""
                INSERT INTO sys_user (id, username, password_hash, display_name, role, status)
                VALUES (81, 'chat-owner', 'unused', 'Chat Owner', 'USER', 'ENABLED'),
                       (82, 'chat-other', 'unused', 'Chat Other', 'USER', 'ENABLED')
                """);
        jdbc.update("""
                INSERT INTO knowledge_base (id, name, description, created_by)
                VALUES (91, '会话测试库', NULL, 81)
                """);
    }

    @Test
    void persistsOwnerScopedSessionHistoryAndCascadeDeletes() {
        ChatSessionEntity session =
                new ChatSessionEntity(null, "新会话", 91L, ChatModelType.DEEPSEEK, 81L);
        sessions.create(session);
        messages.create(new ChatMessageEntity(null, session.getId(), "USER", "年假几天？", null));
        messages.create(new ChatMessageEntity(
                null,
                session.getId(),
                "ASSISTANT",
                "5天",
                "{\"version\":1,\"items\":[]}"));

        assertThat(session.getId()).isPositive();
        assertThat(sessions.findOwned(session.getId(), 81L)).get().satisfies(stored -> {
            assertThat(stored.chatModel()).isEqualTo(ChatModelType.DEEPSEEK);
            assertThat(stored.knowledgeBaseId()).isEqualTo(91L);
        });
        assertThat(sessions.findOwned(session.getId(), 82L)).isEmpty();
        assertThat(messages.findAllBySessionId(session.getId()))
                .extracting(row -> row.role() + ":" + row.content())
                .containsExactly("USER:年假几天？", "ASSISTANT:5天");

        assertThat(sessions.deleteOwned(session.getId(), 82L)).isZero();
        assertThat(sessions.deleteOwned(session.getId(), 81L)).isOne();
        assertThat(messages.findAllBySessionId(session.getId())).isEmpty();
    }
}
