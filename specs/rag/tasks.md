---
module: rag
status: in-dev
---

# RAG 模块任务

| 编号 | 状态 | 依赖 | 关联验收 |
| --- | --- | --- | --- |
| RA-1 | [done] | DO-3 | A-R6-02、A-R6-03 |
| RA-2 | [done] | RA-1 | A-R6-01、A-R6-04 |
| RA-3 | [done] | AU-2 | A-R7-01 至 A-R7-03 |
| RA-4 | [doing] | RA-2、RA-3 | A-R6-01 至 A-R7-03 |

- [done] RA-1：测试先行实现知识库过滤检索、阈值、Top 5、引用候选和可靠性兜底。
- [done] RA-2：测试先行实现模型路由、提示词与 SSE 协议。
- [done] RA-3：测试先行实现会话所有权、消息持久化、标题和删除。
- [doing] RA-4：已完成问答 API 与 SSE 后端，正在实现聊天页面、SSE 客户端和组件/集成测试。
