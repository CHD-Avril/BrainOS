package com.brainos.auth.domain;

public record UserAccount(
        long id,
        String username,
        String passwordHash,
        String displayName,
        UserRole role,
        UserStatus status) {

    public boolean isEnabled() {
        return status == UserStatus.ENABLED;
    }

    public UserSummary toSummary() {
        return new UserSummary(id, username, displayName, role);
    }

    @Override
    public String toString() {
        return "UserAccount[id=" + id
                + ", username=" + username
                + ", displayName=" + displayName
                + ", role=" + role
                + ", status=" + status
                + "]";
    }
}
