---
module: auth
status: approved
depends_on: [foundation]
updated_at: 2026-07-14
---

# Auth 模块需求

## EARS 需求

- When 启用用户提交正确用户名和密码时，系统 shall 返回 2 小时访问令牌、7 天刷新令牌和用户摘要。
- When 密码错误或账号停用时，系统 shall 使用统一认证失败消息拒绝登录。
- When 有效刷新令牌被使用时，系统 shall 轮换刷新令牌并使旧令牌失效。
- When 用户退出时，系统 shall 删除 Redis 中对应刷新令牌摘要。
- When 普通用户访问管理员端点时，系统 shall 返回 HTTP 403。

## Acceptance 映射

A-R1-01、A-R1-02、A-R1-03、A-R1-04。
