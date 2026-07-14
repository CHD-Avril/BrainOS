---
module: admin
status: approved
depends_on: [auth]
deliverables: [user-api, audit-api, vue-pages, tests]
---

# Admin 模块设计

- 用户端点：`GET/POST /api/v1/admin/users`、`PUT /users/{id}`、`PATCH /users/{id}/status`。
- 日志端点：`GET /api/v1/admin/audit-logs`，支持 `userId`、`action`、`from`、`to`、`page`、`size`。
- `UserAdminService` 执行用户名唯一、角色白名单和最后管理员保护。
- `AuditService.record(AuditEvent)` 通过事务提交后事件写入，摘要字段明确白名单。
- 用户页和日志页使用 Element Plus 表格、单行筛选、加载/空/错误/分页状态。
