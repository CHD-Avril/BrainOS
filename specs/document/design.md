---
module: document
status: review
depends_on: [foundation, auth, knowledge]
deliverables: [api, parser, indexer, chroma, vue-page, tests]
---

# Document 模块设计

- `DocumentStatus`：`PARSING`、`INDEXING`、`READY`、`FAILED`。
- 上传端点：`POST /api/v1/knowledge-bases/{id}/documents`，列表端点：`GET .../documents`。
- 操作端点：`POST /api/v1/documents/{id}/retry`、`DELETE /api/v1/documents/{id}`。
- `FileStoragePort` 负责 UUID 存储名、原子写入、读取和删除。
- `DocumentParserPort` 使用 Apache Tika/Spring AI Reader 输出带页码元数据的文本。
- `DocumentChunker` 使用 TokenTextSplitter，产生稳定 `documentId:chunkIndex` ID。
- `EmbeddingPort` 由千问 Embedding 实现；`VectorIndexPort` 由 Spring AI Chroma Vector Store 实现。
- Chroma 元数据固定为 `knowledgeBaseId`、`documentId`、`fileName`、`pageNumber`、`chunkIndex`。
- `DocumentIndexingExecutor` 使用受控 `TaskExecutor` 异步运行，不引入队列。
- 前端知识库详情包含上传区、状态轮询和文档表格。
