---
module: document
status: approved
---

# Document 模块任务

| 编号 | 状态 | 依赖 | 关联验收 |
| --- | --- | --- | --- |
| DO-1 | [doing] | KB-1 | A-R4-01、A-R4-02 |
| DO-2 | [done] | DO-1 | A-R4-03 |
| DO-3 | [done] | DO-2 | A-R5-01、A-R5-02 |
| DO-4 | [doing] | DO-3 | A-R4-04、A-R5-03 |
| DO-5 | [todo] | DO-4 | A-R4-01 至 A-R5-03 |

- [doing] DO-1：文件校验、SHA-256 去重与本地存储已完成，待索引任务补齐完整状态机。
- [done] DO-2：测试先行实现四种文档解析、无文本失败和稳定切片元数据。
- [done] DO-3：已使用官方 Spring AI API 实现千问 Embedding 与 Chroma cosine 索引，并通过真实 Chroma 过滤、阈值和清理测试。
- [doing] DO-4：测试先行实现异步索引、失败补偿、重试和删除清理。
- [todo] DO-5：实现文档 API、上传/状态页面和组件测试。
