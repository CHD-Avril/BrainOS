---
module: dashboard
status: review
depends_on: [auth, knowledge, document, rag]
updated_at: 2026-07-14
---

# Dashboard 模块需求

- When 用户打开工作台时，系统 shall 返回知识库数、文档数、READY 切片总数和问答次数。
- When 用户查看趋势时，系统 shall 返回最近 7 个自然日的问答数量，包括值为零的日期。
- When 用户查看最近文档时，系统 shall 返回最近更新的 5 个文档及所属知识库、状态和时间。
- If 尚无业务数据，系统 shall 返回合法零值和空列表，不返回错误。

Acceptance：A-R2-01、A-R2-02、A-R2-03。
