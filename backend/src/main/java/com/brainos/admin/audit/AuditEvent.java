package com.brainos.admin.audit;

public record AuditEvent(
        Long userId,
        String action,
        String targetType,
        String targetId,
        String result,
        String summary) {

    private static final String LOGIN_ACTION = "AUTH_LOGIN";
    private static final String USER_TARGET = "USER";

    public static AuditEvent loginSuccess(long userId) {
        return login(userId, "SUCCESS", "登录成功");
    }

    public static AuditEvent loginFailure(Long userId) {
        return login(userId, "FAILURE", "登录失败");
    }

    private static AuditEvent login(Long userId, String result, String summary) {
        String targetId = userId == null ? null : userId.toString();
        return new AuditEvent(userId, LOGIN_ACTION, USER_TARGET, targetId, result, summary);
    }
}
