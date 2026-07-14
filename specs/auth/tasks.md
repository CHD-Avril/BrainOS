---
module: auth
status: approved
---

# Auth 模块任务

| 编号 | 状态 | 依赖 | 关联验收 |
| --- | --- | --- | --- |
| AU-1 | [todo] | F-3 | A-R1-01、A-R1-02 |
| AU-2 | [todo] | AU-1 | A-R1-03 |
| AU-3 | [todo] | AU-1 | A-R1-04 |
| AU-4 | [todo] | AU-2、AU-3 | A-R1-01 至 A-R1-04 |

- [todo] AU-1：测试先行实现用户实体、密码校验、登录与 JWT 签发。
- [todo] AU-2：测试先行实现 JWT 过滤链、当前用户和管理员授权。
- [todo] AU-3：测试先行实现 Redis 刷新令牌轮换与退出撤销。
- [todo] AU-4：完成认证接口集成测试与审计事件。
