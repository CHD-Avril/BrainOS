---
module: foundation
status: review
depends_on: [overview]
updated_at: 2026-07-14
---

# Foundation 模块需求

## 目标

建立可重复构建、测试和运行的前后端工程，使所有后续模块共享一致的配置、错误协议、数据库迁移和本地依赖。

## EARS 需求

- When 开发者执行后端测试命令时，系统 shall 使用 Java 21 与 Maven Wrapper 完成编译和测试。
- When 开发者执行前端测试命令时，系统 shall 使用 pnpm 锁文件完成类型检查、单测与构建。
- When 开发者启动 Docker Compose 时，系统 shall 提供健康的 MySQL 8、Redis 7 和 Chroma 服务及持久卷。
- When 后端启动时，系统 shall 使用 Flyway 创建表结构和默认管理员，并暴露健康检查与 Swagger UI。
- If 环境变量缺失或依赖不可用，系统 shall 在启动阶段给出明确配置错误，不使用真实密钥默认值。

## Acceptance 映射

- A-R10-01：README 启动链路和默认管理员。
- A-R10-02：统一自动化测试入口。
- A-R10-05：OpenAPI 与 Swagger UI。
