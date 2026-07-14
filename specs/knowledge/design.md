---
module: knowledge
status: approved
depends_on: [auth]
deliverables: [api, vue-page, tests]
---

# Knowledge 模块设计

- REST 资源为 `/api/v1/knowledge-bases`，使用 `KnowledgeBaseCreateRequest`、`KnowledgeBaseUpdateRequest`、`KnowledgeBaseView`。
- `name` 去除首尾空格后长度 2-60，`description` 最大 500。
- `KnowledgeBaseService` 提供 `create`、`list`、`get`、`update`、`delete`。
- `KnowledgeBaseCleanupPort` 由 document/rag 模块组合实现；删除用例先完成外部清理，再删除 MySQL 事实记录。
- 前端使用最多三列轻量卡片；创建/编辑使用 Element Plus Dialog；删除使用二次确认。
