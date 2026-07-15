package com.brainos.auth.application;

import com.brainos.admin.audit.AuditEvent;
import com.brainos.admin.audit.AuditRecorder;
import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.token.InvalidRefreshTokenException;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.auth.token.TokenService;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public final class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$CTO/s0AkBHtRLsofM0C2t.tIHzItCS0m.Ak/BSehB0SZIrSinHobu";
    private static final Pattern REQUIRED_BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenStore refreshTokens;
    private final AuditRecorder auditRecorder;

    public AuthService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            RefreshTokenStore refreshTokens,
            AuditRecorder auditRecorder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.auditRecorder = auditRecorder;
    }

    public TokenPair login(String username, String password) {
        Optional<UserAccount> candidate = users.findByUsername(username);
        String storedHash = candidate.map(UserAccount::passwordHash).orElse(null);
        boolean hashIsUsable =
                storedHash != null && REQUIRED_BCRYPT_HASH.matcher(storedHash).matches();
        String passwordHash = hashIsUsable ? storedHash : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        Optional<UserAccount> authenticated = candidate
                .filter(found -> hashIsUsable && found.isEnabled() && passwordMatches);
        if (authenticated.isEmpty()) {
            recordAudit(AuditEvent.loginFailure(candidate.map(UserAccount::id).orElse(null)));
            throw new AuthenticationFailedException();
        }
        UserAccount user = authenticated.orElseThrow();
        TokenPair pair = issuePair(user);
        recordAudit(AuditEvent.loginSuccess(user.id()));
        return pair;
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

    private void recordAudit(AuditEvent event) {
        try {
            auditRecorder.record(event);
        } catch (RuntimeException ignored) {
            LOGGER.error("认证审计事件写入失败，认证结果不受影响");
        }
    }
}
