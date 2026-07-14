package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.BrainOsApplication;
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
        "BRAINOS_STORAGE_PATH=/srv/brainos/uploads",
        "BRAINOS_ADMIN_PASSWORD=ProdAdmin@456"
    };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BrainOsApplication.class)
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
            assertThat(context.getEnvironment().getRequiredProperty("brainos.storage.root"))
                    .isEqualTo("./data/uploads");
            assertThat(context.getEnvironment().getRequiredProperty("brainos.admin.password"))
                    .isEqualTo("BrainOS@123");
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
                "BRAINOS_STORAGE_PATH",
                "BRAINOS_ADMIN_PASSWORD"
            })
    void prodProfileFailsWhenAnyRequiredEnvironmentIsMissing(String missingVariable) {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withPropertyValues(environmentWithout(missingVariable))
                .run(context -> {
                    assertThat(context).hasFailed();
                    if (missingVariable.equals("BRAINOS_ADMIN_PASSWORD")) {
                        assertThat(rootCause(context.getStartupFailure()).getMessage())
                                .contains("BRAINOS_ADMIN_PASSWORD");
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
                    assertThat(context.getEnvironment().getRequiredProperty("brainos.storage.root"))
                            .isEqualTo("/srv/brainos/uploads");

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
