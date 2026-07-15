---
module: auth
status: approved
depends_on: [foundation]
deliverables: [api, security, tests]
---

# Auth 模块设计

- `POST /api/v1/auth/login`：`LoginRequest -> TokenResponse`。
- `POST /api/v1/auth/refresh`：轮换刷新令牌。
- `POST /api/v1/auth/logout`：撤销刷新令牌。
- `GET /api/v1/auth/me`：返回当前用户。
- JWT claims 只包含 `sub`、`role`、`iat`、`exp`、`jti`。
- Redis key 使用 `auth:refresh:{sha256(token)}`，值为用户 ID，TTL 7 天。
- `UserPrincipal` 提供 `Long userId()`、`String username()`、`UserRole role()`。
- 密码使用 BCrypt；认证响应和日志不返回 `password_hash`。
- Spring Security 使用无状态过滤链、`JwtAuthenticationFilter`、统一 401/403 JSON 响应。
