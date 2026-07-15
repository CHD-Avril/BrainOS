package com.brainos.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.auth.application.AuthService;
import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.auth.token.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserMapperIT {

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
    UserMapper users;

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenStore refreshTokenStore;

    @Autowired
    TokenService tokenService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void authenticationComponentsAreWiredThroughProductionPorts() {
        assertThat(authService).isNotNull();
        assertThat(userRepository).isSameAs(users);
        assertThat(refreshTokenStore).isNotNull();
        assertThat(tokenService).isNotNull();
        assertThat(passwordEncoder).isNotNull();
    }

    @Test
    void readsUserAccountFromSysUserByUsernameAndId() {
        UserAccount byUsername = users.findByUsername("admin").orElseThrow();

        assertThat(byUsername.id()).isEqualTo(1L);
        assertThat(byUsername.username()).isEqualTo("admin");
        assertThat(byUsername.passwordHash()).startsWith("$2");
        assertThat(byUsername.displayName()).isEqualTo("系统管理员");
        assertThat(byUsername.role()).isEqualTo(UserRole.ADMIN);
        assertThat(byUsername.status()).isEqualTo(UserStatus.ENABLED);
        assertThat(users.findById(1L)).contains(byUsername);
        assertThat(users.findByUsername("absent")).isEmpty();
        assertThat(users.findById(999L)).isEmpty();
    }
}
