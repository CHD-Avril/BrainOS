---
module: admin
status: review
depends_on: [auth]
updated_at: 2026-07-14
---

# Admin 模块需求

- When 管理员创建合法且唯一用户名时，系统 shall 创建可登录的普通用户。
- When 管理员编辑或启停用户时，系统 shall 立即应用状态；停用用户不能登录或刷新。
- If 操作会停用最后一个启用管理员，系统 shall 拒绝操作。
- When 登录、知识库变更、文档上传/重试/删除或用户管理完成时，系统 shall 写入不含凭据的审计日志。
- When 管理员按用户、操作类型、时间筛选日志时，系统 shall 返回匹配分页结果。
- When 普通用户访问用户或日志接口时，系统 shall 返回 HTTP 403。

Acceptance：A-R8-01 至 A-R8-03、A-R9-01 至 A-R9-03。
