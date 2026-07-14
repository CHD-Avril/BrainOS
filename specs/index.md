# BrainOS Spec 导航

| Spec | 阶段 | 状态 | 入口 | 说明 |
| --- | --- | --- | --- | --- |
| 项目总览 | overview | approved | [requirements](overview/requirements.md) / [design](overview/design.md) / [tasks](overview/tasks.md) / [status](overview/status.yaml) | 2026-07-14 由 Baron 批准 |

## 计划模块

overview 获得 `approved` 状态后，按依赖顺序创建以下 Spec 单元：

1. `foundation`：工程、数据库迁移、Docker Compose 和质量基线。
2. `auth`：认证、JWT、Redis 刷新令牌和 RBAC。
3. `dashboard`：统计、趋势和最近文档。
4. `knowledge`：知识库生命周期。
5. `document`：上传、解析、切片、千问 Embedding 和 Chroma。
6. `rag`：检索、千问/DeepSeek、SSE、引用和会话。
7. `admin`：用户管理与操作日志。

模块目录不得在 overview 批准前提前创建。
