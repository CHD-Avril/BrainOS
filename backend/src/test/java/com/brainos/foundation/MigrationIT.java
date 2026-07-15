package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("brainos")
                    .withUsername("brainos")
                    .withPassword("test-only-password")
                    .withReuse(false);

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesCoreSchemaAndDocumentedDevelopmentAdmin() {
        List<String> migrations = jdbc.query(
                "select concat(version, ':', success) from flyway_schema_history "
                        + "where version in ('1', '2') order by installed_rank",
                (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(migrations).containsExactly("1:1", "2:1");

        List<String> coreTables = jdbc.query(
                "select table_name from information_schema.tables "
                        + "where table_schema = database() and table_name in "
                        + "('sys_user', 'knowledge_base', 'kb_document', "
                        + "'chat_session', 'chat_message', 'audit_log')",
                (resultSet, rowNumber) -> resultSet.getString(1));
        assertThat(coreTables)
                .containsExactlyInAnyOrder(
                        "sys_user",
                        "knowledge_base",
                        "kb_document",
                        "chat_session",
                        "chat_message",
                        "audit_log");

        Integer enabledAdmins = jdbc.queryForObject(
                "select count(*) from sys_user "
                        + "where username='admin' and role='ADMIN' and status='ENABLED'",
                Integer.class);
        String passwordHash = jdbc.queryForObject(
                "select password_hash from sys_user where username='admin'", String.class);

        assertThat(enabledAdmins).isEqualTo(1);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("BrainOS@123", passwordHash)).isTrue();
        assertThat(encoder.matches("wrong-password", passwordHash)).isFalse();
    }

    @Test
    void repeatMigrationWithFreshBcryptSaltKeepsVersionedChecksumsValid() {
        Integer checksumBefore = jdbc.queryForObject(
                "select checksum from flyway_schema_history where version='2'", Integer.class);
        String initialHash = jdbc.queryForObject(
                "select password_hash from sys_user where username='admin'", String.class);

        FluentConfiguration restarted = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration");
        new AdminSeedFlywayConfiguration()
                .adminSeedPasswordCustomizer(new AdminSeedProperties("BrainOS@123"))
                .customize(restarted);
        String restartedHash = restarted.getPlaceholders().get("adminPasswordHash");

        assertThat(restartedHash).isNotEqualTo(initialHash);
        assertThat(restarted.load().migrate().migrationsExecuted).isZero();
        Integer checksumAfter = jdbc.queryForObject(
                "select checksum from flyway_schema_history where version='2'", Integer.class);
        assertThat(checksumAfter).isEqualTo(checksumBefore);
    }
}
