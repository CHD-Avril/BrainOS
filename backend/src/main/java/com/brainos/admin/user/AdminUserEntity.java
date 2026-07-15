package com.brainos.admin.user;

import com.brainos.auth.domain.UserRole;
import com.brainos.auth.domain.UserStatus;

public final class AdminUserEntity {

    private Long id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final UserRole role;
    private final UserStatus status;

    public AdminUserEntity(
            Long id,
            String username,
            String passwordHash,
            String displayName,
            UserRole role,
            UserStatus status) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }

    public void assignId(long id) {
        if (this.id != null) throw new IllegalStateException("User id is already assigned");
        this.id = id;
    }
}
