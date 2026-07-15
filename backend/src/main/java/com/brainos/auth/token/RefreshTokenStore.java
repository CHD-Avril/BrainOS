package com.brainos.auth.token;

import java.time.Duration;

public interface RefreshTokenStore {

    String issue(long userId, Duration ttl);

    long consume(String rawToken);

    void revoke(String rawToken);

    default void revokeAll(long userId) {
        // Stores without per-user indexing may rely on the mandatory status check during refresh.
    }
}
