# BrainOS Phase 1 Foundation and Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a reproducible local stack and a tested PC login flow with JWT access tokens, Redis refresh-token rotation, RBAC, and a minimal Vue application shell.

**Architecture:** Create a Spring Boot modular monolith under `backend/` and a Vue SPA under `frontend/`. Establish the shared response/error contract and infrastructure first, then implement authentication as a feature slice from database through API and UI.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring AI 1.1.8 BOM, Spring MVC, Spring Security, MyBatis-Plus, Flyway, MySQL 8.x, Redis 7.x, Vue 3.4+, Vite, Pinia, Vue Router, Element Plus, JUnit 5, Testcontainers, Vitest.

## Global Constraints

- PC Web only; verify 1024px and 1440px. Do not create mobile navigation or mobile layouts.
- API base path is `/api/v1`; ordinary JSON responses contain `code`, `message`, `data`, `traceId`, `timestamp`.
- Access tokens expire after 2 hours; rotating refresh tokens expire after 7 days and are stored as SHA-256 hashes in Redis.
- Only `ADMIN` and `USER` roles exist. Public registration is prohibited.
- Real secrets never enter Git; `.env.example` contains names and explanations only.
- Every production behavior follows RED -> GREEN -> REFACTOR; run the named failing test before implementation.

---

### Task 1: Backend Build and Common Contract

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`
- Create: `backend/src/main/java/com/brainos/BrainOsApplication.java`
- Create: `backend/src/main/java/com/brainos/common/api/ApiResponse.java`
- Create: `backend/src/main/java/com/brainos/common/api/ErrorCode.java`
- Create: `backend/src/main/java/com/brainos/common/api/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/brainos/common/api/ApiResponseTest.java`

**Interfaces:**
- Produces: `ApiResponse<T>.success(T data)` and `ApiResponse<T>.failure(ErrorCode code, String traceId)`.
- Produces: Maven profiles and dependency management consumed by every later backend task.

- [ ] **Step 1: Write the failing common-contract test**

```java
package com.brainos.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void successContainsStableEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ready");
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.traceId()).isNotBlank();
        assertThat(response.timestamp()).isNotNull();
    }
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=ApiResponseTest test`

Expected: compilation fails because `ApiResponse` does not exist.

- [ ] **Step 3: Implement the minimal envelope**

```java
package com.brainos.common.api;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(String code, String message, T data, String traceId, Instant timestamp) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, UUID.randomUUID().toString(), Instant.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode error, String traceId) {
        return new ApiResponse<>(error.code(), error.message(), null, traceId, Instant.now());
    }
}
```

Create `ErrorCode` with `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`; map validation and domain exceptions in `GlobalExceptionHandler`. Add Spring Boot, Security, Validation, MyBatis-Plus, Redis, Flyway, MySQL, Lombok, MapStruct, springdoc-openapi and test dependencies to `pom.xml`.

- [ ] **Step 4: Run GREEN and full backend test**

Run: `cd backend && ./mvnw -Dtest=ApiResponseTest test && ./mvnw test`

Expected: `ApiResponseTest` and the Spring context smoke test pass with no warnings from duplicate logging providers.

- [ ] **Step 5: Commit**

```bash
git add backend
git commit -m "build: scaffold Spring Boot backend"
```

### Task 2: Docker Services, Flyway Schema, and Default Admin

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__create_core_tables.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed_admin.sql`
- Create: `backend/src/test/java/com/brainos/foundation/MigrationIT.java`

**Interfaces:**
- Produces: MySQL tables `sys_user`, `knowledge_base`, `kb_document`, `chat_session`, `chat_message`, `audit_log`.
- Produces: healthy `mysql`, `redis`, `chroma` Compose services.

- [ ] **Step 1: Write the failing migration integration test**

```java
package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
class MigrationIT {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreatesEnabledAdmin() {
        Integer count = jdbc.queryForObject(
            "select count(*) from sys_user where username='admin' and role='ADMIN' and status='ENABLED'",
            Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run RED**

Run: `docker compose up -d mysql redis chroma && cd backend && ./mvnw -Dtest=MigrationIT test`

Expected: test fails because the migration files and `sys_user` table do not exist.

- [ ] **Step 3: Implement infrastructure**

Use images `mysql:8.4`, `redis:7.4-alpine`, and `ghcr.io/chroma-core/chroma:1.0.0`; add health checks and volumes. Create all six tables with foreign keys and indexes from `specs/overview/design.md`. Seed `admin` with a BCrypt hash generated once for the documented development password and require `BRAINOS_ADMIN_PASSWORD` override outside the `dev` profile.

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: brainos
      MYSQL_USER: brainos
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-brainos_dev}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root_dev}
    ports: ["3306:3306"]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD:-root_dev}"]
      interval: 5s
      timeout: 3s
      retries: 20
  redis:
    image: redis:7.4-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20
  chroma:
    image: ghcr.io/chroma-core/chroma:1.0.0
    ports: ["8000:8000"]
```

- [ ] **Step 4: Run GREEN**

Run: `docker compose ps && cd backend && ./mvnw -Dtest=MigrationIT test`

Expected: three services are healthy and `MigrationIT` passes.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .env.example backend/src/main/resources backend/src/test/java/com/brainos/foundation
git commit -m "build: add local data services and migrations"
```

### Task 3: Authentication Domain, Login, and Token Rotation

**Files:**
- Create: `backend/src/main/java/com/brainos/auth/domain/UserRole.java`
- Create: `backend/src/main/java/com/brainos/auth/domain/UserStatus.java`
- Create: `backend/src/main/java/com/brainos/auth/domain/UserAccount.java`
- Create: `backend/src/main/java/com/brainos/auth/persistence/UserMapper.java`
- Create: `backend/src/main/java/com/brainos/auth/token/TokenService.java`
- Create: `backend/src/main/java/com/brainos/auth/token/RedisRefreshTokenStore.java`
- Create: `backend/src/main/java/com/brainos/auth/application/AuthService.java`
- Create: `backend/src/test/java/com/brainos/auth/application/AuthServiceTest.java`

**Interfaces:**
- Produces: `TokenPair login(String username, String password)`.
- Produces: `TokenPair refresh(String refreshToken)` and `void logout(String refreshToken)`.
- Produces: `UserAccountMapper.findByUsername(String username)`.

- [ ] **Step 1: Write the failing login test**

```java
package com.brainos.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
    @Test
    void enabledUserReceivesTwoTokens() {
        FakeUsers users = FakeUsers.withEnabledUser("baron", "secret");
        InMemoryRefreshTokens refresh = new InMemoryRefreshTokens();
        AuthService service = AuthFixtures.service(users, refresh);

        TokenPair pair = service.login("baron", "secret");

        assertThat(pair.accessToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(refresh.contains(pair.refreshToken())).isTrue();
    }

    @Test
    void disabledUserGetsGenericFailure() {
        AuthService service = AuthFixtures.service(FakeUsers.withDisabledUser("baron", "secret"), new InMemoryRefreshTokens());
        AuthenticationFailedException error = assertThrows(
            AuthenticationFailedException.class, () -> service.login("baron", "secret"));
        assertThat(error.getMessage()).isEqualTo("用户名或密码错误");
    }
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=AuthServiceTest test`

Expected: compilation fails because the auth application types do not exist.

- [ ] **Step 3: Implement login, refresh, and logout**

Use `PasswordEncoder.matches`, `JwtEncoder`, and a `RefreshTokenStore` interface. Generate 32 random bytes for refresh tokens, store only `SHA-256(token)` at `auth:refresh:{hash}` with a 7-day TTL, delete the old key before returning a rotated pair, and delete it on logout.

```java
public TokenPair login(String username, String password) {
    UserAccount user = users.findByUsername(username)
        .filter(UserAccount::isEnabled)
        .filter(found -> passwordEncoder.matches(password, found.passwordHash()))
        .orElseThrow(AuthenticationFailedException::new);
    String access = tokenService.createAccessToken(user.id(), user.username(), user.role(), Duration.ofHours(2));
    String refresh = refreshTokens.issue(user.id(), Duration.ofDays(7));
    return new TokenPair(access, refresh, user.toSummary());
}

public TokenPair refresh(String oldToken) {
    long userId = refreshTokens.consume(oldToken);
    UserAccount user = users.getEnabled(userId);
    return new TokenPair(
        tokenService.createAccessToken(user.id(), user.username(), user.role(), Duration.ofHours(2)),
        refreshTokens.issue(user.id(), Duration.ofDays(7)),
        user.toSummary());
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=AuthServiceTest,RedisRefreshTokenStoreIT test`

Expected: login, generic failure, rotation, replay rejection, and logout tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/auth backend/src/test/java/com/brainos/auth
git commit -m "feat: add secure login and token rotation"
```

### Task 4: Security Filter Chain and Auth API

**Files:**
- Create: `backend/src/main/java/com/brainos/auth/security/UserPrincipal.java`
- Create: `backend/src/main/java/com/brainos/auth/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/brainos/auth/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/brainos/auth/api/AuthController.java`
- Create: `backend/src/main/java/com/brainos/auth/api/LoginRequest.java`
- Create: `backend/src/main/java/com/brainos/auth/api/TokenResponse.java`
- Create: `backend/src/test/java/com/brainos/auth/api/AuthControllerIT.java`

**Interfaces:**
- Produces: `/api/v1/auth/login`, `/refresh`, `/logout`, `/me`.
- Produces: `@AuthenticationPrincipal UserPrincipal` for all later controllers.

- [ ] **Step 1: Write failing API authorization tests**

```java
@Test
void anonymousCannotReadCurrentUser() throws Exception {
    mvc.perform(get("/api/v1/auth/me"))
       .andExpect(status().isUnauthorized())
       .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
}

@Test
void ordinaryUserCannotReachAdminProbe() throws Exception {
    mvc.perform(get("/api/v1/admin/probe").header("Authorization", userBearer()))
       .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=AuthControllerIT test`

Expected: endpoints are missing or return the default Spring Security body.

- [ ] **Step 3: Implement the filter chain and controller**

Permit `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`; require `ADMIN` for `/api/v1/admin/**`; authenticate everything else. Add JSON `AuthenticationEntryPoint` and `AccessDeniedHandler` using `ApiResponse.failure`.

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(ex -> ex.authenticationEntryPoint(json401).accessDeniedHandler(json403))
        .build();
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=AuthControllerIT test && ./mvnw test`

Expected: login, current-user, 401, 403, refresh rotation and logout tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/auth backend/src/test/java/com/brainos/auth
git commit -m "feat: expose authenticated API with RBAC"
```

### Task 5: Vue Foundation, Login, and Protected Shell

**Files:**
- Create: `frontend/package.json`, `frontend/pnpm-lock.yaml`, `frontend/vite.config.ts`, `frontend/tsconfig.json`
- Create: `frontend/src/main.ts`, `frontend/src/App.vue`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/features/auth/api.ts`
- Create: `frontend/src/features/auth/store.ts`
- Create: `frontend/src/features/auth/LoginView.vue`
- Create: `frontend/src/layouts/AppShell.vue`
- Create: `frontend/src/styles/tokens.css`, `frontend/src/styles/global.css`
- Create: `frontend/src/features/auth/LoginView.spec.ts`

**Interfaces:**
- Produces: `useAuthStore().login`, `refresh`, `logout`, `isAdmin`.
- Produces: route guard based on `meta.requiresAuth` and `meta.adminOnly`.

- [ ] **Step 1: Write the failing login component test**

```ts
it('submits credentials and routes to the dashboard', async () => {
  const login = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(LoginView, { global: authTestPlugins({ login }) })
  await wrapper.get('[aria-label="用户名"]').setValue('admin')
  await wrapper.get('[aria-label="密码"]').setValue('BrainOS@123')
  await wrapper.get('button[type="submit"]').trigger('submit')
  expect(login).toHaveBeenCalledWith('admin', 'BrainOS@123')
  expect(testRouter.currentRoute.value.name).toBe('dashboard')
})
```

- [ ] **Step 2: Run RED**

Run: `cd frontend && pnpm vitest run src/features/auth/LoginView.spec.ts`

Expected: test fails because `LoginView` and auth store do not exist.

- [ ] **Step 3: Implement the minimal PC login and shell**

Use Element Plus form controls with visible labels, field errors, submit loading state and generic API error. Apply tokens from `design-system/MASTER.md`; implement a 224px sidebar, 64px top bar and desktop content area. Do not create mobile drawer behavior.

```vue
<el-form :model="form" label-position="top" @submit.prevent="submit">
  <el-form-item label="用户名" :error="errors.username">
    <el-input v-model.trim="form.username" aria-label="用户名" autocomplete="username" />
  </el-form-item>
  <el-form-item label="密码" :error="errors.password">
    <el-input v-model="form.password" aria-label="密码" type="password" autocomplete="current-password" show-password />
  </el-form-item>
  <el-button native-type="submit" type="primary" :loading="submitting">登录</el-button>
</el-form>
```

- [ ] **Step 4: Run GREEN and frontend quality checks**

Run: `cd frontend && pnpm vitest run && pnpm vue-tsc --noEmit && pnpm build`

Expected: login tests pass, TypeScript has no errors, and Vite produces `dist/`.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add Vue login and protected app shell"
```

### Task 6: Phase 1 Verification

**Files:**
- Create: `scripts/verify.sh`
- Create: `backend/src/test/java/com/brainos/auth/AuthFlowIT.java`
- Create: `frontend/e2e/auth.spec.ts`

**Interfaces:**
- Produces: one command that verifies backend, frontend and login E2E.

- [ ] **Step 1: Add the failing E2E expectation**

```ts
test('admin logs in and ordinary user is denied admin navigation', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('BrainOS@123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/dashboard/)
  await expect(page.getByRole('navigation')).toBeVisible()
})
```

- [ ] **Step 2: Run RED, then wire Playwright and test data**

Run: `cd frontend && pnpm playwright test e2e/auth.spec.ts`

Expected before wiring: Playwright cannot complete login. Configure web servers and a test profile; do not weaken production security.

- [ ] **Step 3: Run the complete phase verification**

Run: `bash scripts/verify.sh`

Expected: Docker health, backend tests, frontend tests/typecheck/build, and auth E2E all pass.

- [ ] **Step 4: Commit**

```bash
git add scripts backend/src/test frontend/e2e frontend/playwright.config.ts
git commit -m "test: verify foundation and login flow"
```
