package com.brainos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BrainOsApplicationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(BrainOsApplication.class)
            .withBean(com.brainos.auth.domain.UserRepository.class, EmptyUserRepository::new)
            .withBean(com.brainos.admin.audit.AuditRecorder.class, () -> event -> {})
            .withPropertyValues(
                    "spring.autoconfigure.exclude="
                            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration");

    @Test
    void applicationContextStarts() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(BrainOsApplication.class);
        });
    }
}
