package com.brainos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.InvalidRefreshTokenException;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.auth.token.TokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");
    private static final PasswordEncoder PASSWORDS = new BCryptPasswordEncoder(4);

    @Test
    void enabledUserReceivesAccessRefreshAndPasswordFreeSummary() {
        FakeUsers users = new FakeUsers(enabledUser(42L, "baron", "secret"));
        FakeRefreshTokens refreshTokens = new FakeRefreshTokens();
        AuthService service = service(users, refreshTokens);

        TokenPair pair = service.login("baron", "secret");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(refreshTokens.contains(pair.refreshToken())).isTrue();
        assertThat(refreshTokens.lastIssuedTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(pair.user().id()).isEqualTo(42L);
        assertThat(pair.user().username()).isEqualTo("baron");
        assertThat(pair.user().displayName()).isEqualTo("Baron");
        assertThat(pair.user().role()).isEqualTo(UserRole.USER);
        assertThat(pair.user().getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("password", "passwordHash", "password_hash");
    }

    @Test
    void missingUserPasswordMismatchAndDisabledUserShareGenericFailure() {
        AuthService missing = service(new FakeUsers(), new FakeRefreshTokens());
        AuthService wrongPassword =
                service(new FakeUsers(enabledUser(42L, "baron", "secret")), new FakeRefreshTokens());
        AuthService disabled =
                service(new FakeUsers(disabledUser(42L, "baron", "secret")), new FakeRefreshTokens());

        assertGenericLoginFailure(missing, "baron", "secret");
        assertGenericLoginFailure(wrongPassword, "baron", "wrong");
        assertGenericLoginFailure(disabled, "baron", "secret");
    }

    @Test
    void everyRejectedLoginPerformsExactlyOnePasswordComparison() {
        RecordingPasswordEncoder missingPassword = new RecordingPasswordEncoder(false);
        RecordingPasswordEncoder wrongPassword = new RecordingPasswordEncoder(false);
        RecordingPasswordEncoder disabledPassword = new RecordingPasswordEncoder(true);
        AuthService missing = service(new FakeUsers(), new FakeRefreshTokens(), missingPassword);
        AuthService wrong = service(
                new FakeUsers(enabledUser(42L, "baron", "secret")),
                new FakeRefreshTokens(),
                wrongPassword);
        AuthService disabled = service(
                new FakeUsers(disabledUser(42L, "baron", "secret")),
                new FakeRefreshTokens(),
                disabledPassword);

        assertGenericLoginFailure(missing, "baron", "secret");
        assertGenericLoginFailure(wrong, "baron", "wrong");
        assertGenericLoginFailure(disabled, "baron", "secret");

        assertThat(missingPassword.matchesCalls()).isEqualTo(1);
        assertThat(wrongPassword.matchesCalls()).isEqualTo(1);
        assertThat(disabledPassword.matchesCalls()).isEqualTo(1);
        assertThat(missingPassword.lastEncodedPassword()).startsWith("$2a$12$").hasSize(60);
        assertThat(new BCryptPasswordEncoder()
                        .matches("BrainOS-dummy-password", missingPassword.lastEncodedPassword()))
                .isTrue();
    }

    @Test
    void refreshConsumesOldTokenAndReturnsRotatedPair() {
        FakeUsers users = new FakeUsers(enabledUser(42L, "baron", "secret"));
        FakeRefreshTokens refreshTokens = new FakeRefreshTokens();
        String oldToken = refreshTokens.issue(42L, Duration.ofDays(7));
        AuthService service = service(users, refreshTokens);

        TokenPair rotated = service.refresh(oldToken);

        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(oldToken);
        assertThat(refreshTokens.contains(oldToken)).isFalse();
        assertThat(refreshTokens.contains(rotated.refreshToken())).isTrue();
        assertThat(refreshTokens.lastIssuedTtl()).isEqualTo(Duration.ofDays(7));
        assertThatThrownBy(() -> service.refresh(oldToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("刷新令牌无效");
    }

    @Test
    void refreshForDisabledOrMissingUserStillConsumesOldToken() {
        FakeRefreshTokens disabledTokens = new FakeRefreshTokens();
        String disabledToken = disabledTokens.issue(42L, Duration.ofDays(7));
        AuthService disabled =
                service(new FakeUsers(disabledUser(42L, "baron", "secret")), disabledTokens);

        assertThatThrownBy(() -> disabled.refresh(disabledToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("刷新令牌无效");
        assertThatThrownBy(() -> disabled.refresh(disabledToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("刷新令牌无效");

        FakeRefreshTokens missingTokens = new FakeRefreshTokens();
        String missingToken = missingTokens.issue(99L, Duration.ofDays(7));
        AuthService missing = service(new FakeUsers(), missingTokens);

        assertThatThrownBy(() -> missing.refresh(missingToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("刷新令牌无效");
        assertThat(missingTokens.contains(missingToken)).isFalse();
    }

    @Test
    void logoutRevokesRefreshTokenIdempotently() {
        FakeRefreshTokens refreshTokens = new FakeRefreshTokens();
        String token = refreshTokens.issue(42L, Duration.ofDays(7));
        AuthService service = service(new FakeUsers(enabledUser(42L, "baron", "secret")), refreshTokens);

        service.logout(token);
        service.logout(token);

        assertThat(refreshTokens.contains(token)).isFalse();
    }

    @Test
    void domainEnumsContainOnlySupportedValues() {
        assertThat(UserRole.values()).containsExactly(UserRole.ADMIN, UserRole.USER);
        assertThat(UserStatus.values()).containsExactly(UserStatus.ENABLED, UserStatus.DISABLED);
    }

    @Test
    void userAccountStringRepresentationDoesNotExposePasswordHash() {
        UserAccount user = enabledUser(42L, "baron", "secret");

        assertThat(user.toString())
                .contains("42", "baron", "Baron", "USER", "ENABLED")
                .doesNotContain(user.passwordHash(), "password", "passwordHash");
    }

    private static AuthService service(FakeUsers users, FakeRefreshTokens refreshTokens) {
        return service(users, refreshTokens, PASSWORDS);
    }

    private static AuthService service(
            FakeUsers users, FakeRefreshTokens refreshTokens, PasswordEncoder passwordEncoder) {
        byte[] secret = "test-only-auth-jwt-secret-at-least-32-bytes".getBytes();
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        TokenService tokenService =
                new TokenService(jwtEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
        return new AuthService(users, passwordEncoder, tokenService, refreshTokens);
    }

    private static UserAccount enabledUser(long id, String username, String password) {
        return user(id, username, password, UserStatus.ENABLED);
    }

    private static UserAccount disabledUser(long id, String username, String password) {
        return user(id, username, password, UserStatus.DISABLED);
    }

    private static UserAccount user(long id, String username, String password, UserStatus status) {
        return new UserAccount(
                id,
                username,
                PASSWORDS.encode(password),
                "Baron",
                UserRole.USER,
                status);
    }

    private static void assertGenericLoginFailure(
            AuthService service, String username, String password) {
        assertThatThrownBy(() -> service.login(username, password))
                .isExactlyInstanceOf(AuthenticationFailedException.class)
                .hasMessage("用户名或密码错误");
    }

    private static final class FakeUsers implements UserRepository {
        private final Map<Long, UserAccount> byId = new HashMap<>();
        private final Map<String, UserAccount> byUsername = new HashMap<>();

        private FakeUsers(UserAccount... users) {
            for (UserAccount user : users) {
                byId.put(user.id(), user);
                byUsername.put(user.username(), user);
            }
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public Optional<UserAccount> findById(long userId) {
            return Optional.ofNullable(byId.get(userId));
        }
    }

    private static final class FakeRefreshTokens implements RefreshTokenStore {
        private final Map<String, Long> tokens = new HashMap<>();
        private Duration lastIssuedTtl;

        @Override
        public String issue(long userId, Duration ttl) {
            String token = UUID.randomUUID().toString();
            tokens.put(token, userId);
            lastIssuedTtl = ttl;
            return token;
        }

        @Override
        public long consume(String rawToken) {
            Long userId = tokens.remove(rawToken);
            if (userId == null) {
                throw new InvalidRefreshTokenException();
            }
            return userId;
        }

        @Override
        public void revoke(String rawToken) {
            tokens.remove(rawToken);
        }

        private boolean contains(String rawToken) {
            return tokens.containsKey(rawToken);
        }

        private Duration lastIssuedTtl() {
            return lastIssuedTtl;
        }
    }

    private static final class RecordingPasswordEncoder implements PasswordEncoder {
        private final boolean result;
        private int matchesCalls;
        private String lastEncodedPassword;

        private RecordingPasswordEncoder(boolean result) {
            this.result = result;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchesCalls++;
            lastEncodedPassword = encodedPassword;
            return result;
        }

        private int matchesCalls() {
            return matchesCalls;
        }

        private String lastEncodedPassword() {
            return lastEncodedPassword;
        }
    }
}
