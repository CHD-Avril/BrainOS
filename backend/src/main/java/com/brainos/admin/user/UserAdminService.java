package com.brainos.admin.user;

import com.brainos.admin.audit.AuditEvent;
import com.brainos.admin.audit.AuditRecorder;
import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;
import com.brainos.auth.token.RefreshTokenStore;
import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.common.api.PagedResult;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class UserAdminService {

    private static final Pattern USERNAME = Pattern.compile("[a-z][a-z0-9_.-]{2,31}");

    private final AdminUserMapper users;
    private final UserRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokens;
    private final AuditRecorder audit;

    public UserAdminService(
            AdminUserMapper users,
            UserRepository accounts,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokens,
            AuditRecorder audit) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminUserView> list(int page, int size) {
        validatePage(page, size);
        long total = users.countAll();
        return new PagedResult<>(users.findPage(size, (long) (page - 1) * size), total, page, size);
    }

    @Transactional
    public AdminUserView create(
            String rawUsername,
            String rawDisplayName,
            String rawPassword,
            UserRole role,
            long actorId) {
        String username = normalizeUsername(rawUsername);
        String displayName = normalizeDisplayName(rawDisplayName);
        String password = validatePassword(rawPassword);
        requireRole(role);
        if (users.existsByUsername(username)) throw new ApiException(ErrorCode.CONFLICT);
        AdminUserEntity user = new AdminUserEntity(
                null,
                username,
                passwordEncoder.encode(password),
                displayName,
                role,
                UserStatus.ENABLED);
        try {
            users.create(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        audit.record(AuditEvent.userCreated(actorId, user.getId(), username));
        return requiredView(user.getId());
    }

    @Transactional
    public AdminUserView update(
            long id,
            String rawDisplayName,
            UserRole role,
            String rawPassword,
            long actorId) {
        UserAccount current = requiredAccount(id);
        requireRole(role);
        if (current.role() == UserRole.ADMIN
                && current.status() == UserStatus.ENABLED
                && role != UserRole.ADMIN
                && users.countEnabledAdmins() <= 1) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        String passwordHash = rawPassword == null || rawPassword.isBlank()
                ? null
                : passwordEncoder.encode(validatePassword(rawPassword));
        if (users.update(id, normalizeDisplayName(rawDisplayName), role, passwordHash) != 1) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (passwordHash != null || current.role() != role) refreshTokens.revokeAll(id);
        audit.record(AuditEvent.userUpdated(actorId, id));
        return requiredView(id);
    }

    @Transactional
    public AdminUserView changeStatus(long id, UserStatus status, long actorId) {
        UserAccount current = requiredAccount(id);
        if (status == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        if (current.role() == UserRole.ADMIN
                && current.status() == UserStatus.ENABLED
                && status == UserStatus.DISABLED
                && users.countEnabledAdmins() <= 1) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        if (users.updateStatus(id, status) != 1) throw new ApiException(ErrorCode.NOT_FOUND);
        if (status == UserStatus.DISABLED) refreshTokens.revokeAll(id);
        audit.record(AuditEvent.userStatusChanged(actorId, id, status));
        return requiredView(id);
    }

    private UserAccount requiredAccount(long id) {
        if (id <= 0) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return accounts.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private AdminUserView requiredView(long id) {
        return users.findViewById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static String normalizeUsername(String raw) {
        if (raw == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(value).matches()) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return value;
    }

    private static String normalizeDisplayName(String raw) {
        if (raw == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 100) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return value;
    }

    private static String validatePassword(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 72
                || raw.chars().noneMatch(Character::isLowerCase)
                || raw.chars().noneMatch(Character::isUpperCase)
                || raw.chars().noneMatch(Character::isDigit)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return raw;
    }

    private static void requireRole(UserRole role) {
        if (role == null) throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
}
