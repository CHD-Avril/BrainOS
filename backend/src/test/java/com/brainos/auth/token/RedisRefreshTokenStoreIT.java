package com.brainos.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = false)
class RedisRefreshTokenStoreIT {

    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(false);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private RedisRefreshTokenStore store;

    @BeforeAll
    static void connect() {
        connectionFactory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void resetRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        store = new RedisRefreshTokenStore(redis);
    }

    @Test
    void issueStoresOnlySha256DigestWithUserAndSevenDayTtl() throws Exception {
        String rawToken = store.issue(42L, SEVEN_DAYS);

        assertThat(Base64.getUrlDecoder().decode(rawToken)).hasSize(32);
        assertThat(rawToken).doesNotContain("=");
        String expectedKey = "auth:refresh:" + sha256(rawToken);
        Set<String> keys = redis.keys("*");
        assertThat(keys).containsExactly(expectedKey);
        assertThat(expectedKey).doesNotContain(rawToken);
        assertThat(redis.opsForValue().get(expectedKey)).isEqualTo("42");
        assertThat(redis.getExpire(expectedKey, TimeUnit.SECONDS))
                .isBetween(SEVEN_DAYS.toSeconds() - 10, SEVEN_DAYS.toSeconds());
    }

    @Test
    void consumeIsAtomicAndRejectsEveryReplay() throws Exception {
        String rawToken = store.issue(42L, SEVEN_DAYS);
        int consumers = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(consumers);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (int index = 0; index < consumers; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        return store.consume(rawToken);
                    } catch (InvalidRefreshTokenException exception) {
                        return exception;
                    }
                }));
            }
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(10, TimeUnit.SECONDS));
            }
            assertThat(outcomes).filteredOn(Long.class::isInstance).containsExactly(42L);
            assertThat(outcomes)
                    .filteredOn(InvalidRefreshTokenException.class::isInstance)
                    .hasSize(consumers - 1)
                    .allSatisfy(error -> assertThat(((Exception) error).getMessage())
                            .isEqualTo("刷新令牌无效"));
            assertThat(redis.keys("auth:refresh:*")).isEmpty();
        } finally {
            executor.shutdownNow();
        }

        assertThatThrownBy(() -> store.consume(rawToken))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("刷新令牌无效");
    }

    @Test
    void expiredAndUnknownTokensHaveTheSameFailure() throws Exception {
        String expired = store.issue(42L, Duration.ofMillis(50));
        awaitKeyAbsent("auth:refresh:" + sha256(expired), Duration.ofSeconds(5));

        assertInvalid(expired);
        assertInvalid("unknown-token");
    }

    @Test
    void revokeDeletesDigestAndIsIdempotent() throws Exception {
        String rawToken = store.issue(42L, SEVEN_DAYS);
        String key = "auth:refresh:" + sha256(rawToken);

        store.revoke(rawToken);

        assertThat(redis.hasKey(key)).isFalse();
        assertThatCode(() -> store.revoke(rawToken)).doesNotThrowAnyException();
        assertInvalid(rawToken);
    }

    private static void assertInvalid(String token) {
        assertThatThrownBy(() -> new RedisRefreshTokenStore(redis).consume(token))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("刷新令牌无效");
    }

    private static String sha256(String rawToken) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private static void awaitKeyAbsent(String key, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (Boolean.TRUE.equals(redis.hasKey(key))) {
            if (System.nanoTime() >= deadline) {
                fail("Redis key did not expire within " + timeout);
            }
            Thread.sleep(10);
        }
    }
}
