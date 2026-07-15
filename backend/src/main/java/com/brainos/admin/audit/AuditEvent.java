package com.brainos.admin.audit;

import com.brainos.auth.domain.UserStatus;

public record AuditEvent(
        Long userId,
        String action,
        String targetType,
        String targetId,
        String result,
        String summary) {

    public AuditEvent {
        summary = sanitize(summary);
    }

    private static final String LOGIN_ACTION = "AUTH_LOGIN";
    private static final String USER_TARGET = "USER";

    public static AuditEvent loginSuccess(long userId) {
        return login(userId, "SUCCESS", "登录成功");
    }

    public static AuditEvent loginFailure(Long userId) {
        return login(userId, "FAILURE", "登录失败");
    }

    public static AuditEvent userCreated(long actorId, long targetId, String username) {
        return success(actorId, "USER_CREATE", "USER", targetId, "创建用户：" + username);
    }

    public static AuditEvent userUpdated(long actorId, long targetId) {
        return success(actorId, "USER_UPDATE", "USER", targetId, "更新用户资料");
    }

    public static AuditEvent userStatusChanged(
            long actorId, long targetId, UserStatus status) {
        return success(
                actorId,
                "USER_STATUS_CHANGE",
                "USER",
                targetId,
                status == UserStatus.ENABLED ? "启用用户" : "停用用户");
    }

    public static AuditEvent knowledgeCreated(long actorId, long targetId) {
        return success(actorId, "KNOWLEDGE_CREATE", "KNOWLEDGE_BASE", targetId, "创建知识库");
    }

    public static AuditEvent knowledgeUpdated(long actorId, long targetId) {
        return success(actorId, "KNOWLEDGE_UPDATE", "KNOWLEDGE_BASE", targetId, "更新知识库");
    }

    public static AuditEvent knowledgeDeleted(long actorId, long targetId) {
        return success(actorId, "KNOWLEDGE_DELETE", "KNOWLEDGE_BASE", targetId, "删除知识库");
    }

    public static AuditEvent documentUploaded(long actorId, long targetId) {
        return success(actorId, "DOCUMENT_UPLOAD", "DOCUMENT", targetId, "上传文档");
    }

    public static AuditEvent documentRetried(long actorId, long targetId) {
        return success(actorId, "DOCUMENT_RETRY", "DOCUMENT", targetId, "重试文档索引");
    }

    public static AuditEvent documentDeleted(long actorId, long targetId) {
        return success(actorId, "DOCUMENT_DELETE", "DOCUMENT", targetId, "删除文档");
    }

    private static AuditEvent login(Long userId, String result, String summary) {
        String targetId = userId == null ? null : userId.toString();
        return new AuditEvent(userId, LOGIN_ACTION, USER_TARGET, targetId, result, summary);
    }

    private static AuditEvent success(
            long actorId, String action, String targetType, long targetId, String summary) {
        return new AuditEvent(
                actorId, action, targetType, Long.toString(targetId), "SUCCESS", summary);
    }

    private static String sanitize(String value) {
        if (value == null) return null;
        String normalized = value.length() > 500 ? value.substring(0, 500) : value;
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        for (String sensitive : java.util.List.of(
                "password", "authorization", "bearer ", "token", "api_key", "apikey", "secret")) {
            if (lower.contains(sensitive)) return "内容已脱敏";
        }
        return normalized;
    }
}
