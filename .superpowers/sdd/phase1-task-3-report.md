# Phase 1 Task 3 SDD Report

## Scope

- Base commit: `1dc62c3b54b53d0079c462aa436ced3e3a7fafa9`
- Branch: `Baron`
- Implemented authentication domain types, a narrow `UserRepository`, the MyBatis `UserMapper`, login/refresh/logout application behavior, HS256 access-token signing, Redis refresh-token issue/consume/revoke, Spring production wiring, and JWT secret configuration.
- Added production-facing unit, configuration-contract, MySQL Testcontainer, and Redis Testcontainer coverage.
- Did not implement controllers, Spring Security filters, or frontend work. Did not change specs or plans.

## TDD Evidence

### Authentication application and JWT

1. Wrote `AuthServiceTest` and `TokenServiceTest` before production authentication types existed.
2. RED: `cd backend && ./mvnw -Dtest=AuthServiceTest,TokenServiceTest test`
   - Exit code: `1`
   - Expected cause: test compilation failed with 33 errors for missing auth domain/application/token types and the missing OAuth2 JOSE dependency.
3. Added the minimal domain, repository port, application service, refresh-store port, token service, and JOSE dependency.
4. GREEN: the same focused command ran `7` tests with `0` failures, `0` errors, and `0` skipped.

### JWT secret configuration contract

1. Extended `ConfigurationContractTest` first with the dev demo value, mandatory non-dev `BRAINOS_JWT_SECRET`, complete prod binding, and `.env.example` documentation assertions.
2. RED: `cd backend && ./mvnw -Dtest=ConfigurationContractTest test`
   - Exit code: `1`
   - `13` tests ran: `2` failures, `2` errors, `0` skipped.
   - Expected causes: the JWT property did not exist, missing prod JWT secret did not stop startup, and `.env.example` lacked the variable.
3. Added validated `JwtProperties`, `JwtEncoder`/`TokenService`/`PasswordEncoder` configuration, profile-specific YAML, and the blank `.env.example` variable.
4. GREEN: the focused configuration command ran `13` tests with `0` failures, `0` errors, and `0` skipped.

### MyBatis user lookup

1. Wrote `UserMapperIT` against a fresh MySQL 8.4 Testcontainer and the V1/V2 schema before `UserMapper` existed.
2. RED: `cd backend && ./mvnw -Dit.test=UserMapperIT verify`
   - Exit code: `1`
   - Expected cause: test compilation failed because `UserMapper` did not exist.
3. Added a parameterized, annotated MyBatis mapper that extends the narrow application-facing repository port.
4. GREEN: the focused Failsafe command ran `27` unit tests plus `1` selected IT, all with `0` skipped.

### Redis refresh-token store

1. Wrote `RedisRefreshTokenStoreIT` first using `redis:7.4-alpine`, `withReuse(false)`, and `@Testcontainers(disabledWithoutDocker = false)`.
2. RED: `cd backend && ./mvnw -Dit.test=RedisRefreshTokenStoreIT verify`
   - Exit code: `1`
   - Expected cause: test compilation failed because `RedisRefreshTokenStore` did not exist.
3. Added 32-byte `SecureRandom` Base64URL tokens, lowercase SHA-256 key derivation, atomic `getAndDelete`, TTL writes, uniform invalid-token errors, and idempotent deletion.
4. GREEN: the focused Failsafe command ran `27` unit tests plus `4` Redis ITs, all with `0` skipped.
5. The IT proves the database contains only the digest key and user ID, TTL is within 10 seconds of exactly seven days, 16 concurrent consumers yield exactly one success, all replays fail, expired/unknown failures match, and revoke removes the digest.

### Review-driven security and wiring fixes

1. Added real Spring wiring assertions to `UserMapperIT` before registering `AuthService`.
2. RED: `cd backend && ./mvnw -Dit.test=UserMapperIT verify`
   - The selected IT reported `2` errors caused by `NoSuchBeanDefinitionException: AuthService`; `0` skipped.
3. Registered `AuthService` as a production Spring service and provided an empty repository fake only to existing context-runner tests that intentionally exclude DataSource auto-configuration.
4. GREEN: the focused command ran `27` unit tests plus `2` MyBatis/wiring ITs with `0` failures, `0` errors, and `0` skipped.
5. Added password-comparison count and `UserAccount.toString()` redaction assertions before changing their production behavior.
6. RED: `cd backend && ./mvnw -Dtest=AuthServiceTest test`
   - `8` tests ran: `2` expected failures, `0` errors, `0` skipped.
   - Missing/disabled users performed zero password comparisons, and the record `toString()` exposed the bcrypt hash.
7. Added a valid fixed cost-12 dummy BCrypt hash, made every normal rejected-login path perform exactly one `PasswordEncoder.matches`, and redacted the record string representation.
8. GREEN: `cd backend && ./mvnw -Dtest=AuthServiceTest,ConfigurationContractTest,ComposeContractTest test`
   - `22` tests passed with `0` skipped.
9. Replaced fixed Redis expiry sleep with a 10 ms bounded poll that fails after 5 seconds, and made repository test-file lookup walk upward at most eight parent levels.
10. GREEN: `cd backend && ./mvnw -Dit.test=RedisRefreshTokenStoreIT verify`
    - `29` unit tests plus `4` selected Redis ITs passed with `0` skipped.

## Requirement Self-Check

- `UserRole` contains only `ADMIN` and `USER`; `UserStatus` contains only `ENABLED` and `DISABLED`.
- `UserSummary` has no password field, and `UserAccount.toString()` omits password material.
- Missing user, bad password, and disabled user throw exactly `AuthenticationFailedException` with `用户名或密码错误` and each performs one password comparison.
- Access tokens use `JwtEncoder`, last exactly two hours, and decode to only `sub`, `role`, `iat`, `exp`, and `jti`.
- Refresh tokens are 32 CSPRNG bytes encoded without Base64 padding. Redis keys are `auth:refresh:{lowercase SHA-256}`, values are user IDs, and the application passes a seven-day TTL.
- Atomic Redis `GETDEL` semantics allow one consumer only; old tokens, expired tokens, unknown tokens, and revoked tokens cannot be replayed.
- Refresh consumes the old token before reloading the user and only issues a new pair for a still-enabled account. Missing or disabled users leave the old token consumed.
- `UserMapper` queries `sys_user`; `AuthService` depends on `UserRepository`, not on MyBatis.
- Redis IT uses a non-reusable `redis:7.4-alpine` container and cannot silently skip when Docker is absent. MyBatis lookup and production wiring run against a fresh MySQL container.
- Dev has an explicitly named demo JWT secret. Every non-dev profile requires `BRAINOS_JWT_SECRET`; `.env.example` documents a blank override and no real secret is committed.
- No controller or security filter was added.

## Final Verification

- Focused application/config/compose: `22` passed, `0` skipped.
- Focused Redis lifecycle: `29` unit + `4` Redis IT passed, `0` skipped.
- `cd backend && ./mvnw test`: `29` passed, `0` failures, `0` errors, `0` skipped, exit code `0`.
- `cd backend && ./mvnw verify`: `29` unit + `8` IT = `37` passed, `0` failures, `0` errors, `0` skipped, exit code `0`.
- Docker was available and Testcontainers connected to Docker Server `29.1.2`; Redis and both MySQL containers started during the final verify.
- `git diff --check`: exit code `0`, no output.
- Secret/log scan found no private-key material, populated `BRAINOS_JWT_SECRET`, or auth token logging in production code. Test-only and explicitly named dev-demo values are not production secrets.

## Non-Blocking Warnings

- Flyway logs that MySQL 8.4 is newer than its latest tested MySQL 8.1 version; the existing and new migrations still pass against the pinned project image.
- The existing Spring test stack logs Mockito/Byte Buddy dynamic-agent deprecation warnings on Java 21; no tests are skipped or failed.

## Official Review Follow-Up: BCrypt Cost Normalization

### Finding

The original login path delegated every stored BCrypt hash to Spring Security. Because BCrypt derives its work factor from the stored hash, legacy low-cost hashes, higher-cost hashes, malformed values, and a missing-user dummy comparison did not have a uniform cost. This created a timing distinction at the authentication boundary.

### TDD Evidence

- RED: `./mvnw -Dtest=AuthServiceTest test` ran 11 tests with 1 expected failure. A user holding a valid cost-4 BCrypt hash authenticated successfully, proving the review finding.
- GREEN: the same focused command passed 11/11 tests with 0 failures, 0 errors, and 0 skipped tests.
- The rejection matrix covers missing users, wrong passwords, disabled users, cost 4/10/13 hashes, malformed hashes, unsupported BCrypt versions, blank hashes, and null hashes.
- The recording test double delegates to a real `BCryptPasswordEncoder(12)`, proving each rejected attempt performs exactly one legal cost-12 BCrypt comparison. Supported `$2a$`, `$2b$`, and `$2y$` cost-12 hashes remain accepted.

### Fix

`AuthService` now accepts only structurally valid 60-character BCrypt hashes with versions `2a`, `2b`, or `2y` and an exact cost of 12. Missing, malformed, unsupported, blank, null, and non-cost-12 stored hashes are replaced by the fixed cost-12 dummy hash for comparison and are always rejected with the same generic authentication error. Malformed values never reach the BCrypt delegate, so they cannot cause parser warnings or exceptions.

### Write Boundary

- `AuthTokenConfiguration` provides `BCryptPasswordEncoder(12)` for application password encoding.
- `AdminSeedFlywayConfiguration` also uses `BCryptPasswordEncoder(12)` to supply `${adminPasswordHash}` to the V2 Flyway migration.
- No user-creation path was added in this task; the existing production and seed write paths both produce cost-12 hashes.

### Verification

- `./mvnw test`: 32/32 tests passed, 0 skipped.
- `./mvnw verify`: 32 unit tests plus 8 integration tests passed (40/40 total), 0 skipped. Redis and MySQL Testcontainers started successfully.
