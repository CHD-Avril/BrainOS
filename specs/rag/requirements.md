---
module: rag
status: review
depends_on: [auth, knowledge, document]
updated_at: 2026-07-14
---

# RAG 模块需求

- When 用户创建会话时，系统 shall 将会话绑定到知识库、用户和千问或 DeepSeek 聊天模型。
- When 用户提问时，系统 shall 使用千问 Embedding 并只检索所选知识库中 Top 5 可靠片段。
- If 没有片段达到阈值，系统 shall 返回固定的无可靠依据提示而不调用聊天模型。
- When 有可靠片段时，系统 shall 通过 SSE 流式返回回答，并在完成后返回实际引用。
- If 模型调用失败，系统 shall 保留用户问题、返回可重试错误且不保存空 AI 消息。
- When 用户查询会话时，系统 shall 只返回本人会话、消息和引用。
- When 用户删除会话时，系统 shall 级联删除消息和引用。

Acceptance：A-R6-01 至 A-R6-04、A-R7-01 至 A-R7-03。
