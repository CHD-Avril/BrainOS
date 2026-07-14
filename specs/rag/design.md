---
module: rag
status: review
depends_on: [auth, knowledge, document]
deliverables: [api, retrieval, model-routing, sse, vue-page, tests]
---

# RAG 模块设计

- `ChatModelType`：`QWEN`、`DEEPSEEK`；Embedding 固定为 Qwen。
- `RagRetriever.retrieve(Long knowledgeBaseId, String question)` 返回最多 5 个 `CitationCandidate`。
- `RagPromptFactory` 生成中文系统约束、编号上下文和用户问题。
- `ChatModelRouter.stream(ChatModelType, Prompt)` 返回 `Flux<String>`。
- SSE 事件固定为 `start`、`delta`、`citations`、`done`、`error`。
- `ChatSessionService` 校验所有权，并保存会话、用户消息、完整 AI 消息及 `citations_json`。
- 前端会话侧栏宽 260px；顶部选择知识库和模型；回答支持 Markdown 与折叠引用。
