package com.brainos.admin.audit;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.common.api.PagedResult;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuditLogService {

    private static final Pattern ACTION = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Pattern USERNAME = Pattern.compile("[a-z][a-z0-9_.-]{2,31}");

    private final AuditMapper audits;

    public AuditLogService(AuditMapper audits) {
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public PagedResult<AuditLogView> list(
            Long userId,
            String rawUsername,
            String rawAction,
            Instant from,
            Instant to,
            int page,
            int size) {
        if ((userId != null && userId <= 0)
                || page < 1
                || size < 1
                || size > 100
                || (from != null && to != null && from.isAfter(to))) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        String username = normalizeUsername(rawUsername);
        String action = normalizeAction(rawAction);
        long total = audits.count(userId, username, action, from, to);
        return new PagedResult<>(
                audits.findPage(
                        userId,
                        username,
                        action,
                        from,
                        to,
                        size,
                        (long) (page - 1) * size),
                total,
                page,
                size);
    }

    private static String normalizeUsername(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String username = raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return username;
    }

    private static String normalizeAction(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String action = raw.trim().toUpperCase(Locale.ROOT);
        if (!ACTION.matcher(action).matches()) throw new ApiException(ErrorCode.VALIDATION_ERROR);
        return action;
    }
}
