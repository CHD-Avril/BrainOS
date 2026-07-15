---
module: document
status: approved
depends_on: [foundation, auth, knowledge]
updated_at: 2026-07-14
---

# Document 模块需求

- When 用户上传 PDF、DOCX、TXT 或 Markdown 且不超过 20MB 时，系统 shall 保存文件并创建 `PARSING` 文档记录。
- If 扩展名/MIME 不支持、文件超限、文件名不安全或同知识库 SHA-256 重复，系统 shall 在索引前拒绝上传。
- When 文本可解析时，系统 shall 切片、调用千问 Embedding、写入 Chroma，并依次更新为 `INDEXING` 和 `READY`。
- If 未提取到文本或外部调用失败，系统 shall 标记 `FAILED`、保存可读原因并清理当次向量。
- When 用户重试失败文档时，系统 shall 清理旧向量后重新处理且不生成重复切片。
- When 用户删除文档时，系统 shall 删除 Chroma 切片、原文件和 MySQL 记录。

Acceptance：A-R4-01 至 A-R4-04、A-R5-01 至 A-R5-03。
