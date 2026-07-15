package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.BrainOsApplication;
import com.brainos.EmptyUserRepository;
import com.brainos.RepositoryFiles;
import com.brainos.admin.audit.AuditRecorder;
import com.brainos.auth.domain.UserRepository;
import java.nio.file.Files;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ConfigurationContractTest {

    private static final String AUTO_CONFIGURATION_EXCLUSIONS =
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";
    private static final String[] COMPLETE_PROD_ENVIRONMENT = {
        "MYSQL_URL=jdbc:mysql://db.internal:3306/brainos",
        "MYSQL_USER=brainos_prod",
        "MYSQL_PASSWORD=database-secret",
        "REDIS_HOST=redis.internal",
        "REDIS_PORT=6380",
        "REDIS_PASSWORD=redis-secret",
        "CHROMA_URL=https://chroma.internal",
        "CHROMA_API_KEY=cloud-secret",
        "CHROMA_TENANT=tenant-123",
        "CHROMA_DATABASE=brainos-prod",
        "BRAINOS_STORAGE_PATH=/srv/brainos/uploads",
        "BRAINOS_ADMIN_PASSWORD=ProdAdmin@456",
        "BRAINOS_JWT_SECRET=prod-only-jwt-secret-with-at-least-32-bytes"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BrainOsApplication.class)
            .withBean(UserRepository.class, EmptyUserRepository::new)
            .withBean(AuditRecorder.class, () -> event -> {})
            .withPropertyValues(AUTO_CONFIGURATION_EXCLUSIONS);

    @Test
    void devProfileProvidesLocalDefaults() {
        contextRunner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url"))
                    .startsWith("jdbc:mysql://localhost:3306/brainos");
            assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.username"))
                    .isEqualTo("brainos");
            assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.password"))
                    .isEqualTo("brainos_dev");
            assertThat(context.getEnvironment().getRequiredProperty("spring.data.redis.host"))
                    .isEqualTo("localhost");
            assertThat(context.getEnvironment().getRequiredProperty("spring.data.redis.port"))
                    .isEqualTo("6379");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.base-url"))
                    .isEqualTo("http://localhost:8000");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.tenant"))
                    .isEqualTo("default_tenant");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.database"))
                    .isEqualTo("default_database");
            assertThat(context.getEnvironment().getProperty("brainos.chroma.api-key"))
                    .isEmpty();
            assertThat(context.getEnvironment().getRequiredProperty("brainos.storage.root"))
                    .isEqualTo("./data/uploads");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.admin.password"))
                    .isEqualTo("BrainOS@123");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.auth.jwt.secret"))
                    .isEqualTo("BrainOS-dev-only-jwt-secret-change-before-production");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.ai.chat.chatgpt.base-url"))
                    .isEqualTo("https://api.openai.com/v1");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.ai.chat.chatgpt.model"))
                    .isEqualTo("gpt-4.1-mini");
            assertThat(context.getEnvironment().getProperty("brainos.ai.chat.chatgpt.api-key"))
                    .isEmpty();
        });
    }

    @ParameterizedTest(name = "prod rejects missing {0}")
    @ValueSource(
            strings = {
                "MYSQL_URL",
                "MYSQL_USER",
                "MYSQL_PASSWORD",
                "REDIS_HOST",
                "REDIS_PORT",
                "REDIS_PASSWORD",
                "CHROMA_URL",
                "CHROMA_TENANT",
                "CHROMA_DATABASE",
                "BRAINOS_STORAGE_PATH",
                "BRAINOS_ADMIN_PASSWORD",
                "BRAINOS_JWT_SECRET"
            })
    void prodProfileFailsWhenAnyRequiredEnvironmentIsMissing(String missingVariable) {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withPropertyValues(environmentWithout(missingVariable))
                .run(context -> {
                    assertThat(context).hasFailed();
                    if (missingVariable.equals("BRAINOS_ADMIN_PASSWORD")
                            || missingVariable.equals("BRAINOS_JWT_SECRET")) {
                        assertThat(rootCause(context.getStartupFailure()).getMessage())
                                .contains(missingVariable);
                    }
                });
    }

    @Test
    void prodProfileBindsCompleteEnvironmentAndHashesAdminOverride() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withPropertyValues(COMPLETE_PROD_ENVIRONMENT)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:mysql://db.internal:3306/brainos");
                    assertThat(
                                    context.getEnvironment()
                                            .getRequiredProperty("spring.datasource.username"))
                            .isEqualTo("brainos_prod");
                    assertThat(
                                    context.getEnvironment()
                                            .getRequiredProperty("spring.datasource.password"))
                            .isEqualTo("database-secret");
                    assertThat(context.getEnvironment().getRequiredProperty("spring.data.redis.host"))
                            .isEqualTo("redis.internal");
                    assertThat(context.getEnvironment().getRequiredProperty("spring.data.redis.port"))
                            .isEqualTo("6380");
                    assertThat(
                                    context.getEnvironment()
                                            .getRequiredProperty("spring.data.redis.password"))
                            .isEqualTo("redis-secret");
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.base-url"))
                            .isEqualTo("https://chroma.internal");
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.api-key"))
                            .isEqualTo("cloud-secret");
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.tenant"))
                            .isEqualTo("tenant-123");
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.chroma.database"))
                            .isEqualTo("brainos-prod");
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.storage.root"))
                            .isEqualTo("/srv/brainos/uploads");
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.auth.jwt.secret"))
                            .isEqualTo("prod-only-jwt-secret-with-at-least-32-bytes");

                    FlywayConfigurationCustomizer customizer =
                            context.getBean(FlywayConfigurationCustomizer.class);
                    FluentConfiguration flyway = Flyway.configure();
                    customizer.customize(flyway);
                    String seededHash = flyway.getPlaceholders().get("adminPasswordHash");

                    assertThat(seededHash).startsWith("$2").doesNotContain("ProdAdmin@456");
                    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                    assertThat(encoder.matches("ProdAdmin@456", seededHash)).isTrue();
                    assertThat(encoder.matches("BrainOS@123", seededHash)).isFalse();
                });
    }

    @Test
    void chromaCloudRejectsMissingApiKey() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "CHROMA_URL=https://api.trychroma.com",
                        "CHROMA_TENANT=tenant-123",
                        "CHROMA_DATABASE=brainos")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("CHROMA_API_KEY");
                });
    }

    @Test
    void environmentExampleDocumentsJwtSecretOverride() throws Exception {
        assertThat(Files.readString(RepositoryFiles.find(".env.example")))
                .contains("BRAINOS_JWT_SECRET=")
                .contains("CHROMA_API_KEY=")
                .contains("CHROMA_TENANT=")
                .contains("CHROMA_DATABASE=")
                .contains("OPENAI_API_KEY=")
                .contains("Required when running the backend with any profile other than dev");
    }

    private static String[] environmentWithout(String missingVariable) {
        String prefix = missingVariable + "=";
        return Arrays.stream(COMPLETE_PROD_ENVIRONMENT)
                .filter(property -> !property.startsWith(prefix))
                .toArray(String[]::new);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
