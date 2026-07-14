# BrainOS Phase 4 Admin, Dashboard, and Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the light enterprise feature set with user administration, safe audit logs, a real-data dashboard, API documentation, desktop visual QA, and a reproducible defense demo.

**Architecture:** Add admin-only feature slices on the existing security boundary and aggregate dashboard metrics with SQL. Finish with full-stack E2E, accessibility, visual, deployment and documentation checks rather than adding new product scope.

**Tech Stack:** Spring Boot, Spring Security, MyBatis-Plus, springdoc-openapi/Swagger UI, Vue 3, Element Plus, ECharts, Vitest, Playwright, Docker Compose.

## Global Constraints

- Admin functions are user management and audit-log viewing only.
- Roles remain `ADMIN` and `USER`; at least one enabled administrator must remain.
- Audit summaries use an explicit field whitelist and never contain passwords, tokens, Authorization headers or provider keys.
- Dashboard contains exactly four metrics, one seven-day line chart and one recent-documents table.
- UI is PC-only and checked at 1024x768 and 1440x900.
- No extra charts, dark mode, exports, alerts, billing or decorative dashboard widgets.

---

### Task 1: User Administration Rules and API

**Files:**
- Create: `backend/src/main/java/com/brainos/admin/user/UserAdminService.java`
- Create: `backend/src/main/java/com/brainos/admin/user/UserAdminController.java`
- Create: `backend/src/main/java/com/brainos/admin/user/CreateUserRequest.java`
- Create: `backend/src/main/java/com/brainos/admin/user/UpdateUserRequest.java`
- Create: `backend/src/test/java/com/brainos/admin/UserAdminServiceTest.java`

**Interfaces:**
- Produces: paged list, create, edit and status change under `/api/v1/admin/users`.

- [ ] **Step 1: Write failing admin-rule tests**

```java
@Test
void cannotDisableLastEnabledAdmin() {
    users.save(enabledAdmin(1L, "admin"));
    assertThrows(LastAdministratorException.class,
        () -> service.changeStatus(1L, UserStatus.DISABLED, 1L));
}

@Test
void createdUserReceivesEncodedPassword() {
    UserView created = service.create(new CreateUserCommand("alice", "Alice", "Secret@123", UserRole.USER), 1L);
    assertThat(users.getRequired(created.id()).passwordHash()).startsWith("$2");
    assertThat(users.getRequired(created.id()).passwordHash()).doesNotContain("Secret@123");
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=UserAdminServiceTest test`

Expected: admin user types are missing.

- [ ] **Step 3: Implement service and controller**

Normalize unique usernames, whitelist roles, encode new/reset passwords, count enabled admins inside the status transaction, revoke all refresh tokens when disabling a user, and expose no password fields in responses.

```java
@Transactional
public UserView changeStatus(long targetId, UserStatus status, long actorId) {
    UserAccount target = users.getRequired(targetId);
    if (target.role() == UserRole.ADMIN && status == UserStatus.DISABLED && users.countEnabledAdmins() == 1) {
        throw new LastAdministratorException();
    }
    users.updateStatus(targetId, status);
    if (status == UserStatus.DISABLED) refreshTokens.revokeAll(targetId);
    audit.record(AuditEvent.userStatus(actorId, targetId, status));
    return mapper.toView(users.getRequired(targetId));
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=UserAdminServiceTest,UserAdminControllerIT test`

Expected: create/edit/disable/last-admin/403 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/admin backend/src/test/java/com/brainos/admin
git commit -m "feat: administer enterprise users"
```

### Task 2: Safe Audit Events and Query API

**Files:**
- Create: `backend/src/main/java/com/brainos/common/audit/AuditAction.java`
- Create: `backend/src/main/java/com/brainos/common/audit/AuditEvent.java`
- Create: `backend/src/main/java/com/brainos/common/audit/AuditService.java`
- Create: `backend/src/main/java/com/brainos/admin/audit/AuditLogController.java`
- Create: `backend/src/test/java/com/brainos/common/audit/AuditServiceTest.java`

**Interfaces:**
- Produces: `void record(AuditEvent event)` and paged admin query with user/action/time filters.

- [ ] **Step 1: Write failing redaction test**

```java
@Test
void auditSummaryCannotContainCredentialFields() {
    AuditEvent event = AuditEvent.builder(AuditAction.LOGIN_FAILED)
        .actorId(9L).target("USER", "alice")
        .detail("username", "alice")
        .detail("password", "Secret@123")
        .detail("authorization", "Bearer token")
        .build();
    service.record(event);
    AuditLog saved = repository.last();
    assertThat(saved.summary()).contains("alice")
        .doesNotContain("Secret@123").doesNotContain("Bearer token")
        .doesNotContain("password").doesNotContain("authorization");
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=AuditServiceTest test`

Expected: audit service types are missing.

- [ ] **Step 3: Implement whitelisted audit recording**

Accept details only for action-specific allowed keys; publish after-commit events from auth, knowledge, document and admin services. Add indexed filters on `user_id`, `action`, `created_at`. Do not store request bodies.

```java
public void record(AuditEvent event) {
    Set<String> allowed = allowedFields.getOrDefault(event.action(), Set.of());
    Map<String, Object> safe = event.details().entrySet().stream()
        .filter(entry -> allowed.contains(entry.getKey()))
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    repository.insert(AuditLog.from(event, json.write(safe)));
}
```

- [ ] **Step 4: Run GREEN and regression**

Run: `cd backend && ./mvnw -Dtest=AuditServiceTest,AuditLogControllerIT test && ./mvnw test`

Expected: writing, redaction, filters, pagination and admin authorization pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/common/audit backend/src/main/java/com/brainos/admin/audit backend/src/test
git commit -m "feat: record safe operation audits"
```

### Task 3: Real-Data Dashboard API

**Files:**
- Create: `backend/src/main/java/com/brainos/dashboard/DashboardMapper.java`
- Create: `backend/src/main/java/com/brainos/dashboard/DashboardService.java`
- Create: `backend/src/main/java/com/brainos/dashboard/DashboardController.java`
- Create: `backend/src/test/java/com/brainos/dashboard/DashboardServiceTest.java`

**Interfaces:**
- Produces: summary, seven-day trend and recent documents endpoints.

- [ ] **Step 1: Write failing zero-fill test**

```java
@Test
void sevenDayTrendIncludesDatesWithNoQuestions() {
    LocalDate today = LocalDate.of(2026, 7, 14);
    mapper.returnsCounts(Map.of(today.minusDays(1), 3L));
    List<DailyCount> trend = service.trend(today, 7);
    assertThat(trend).hasSize(7);
    assertThat(trend.get(5)).isEqualTo(new DailyCount(today.minusDays(1), 3L));
    assertThat(trend.stream().mapToLong(DailyCount::count).sum()).isEqualTo(3L);
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=DashboardServiceTest test`

Expected: dashboard types are missing.

- [ ] **Step 3: Implement SQL aggregations and date filling**

Count knowledge bases, documents, sum `chunk_count` for READY documents, count user-role messages as questions, aggregate the last seven dates in SQL, and fill missing dates in Java using an injected `Clock`. Query only the five most recently updated documents.

```java
public List<DailyCount> trend(LocalDate today, int days) {
    Map<LocalDate, Long> counts = mapper.countQuestionsByDate(today.minusDays(days - 1L), today).stream()
        .collect(Collectors.toMap(DailyCount::date, DailyCount::count));
    return IntStream.range(0, days)
        .mapToObj(offset -> today.minusDays(days - 1L - offset))
        .map(date -> new DailyCount(date, counts.getOrDefault(date, 0L)))
        .toList();
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=DashboardServiceTest,DashboardControllerIT test`

Expected: real counts, zero state, date boundaries and recent documents pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/dashboard backend/src/test/java/com/brainos/dashboard
git commit -m "feat: expose real knowledge dashboard metrics"
```

### Task 4: Admin and Dashboard Vue Pages

**Files:**
- Create: `frontend/src/features/admin/users/UserListView.vue`, `api.ts`, `UserListView.spec.ts`
- Create: `frontend/src/features/admin/audit/AuditLogView.vue`, `api.ts`, `AuditLogView.spec.ts`
- Create: `frontend/src/features/dashboard/DashboardView.vue`, `api.ts`, `DashboardView.spec.ts`
- Create: `frontend/src/features/dashboard/QuestionTrendChart.vue`

**Interfaces:**
- Produces: admin-only routes and the default `/dashboard` route.

- [ ] **Step 1: Write failing dashboard rendering test**

```ts
it('renders four real metrics and the recent document empty state', async () => {
  api.summary.mockResolvedValue({ knowledgeBaseCount: 2, documentCount: 4, chunkCount: 31, questionCount: 8 })
  api.trends.mockResolvedValue(sevenDaysOfZero())
  api.recentDocuments.mockResolvedValue([])
  const wrapper = mountDashboard()
  await flushPromises()
  expect(wrapper.findAll('[data-test="metric-card"]')).toHaveLength(4)
  expect(wrapper.text()).toContain('31')
  expect(wrapper.text()).toContain('暂无最近文档')
})
```

- [ ] **Step 2: Run RED**

Run: `cd frontend && pnpm vitest run src/features/dashboard/DashboardView.spec.ts`

Expected: dashboard components are missing.

- [ ] **Step 3: Implement approved layouts**

Render exactly four metric cards, one blue ECharts line and one recent-documents table. Implement user create/edit/status dialogs and audit one-line filters. Keep tables as tables, add loading/empty/error/pagination, and hide admin navigation for USER.

```vue
<section class="dashboard-metrics" aria-label="知识库统计">
  <MetricCard v-for="metric in metrics" :key="metric.key" :data-test="`metric-${metric.key}`"
    :label="metric.label" :value="metric.value" />
</section>
<section class="dashboard-detail">
  <QuestionTrendChart :points="trends" />
  <RecentDocumentTable :rows="recentDocuments" :loading="loading" />
</section>
```

- [ ] **Step 4: Run GREEN and frontend regression**

Run: `cd frontend && pnpm vitest run && pnpm vue-tsc --noEmit && pnpm build`

Expected: dashboard/admin tests pass and no unapproved chart or route is present.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/admin frontend/src/features/dashboard frontend/src/router frontend/src/layouts
git commit -m "feat: add dashboard and admin workspaces"
```

### Task 5: OpenAPI, README, and Defense Fixtures

**Files:**
- Modify: `backend/src/main/java/com/brainos/common/config/OpenApiConfig.java`
- Modify: `README.md`
- Create: `docs/DEPLOYMENT.md`
- Create: `docs/DEFENSE-DEMO.md`
- Create: `docs/TEST-REPORT.md`
- Create: `fixtures/employee-handbook.md`

**Interfaces:**
- Produces: Swagger UI without credential values and a deterministic demo script.

- [ ] **Step 1: Write the failing documentation smoke test**

```java
@Test
void openApiContainsAuthAndDocumentEndpointsWithoutSecrets() throws Exception {
    String json = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
    assertThat(json).contains("/api/v1/auth/login")
        .contains("/api/v1/knowledge-bases/{id}/documents")
        .doesNotContain("QWEN_API_KEY").doesNotContain("DEEPSEEK_API_KEY");
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=OpenApiSmokeIT test`

Expected: missing operation documentation or unintended configuration schema fails an assertion.

- [ ] **Step 3: Complete docs and API descriptions**

README must contain prerequisites, environment variables, Docker startup, backend/frontend commands, default development admin, supported files, model-key behavior, verification commands and troubleshooting. `DEFENSE-DEMO.md` uses the exact main flow and includes the expected visible result at each click.

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=OpenApiSmokeIT test`

Expected: OpenAPI contains all public app APIs and no secrets.

- [ ] **Step 5: Commit**

```bash
git add README.md docs fixtures backend/src/main/java/com/brainos/common/config backend/src/test
git commit -m "docs: add API and defense runbook"
```

### Task 6: Full E2E, Accessibility, and Desktop Visual QA

**Files:**
- Create: `frontend/e2e/full-defense-flow.spec.ts`
- Create: `frontend/e2e/admin-access.spec.ts`
- Create: `frontend/e2e/visual-desktop.spec.ts`
- Modify: `scripts/verify.sh`

**Interfaces:**
- Produces: final automated evidence for A-R1 through A-R10.

- [ ] **Step 1: Write the complete failing defense-flow test**

```ts
test('completes the defense workflow', async ({ page }) => {
  await loginAsAdmin(page)
  await createKnowledgeBase(page, '员工制度')
  await uploadAndWaitReady(page, 'fixtures/employee-handbook.md')
  await askAndExpectCitation(page, '年假有几天？', 'employee-handbook.md')
  await page.getByRole('link', { name: '工作台' }).click()
  await expect(page.getByTestId('metric-document-count')).not.toHaveText('0')
  await page.getByRole('link', { name: '操作日志' }).click()
  await expect(page.getByText('DOCUMENT_UPLOAD')).toBeVisible()
})
```

- [ ] **Step 2: Run RED and repair only observable gaps**

Run: `cd frontend && pnpm playwright test e2e/full-defense-flow.spec.ts e2e/admin-access.spec.ts`

Expected before repair: at least one end-to-end expectation fails; record the cause in the task notes, add a regression test, then fix.

- [ ] **Step 3: Verify accessibility and desktop views**

At 1024x768 and 1440x900, capture login, dashboard, document and chat screenshots. Check keyboard order, visible focus, contrast, no whole-page horizontal scroll, table readability, status labels, citation disclosure and no mobile UI. Compare against `design-system/MASTER.md` and record the fidelity ledger in `docs/TEST-REPORT.md`.

- [ ] **Step 4: Run the final verification suite**

Run: `bash scripts/verify.sh && cd frontend && pnpm playwright test`

Expected: backend tests, frontend tests, typecheck, build and all Playwright projects pass with no console errors.

- [ ] **Step 5: Verify a clean production-like startup**

Run: `docker compose down -v && docker compose up -d && cd backend && ./mvnw spring-boot:run`

In a second terminal run: `cd frontend && pnpm dev --host 127.0.0.1`.

Expected: a clean database migrates, default admin logs in, the fixture indexes, RAG cites it, Swagger UI opens, and the defense flow succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/e2e scripts docs/TEST-REPORT.md
git commit -m "test: verify complete BrainOS defense flow"
```

### Task 7: Final Spec Status and Delivery Commit

**Files:**
- Modify: `specs/*/tasks.md`
- Modify: `specs/*/status.yaml`
- Modify: `specs/index.md`

- [ ] **Step 1: Reconcile every Acceptance**

For each `A-Rx-xx`, record its automated test or exact manual desktop check in `docs/TEST-REPORT.md`. A module may become `done` only when its mapped tests pass.

- [ ] **Step 2: Run consistency checks**

Run: `rg -n '\[(todo|doing|blocked)\]' specs/*/tasks.md`

Expected: no unfinished implementation task remains.

Run: `rg -n '^status: (draft|review|approved|in-dev)$' specs/*/status.yaml`

Expected: no module remains outside `done`.

- [ ] **Step 3: Commit final status**

```bash
git add specs docs/TEST-REPORT.md
git commit -m "docs: mark BrainOS implementation complete"
```
