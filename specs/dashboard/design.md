---
module: dashboard
status: approved
depends_on: [auth, knowledge, document, rag]
deliverables: [api, vue-page, tests]
---

# Dashboard 模块设计

- `GET /api/v1/dashboard/summary` 返回 `knowledgeBaseCount`、`documentCount`、`chunkCount`、`questionCount`。
- `GET /api/v1/dashboard/trends?days=7` 返回连续日期与数量。
- `GET /api/v1/dashboard/recent-documents?limit=5` 返回轻量文档行。
- 后端使用 MyBatis-Plus 自定义聚合 SQL；不加载完整实体后在 Java 中统计。
- 前端四张统计卡、一张 ECharts 折线图和最近文档表格，具备加载、空和错误状态。
