package com.brainos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.admin.audit.AuditEvent;
import com.brainos.admin.audit.AuditRecorder;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");
    private static final String COST_4_HASH =
            "$2a$04$snjIMKdF.ussZlpfrYVfp.jScDNgjiUpRQ6QeYjEx1c8dZ16n3cY.";
    private static final String COST_10_HASH =
            "$2a$10$OS1nnw1k4loza8iJPrZSbOewXQpelr1BjtHPyt1UWcCgPLznsYLWO";
    private static final String COST_12_HASH =
            "$2a$12$Q30uo7US/EGENuf9jJ4nDeMSV0lW1S3cZgMWGk.A0z5SgxUtjhxve";
    private static final String COST_13_HASH =
            "$2a$13$VS/W3NH6Yyqzu5cZLJ0xLO/GNJHteK8US3fxSR9eZ7jYLyWDDrQ2C";
    private static final String DUMMY_PASSWORD = "BrainOS-dummy-password";

    @Test
    void enabledUserReceivesAccessRefreshAndPasswordFreeSummary() {
        FakeUsers users = new FakeUsers(enabledUser(42L, "baron"));
        FakeRefreshTokens refreshTokens = new FakeRefreshTokens();
        RecordingAudit audit = new RecordingAudit();
        AuthService service = service(users, refreshTokens, audit);

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
        assertThat(audit.events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.userId()).isEqualTo(42L);
                    assertThat(event.action()).isEqualTo("AUTH_LOGIN");
                    assertThat(event.targetType()).isEqualTo("USER");
                    assertThat(event.targetId()).isEqualTo("42");
                    assertThat(event.result()).isEqualTo("SUCCESS");
                    assertThat(event.summary()).isEqualTo("登录成功");
                    assertThat(event.toString())
                            .doesNotContain(
                                    "baron", "secret", pair.accessToken(), pair.refreshToken());
                });
    }

    @Test
    void rejectedLoginsShareGenericFailureAndRecordSafeAuditEvents() {
        RecordingAudit missingAudit = new RecordingAudit();
        RecordingAudit wrongPasswordAudit = new RecordingAudit();
        RecordingAudit disabledAudit = new RecordingAudit();
        AuthService missing = service(new FakeUsers(), new FakeRefreshTokens(), missingAudit);
        AuthService wrongPassword = service(
                new FakeUsers(enabledUser(42L, "baron")),
                new FakeRefreshTokens(),
                wrongPasswordAudit);
        AuthService disabled = service(
                new FakeUsers(disabledUser(42L, "baron")),
                new FakeRefreshTokens(),
                disabledAudit);

        assertGenericLoginFailure(missing, "baron", "secret");
        assertGenericLoginFailure(wrongPassword, "baron", "wrong");
        assertGenericLoginFailure(disabled, "baron", "secret");

        assertThat(missingAudit.events())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.userId()).isNull();
                    assertThat(event.targetId()).isNull();
                    assertThat(event.action()).isEqualTo("AUTH_LOGIN");
                    assertThat(event.targetType()).isEqualTo("USER");
                    assertThat(event.result()).isEqualTo("FAILURE");
                    assertThat(event.summary()).isEqualTo("登录失败");
                });
        assertThat(wrongPasswordAudit.events())
                .containsExactly(AuditEvent.loginFailure(42L));
        assertThat(disabledAudit.events()).containsExactly(AuditEvent.loginFailure(42L));
        assertThat(List.of(missingAudit, wrongPasswordAudit, disabledAudit))
                .flatExtracting(RecordingAudit::events)
                .allSatisfy(event -> assertThat(event.toString())
                        .doesNotContain("baron", "secret", "wrong", "password", "token"));
    }

    @Test
    void auditStorageFailureDoesNotChangeAuthenticationSemantics() {
        AuthService success = service(
                new FakeUsers(enabledUser(42L, "baron")),
                new FakeRefreshTokens(),
                event -> {
                    throw new IllegalStateException("storage unavailable");
                });
        AuthService failure = service(
                new FakeUsers(),
                new FakeRefreshTokens(),
                event -> {
                    throw new IllegalStateException("storage unavailable");
                });

        assertThat(success.login("baron", "secret").user().id()).isEqualTo(42L);
        assertGenericLoginFailure(failure, "baron", "secret");
    }

    @Test
    void everyRejectedLoginPerformsOneRealCost12Comparison() {
        List<RejectedLoginAttempt> attempts = List.of(
                new RejectedLoginAttempt("missing", new FakeUsers(), DUMMY_PASSWORD),
                new RejectedLoginAttempt(
                        "wrong password",
                        new FakeUsers(enabledUser(42L, "baron")),
                        "wrong"),
                new RejectedLoginAttempt(
                        "disabled cost 12",
                        new FakeUsers(disabledUser(42L, "baron")),
                        "secret"),
                new RejectedLoginAttempt(
                        "cost 4",
                        new FakeUsers(userWithHash(42L, "baron", COST_4_HASH, UserStatus.ENABLED)),
                        "secret"),
                new RejectedLoginAttempt(
                        "cost 10",
                        new FakeUsers(userWithHash(42L, "baron", COST_10_HASH, UserStatus.ENABLED)),
                        "secret"),
                new RejectedLoginAttempt(
                        "cost 13",
                        new FakeUsers(userWithHash(42L, "baron", COST_13_HASH, UserStatus.ENABLED)),
                        "secret"),
                new RejectedLoginAttempt(
                        "malformed",
                        new FakeUsers(userWithHash(
                                42L, "baron", "not-a-bcrypt-hash", UserStatus.ENABLED)),
                        DUMMY_PASSWORD),
                new RejectedLoginAttempt(
                        "unsupported variant",
                        new FakeUsers(userWithHash(
                                42L,
                                "baron",
                                "$2x$" + COST_12_HASH.substring(4),
                                UserStatus.ENABLED)),
                        DUMMY_PASSWORD),
                new RejectedLoginAttempt(
                        "blank",
                        new FakeUsers(userWithHash(42L, "baron", " ", UserStatus.ENABLED)),
                        DUMMY_PASSWORD),
                new RejectedLoginAttempt(
                        "null",
                        new FakeUsers(userWithHash(42L, "baron", null, UserStatus.ENABLED)),
                        DUMMY_PASSWORD));

        for (RejectedLoginAttempt attempt : attempts) {
            RecordingPasswordEncoder recording = new RecordingPasswordEncoder();
            AuthService service = service(attempt.users(), new FakeRefreshTokens(), recording);

            assertGenericLoginFailure(service, "baron", attempt.password());

            assertThat(recording.encodedPasswords())
                    .as(attempt.name())
                    .singleElement()
                    .satisfies(hash -> assertThat(hash)
                            .matches("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2a", "2b", "2y"})
    void enabledUserWithSupportedCost12BcryptVersionCanLogin(String version) {
        String passwordHash = "$" + version + COST_12_HASH.substring(3);
        RecordingPasswordEncoder recording = new RecordingPasswordEncoder();
        AuthService service = service(
                new FakeUsers(userWithHash(42L, "baron", passwordHash, UserStatus.ENABLED)),
                new FakeRefreshTokens(),
                recording);

        TokenPair pair = service.login("baron", "secret");

        assertThat(pair.user().id()).isEqualTo(42L);
        assertThat(recording.encodedPasswords()).containsExactly(passwordHash);
    }

    @Test
    void refreshConsumesOldTokenAndReturnsRotatedPair() {
        FakeUsers users = new FakeUsers(enabledUser(42L, "baron"));
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
                service(new FakeUsers(disabledUser(42L, "baron")), disabledTokens);

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
        AuthService service = service(new FakeUsers(enabledUser(42L, "baron")), refreshTokens);

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
        UserAccount user = enabledUser(42L, "baron");

        assertThat(user.toString())
                .contains("42", "baron", "Baron", "USER", "ENABLED")
                .doesNotContain(user.passwordHash(), "password", "passwordHash");
    }

    private static AuthService service(FakeUsers users, FakeRefreshTokens refreshTokens) {
        return service(users, refreshTokens, new RecordingAudit());
    }

    private static AuthService service(
            FakeUsers users, FakeRefreshTokens refreshTokens, AuditRecorder auditRecorder) {
        return service(users, refreshTokens, new BCryptPasswordEncoder(12), auditRecorder);
    }

    private static AuthService service(
            FakeUsers users, FakeRefreshTokens refreshTokens, PasswordEncoder passwordEncoder) {
        return service(users, refreshTokens, passwordEncoder, new RecordingAudit());
    }

    private static AuthService service(
            FakeUsers users,
            FakeRefreshTokens refreshTokens,
            PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder) {
        byte[] secret = "test-only-auth-jwt-secret-at-least-32-bytes".getBytes();
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        TokenService tokenService =
                new TokenService(jwtEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
        return new AuthService(users, passwordEncoder, tokenService, refreshTokens, auditRecorder);
    }

    private static UserAccount enabledUser(long id, String username) {
        return userWithHash(id, username, COST_12_HASH, UserStatus.ENABLED);
    }

    private static UserAccount disabledUser(long id, String username) {
        return userWithHash(id, username, COST_12_HASH, UserStatus.DISABLED);
    }

    private static UserAccount userWithHash(
            long id, String username, String passwordHash, UserStatus status) {
        return new UserAccount(
                id,
                username,
                passwordHash,
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

    private record RejectedLoginAttempt(String name, FakeUsers users, String password) {}

    private static final class RecordingAudit implements AuditRecorder {
        private final List<AuditEvent> events = new java.util.ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }

        private List<AuditEvent> events() {
            return List.copyOf(events);
        }
    }

    private static final class RecordingPasswordEncoder implements PasswordEncoder {
        private final PasswordEncoder delegate = new BCryptPasswordEncoder(12);
        private final List<String> encodedPasswords = new java.util.ArrayList<>();

        @Override
        public String encode(CharSequence rawPassword) {
            throw new UnsupportedOperationException("not needed by this test");
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            encodedPasswords.add(encodedPassword);
            return delegate.matches(rawPassword, encodedPassword);
        }

        private List<String> encodedPasswords() {
            return encodedPasswords;
        }
    }
}
