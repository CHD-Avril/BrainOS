package com.brainos.auth.application;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.token.InvalidRefreshTokenException;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.auth.token.TokenService;
import java.time.Duration;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public final class AuthService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$CTO/s0AkBHtRLsofM0C2t.tIHzItCS0m.Ak/BSehB0SZIrSinHobu";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenStore refreshTokens;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RefreshTokenStore refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
    }

    public TokenPair login(String username, String password) {
        Optional<UserAccount> candidate = users.findByUsername(username);
        String passwordHash = candidate.map(UserAccount::passwordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        UserAccount user = candidate
                .filter(found -> found.isEnabled() && passwordMatches)
                .orElseThrow(AuthenticationFailedException::new);
        return issuePair(user);
    }

    public TokenPair refresh(String refreshToken) {
        long userId = refreshTokens.consume(refreshToken);
        UserAccount user = users.findById(userId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::new);
        return issuePair(user);
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private TokenPair issuePair(UserAccount user) {
        return new TokenPair(
                tokenService.createAccessToken(user),
                refreshTokens.issue(user.id(), REFRESH_TOKEN_TTL),
                user.toSummary());
    }
}
