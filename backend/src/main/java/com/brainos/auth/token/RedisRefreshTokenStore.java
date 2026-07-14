package com.brainos.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String USER_KEY_PREFIX = "auth:user-refresh:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redis;
    private final SecureRandom secureRandom;

    public RedisRefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String issue(long userId, Duration ttl) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenKey = keyFor(rawToken);
        redis.opsForValue().set(tokenKey, Long.toString(userId), ttl);
        redis.opsForSet().add(userKey(userId), tokenKey);
        redis.expire(userKey(userId), ttl);
        return rawToken;
    }

    @Override
    public long consume(String rawToken) {
        String userId = redis.opsForValue().getAndDelete(keyFor(rawToken));
        if (userId == null) {
            throw new InvalidRefreshTokenException();
        }
        try {
            long parsed = Long.parseLong(userId);
            redis.opsForSet().remove(userKey(parsed), keyFor(rawToken));
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidRefreshTokenException();
        }
    }

    @Override
    public void revoke(String rawToken) {
        String tokenKey = keyFor(rawToken);
        String userId = redis.opsForValue().get(tokenKey);
        redis.delete(tokenKey);
        if (userId != null) {
            try {
                redis.opsForSet().remove(userKey(Long.parseLong(userId)), tokenKey);
            } catch (NumberFormatException ignored) {
                // Invalid values are deleted above and cannot be consumed again.
            }
        }
    }

    @Override
    public void revokeAll(long userId) {
        String userKey = userKey(userId);
        var tokenKeys = redis.opsForSet().members(userKey);
        if (tokenKeys != null && !tokenKeys.isEmpty()) redis.delete(tokenKeys);
        redis.delete(userKey);
    }

    private static String keyFor(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String userKey(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        return USER_KEY_PREFIX + userId;
    }
}
