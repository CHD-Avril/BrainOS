package com.brainos.auth.token;

import com.brainos.auth.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public final class TokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    public String createAccessToken(UserAccount user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(user.id()))
                .claim("role", user.role().name())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ACCESS_TOKEN_TTL))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
