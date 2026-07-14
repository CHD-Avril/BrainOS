# Phase 1 Task 4 SDD Report

## Scope

- Base commit: `2526245ad7d2c171b11c00140e7cc447d242dbcb`
- Branch: `Baron`
- Implemented the stateless Spring Security filter chain, same-secret HS256 JWT decoder, database-backed `UserPrincipal`, JSON 401/403 handlers, and `/api/v1/auth/login|refresh|logout|me`.
- Added the real `AuthControllerIT` chain using non-reusable `mysql:8.4` and `redis:7.4-alpine` Testcontainers with dynamic datasource/Redis properties.
- Did not add an admin controller or any Task 5 endpoint. Did not modify frontend, specs, or plans.

## TDD Evidence

### Security boundary cycle

1. Added only two integration tests first: anonymous `/me` and USER access to the nonexistent admin probe.
2. RED: `cd backend && ./mvnw -Dit.test=AuthControllerIT verify`
   - Failsafe executed `2` ITs with `2` expected failures and `0` skipped.
   - Anonymous `/me` returned Spring Security's default empty 401 body, so `$.code` was absent.
   - The USER bearer was not decoded, so the admin probe returned 401 instead of 403.
3. Added the minimum decoder/filter/principal/filter-chain implementation and JSON security handlers.
4. The first GREEN attempt exposed an existing unit regression: `BrainOsApplicationTest` uses a non-Web `ApplicationContextRunner`, where an unconditional `SecurityConfig` could not obtain `HttpSecurity`.
5. Added the minimal servlet-only condition to `SecurityConfig` and reran the same command.
6. GREEN: Surefire `32` + selected Failsafe `2`, all with `0` failures/errors/skips.

### Auth API and invalid-token cycle

1. Extended `AuthControllerIT` first with seeded-admin login, generic login failure, validation, refresh rotation/replay, protected logout/revocation, live-database `/me`, invalid access tokens, ADMIN authorization, and exact public-path coverage.
2. RED: `cd backend && ./mvnw -Dit.test=AuthControllerIT verify`
   - Failsafe executed `11` ITs: `8` expected failures, `1` error, `0` skipped.
   - The API cases returned 404 because `AuthController` did not exist.
   - The invalid-token matrix also exposed a missing-role claim NPE in the new filter.
3. Added `LoginRequest`, the separate validated `RefreshTokenRequest`, password-free `TokenResponse`, `AuthController`, explicit authentication/refresh exception mappings, and missing-role anonymous handling.
4. GREEN: Surefire `32` + selected Failsafe `11`, all with `0` failures/errors/skips.

### Exact public-list cycle

1. Added an anonymous `POST /logout` assertion during final scope review because Spring's default logout filter runs before authorization.
2. RED: the focused verify ran unit `32` successfully, then `AuthControllerIT` reported `1` failure of `11`, `0` skipped: `/logout` returned a 302 redirect to `/login?logout` instead of JSON 401.
3. Disabled Spring's default logout filter; the product's refresh-token logout remains `/api/v1/auth/logout` and requires JWT authentication.
4. GREEN: the focused verify ran unit `32` + selected IT `11`, all passed with `0` skipped.
5. During this cycle, the existing fixed-time `TokenServiceTest` crossed its hard-coded token expiry because the environment clock advanced. Its decoder validator now uses the same fixed test clock as the encoder, keeping the Task 3 two-hour-claim test deterministic without changing production behavior.

## Requirement Self-Check

- Security is stateless, request cache/CSRF/form login/HTTP Basic/default logout are disabled, and only the approved exact auth/health/docs patterns are public.
- `/api/v1/admin/**` requires live `ROLE_ADMIN`; every other path requires authentication. A real USER receives JSON 403 before handler lookup, while the seeded ADMIN reaches handler lookup and receives 404 for the absent probe.
- 401 and 403 bodies use the `ApiResponse` envelope with stable code/message, explicit null data, nonblank trace ID, and timestamp.
- `NimbusJwtDecoder` uses the same configured secret and only HS256. Its default validators enforce expiration.
- Missing bearer credentials remain anonymous. Bad signature, expiry, malformed/nonnumeric subject, absent role, unknown user, disabled user, and stale/mismatched role claims all remain anonymous and receive the same protected-endpoint 401.
- The filter loads the current ENABLED user through `UserRepository.findById`, strictly checks claim role against the live role, builds `UserPrincipal(Long userId, String username, UserRole role)`, and grants authority from the live database role.
- Access-token claims remain only `sub`, `role`, `iat`, `exp`, and `jti`; no username claim or password material is returned.
- Login delegates to the real `AuthService`; missing users, disabled users, and bad passwords share the same generic 401 envelope.
- Refresh uses real Redis GETDEL rotation; replay is 401. Authenticated logout revokes the digest; subsequent refresh is 401. Blank request fields map to JSON 400.
- `/me` returns the password-free `UserSummary` and demonstrates that a username changed after issuance is reloaded from MySQL rather than invented from the token.
- `AuthControllerIT` uses no `@MockBean` and exercises Flyway, MyBatis, BCrypt cost 12, JWT, Spring Security, and Redis together. Docker absence cannot skip the test.
- No access token, refresh token, password, or password hash is logged by production code. Test-only credentials and secrets are explicitly named and not production values.

## Final Verification

- Focused: `./mvnw -Dit.test=AuthControllerIT verify` — `32` unit + `11` selected IT, all passed, `0` skipped.
- Unit: `./mvnw test` — `32` passed, `0` failures, `0` errors, `0` skipped, exit `0`.
- Full lifecycle: `./mvnw verify` — `32` unit + `19` IT = `51` passed, `0` failures, `0` errors, `0` skipped, exit `0`.
- Full Failsafe coverage: Redis refresh store `4`, UserMapper/wiring `2`, AuthController `11`, Flyway migration `2`.
- Docker Server `29.1.2` was available; all mandated MySQL and Redis containers started.
- `git diff --check` and staged diff checks are required again immediately before commit.

## Files

- `backend/src/main/java/com/brainos/auth/api/AuthController.java`
- `backend/src/main/java/com/brainos/auth/api/LoginRequest.java`
- `backend/src/main/java/com/brainos/auth/api/RefreshTokenRequest.java`
- `backend/src/main/java/com/brainos/auth/api/TokenResponse.java`
- `backend/src/main/java/com/brainos/auth/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/brainos/auth/security/SecurityConfig.java`
- `backend/src/main/java/com/brainos/auth/security/UserPrincipal.java`
- `backend/src/main/java/com/brainos/auth/token/AuthTokenConfiguration.java`
- `backend/src/main/java/com/brainos/common/api/GlobalExceptionHandler.java`
- `backend/src/test/java/com/brainos/auth/api/AuthControllerIT.java`
- `backend/src/test/java/com/brainos/auth/token/TokenServiceTest.java`
- `.superpowers/sdd/phase1-task-4-report.md`

## Non-Blocking Concerns

- Spring Boot's managed Flyway version warns that MySQL 8.4 is newer than its latest explicitly tested MySQL 8.1. All migrations and repeat validation pass against the required MySQL 8.4 image.
- The existing Java 21 test stack emits Mockito/Byte Buddy future dynamic-agent warnings.
- An earlier full run logged Hikari's explicit `thread starvation or clock leap` warning and reported an abnormal 1:42 h wall-clock duration. The fresh post-fix full lifecycle completed in 38.612 seconds with all `51` tests executed and no skips.

## Independent Review Follow-Up

### Auth exception boundary

1. Added `AuthExceptionHandlerTest` and a reflection assertion proving the common advice does not register handlers for auth exceptions.
2. RED: `./mvnw -Dtest=AuthExceptionHandlerTest,GlobalExceptionHandlerTest test` failed at test compilation because the auth-scoped advice did not yet exist. After correcting a test-only generic inference issue, the clean RED failure remained solely the missing `AuthExceptionHandler`.
3. Moved `AuthenticationFailedException` and `InvalidRefreshTokenException` handling out of `GlobalExceptionHandler` into `AuthExceptionHandler`, scoped to the auth controller package.
4. GREEN: the focused unit command executed `6` tests with `0` failures, `0` errors, and `0` skipped.
5. `backend/src/main/java/com/brainos/common` no longer imports or references `com.brainos.auth`.

### Malformed auth JSON

1. Added integration coverage for malformed JSON sent to login, refresh, and authenticated logout. Each assertion requires the complete `ApiResponse` validation envelope.
2. RED: `./mvnw -Dit.test=AuthControllerIT verify` executed `12` selected integration tests with `1` expected failure and `0` skipped: malformed login returned HTTP 400 with an empty body, so `$.code` was absent.
3. Added the minimal `HttpMessageNotReadableException` mapping in `GlobalExceptionHandler`, returning HTTP 400 and `VALIDATION_ERROR`.
4. GREEN: the focused verify executed `35` unit tests plus `12` selected integration tests, all passing with `0` skipped.

### Post-review verification

- Focused advice tests: `6` passed, `0` failures, `0` errors, `0` skipped.
- Focused auth lifecycle: `35` unit + `12` selected integration tests, all passed, `0` skipped.
- Unit: `./mvnw test` — `35` passed, `0` failures, `0` errors, `0` skipped, exit `0`.
- Full lifecycle: `./mvnw verify` — `35` unit + `20` integration = `55` passed, `0` failures, `0` errors, `0` skipped, exit `0`.
- Full Failsafe coverage: Redis refresh store `4`, UserMapper/wiring `2`, AuthController `12`, Flyway migration `2`.
- No frontend, specification, plan, or unrelated Task 5 files were changed.
