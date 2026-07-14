package com.brainos.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");

    @Test
    void accessTokenHasOnlyApprovedClaimsAndExactlyTwoHourLifetime() {
        SecretKey key = new SecretKeySpec(
                "test-only-auth-jwt-secret-at-least-32-bytes".getBytes(), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        TokenService service = new TokenService(encoder, Clock.fixed(NOW, ZoneOffset.UTC));
        UserAccount user = new UserAccount(
                42L,
                "baron",
                "$2a$12$not-returned",
                "Baron",
                UserRole.USER,
                UserStatus.ENABLED);

        Jwt decoded = decoder.decode(service.createAccessToken(user));

        assertThat(decoded.getClaims().keySet())
                .containsExactlyInAnyOrderElementsOf(Set.of("sub", "role", "iat", "exp", "jti"));
        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("USER");
        assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(decoded.getId()).isNotBlank();
    }
}
