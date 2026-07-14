package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesEnabledAdmin() {
        Integer count = jdbc.queryForObject(
                "select count(*) from sys_user where username='admin' and role='ADMIN' and status='ENABLED'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
