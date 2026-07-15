package com.brainos.auth.security;

import com.brainos.auth.domain.UserRole;
import java.security.Principal;

public record UserPrincipal(Long userId, String username, UserRole role) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
