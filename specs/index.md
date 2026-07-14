# BrainOS Spec 导航

| Spec | 阶段 | 状态 | 入口 | 说明 |
| --- | --- | --- | --- | --- |
| 项目总览 | overview | done | [requirements](overview/requirements.md) / [design](overview/design.md) / [tasks](overview/tasks.md) / [status](overview/status.yaml) | 2026-07-15 完成整仓验收 |

## 已交付模块

项目已按依赖顺序完成以下 Spec 单元：

1. `foundation`：工程、数据库迁移、Docker Compose 和质量基线。
2. `auth`：认证、JWT、Redis 刷新令牌和 RBAC。
3. `dashboard`：统计、趋势和最近文档。
4. `knowledge`：知识库生命周期。
5. `document`：上传、解析、切片、千问 Embedding 和 Chroma。
6. `rag`：检索、千问/DeepSeek、SSE、引用和会话。
7. `admin`：用户管理与操作日志。

## 模块审阅状态

| 模块 | 状态 | Spec |
| --- | --- | --- |
| foundation | done | [requirements](foundation/requirements.md) / [design](foundation/design.md) / [tasks](foundation/tasks.md) / [status](foundation/status.yaml) |
| auth | done | [requirements](auth/requirements.md) / [design](auth/design.md) / [tasks](auth/tasks.md) / [status](auth/status.yaml) |
| dashboard | done | [requirements](dashboard/requirements.md) / [design](dashboard/design.md) / [tasks](dashboard/tasks.md) / [status](dashboard/status.yaml) |
| knowledge | done | [requirements](knowledge/requirements.md) / [design](knowledge/design.md) / [tasks](knowledge/tasks.md) / [status](knowledge/status.yaml) |
| document | done | [requirements](document/requirements.md) / [design](document/design.md) / [tasks](document/tasks.md) / [status](document/status.yaml) |
| rag | done | [requirements](rag/requirements.md) / [design](rag/design.md) / [tasks](rag/tasks.md) / [status](rag/status.yaml) |
| admin | done | [requirements](admin/requirements.md) / [design](admin/design.md) / [tasks](admin/tasks.md) / [status](admin/status.yaml) |

模块目录于 2026-07-14 由 Baron 统一批准，并于 2026-07-15 完成实现、文档和自动化验收。
