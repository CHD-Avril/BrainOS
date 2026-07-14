package com.brainos.auth.token;

import java.time.Duration;

public interface RefreshTokenStore {

    String issue(long userId, Duration ttl);

    long consume(String rawToken);

    void revoke(String rawToken);
}
