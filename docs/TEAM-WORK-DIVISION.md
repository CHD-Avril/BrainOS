# BrainOS 五人开发分工

## 1. 分工原则

BrainOS 按照“可独立开发、可独立测试、可独立答辩”的原则划分为五条功能线。分工不以代码行数作为唯一依据，而是综合考虑技术难度、页面数量、测试成本、接口联调和答辩讲解工作量。

每位成员均需要完成本模块的：

- 需求分析与设计说明。
- 后端或前端功能实现。
- 单元测试与接口测试。
- 与上下游模块的接口联调。
- 答辩讲稿、成功用例和异常用例准备。

## 2. 工作量总览

| 成员 | 工作量 | 主要负责内容 | 答辩主题 |
| --- | ---: | --- | --- |
| 成员一 | 20% | 基础架构、登录鉴权、权限安全 | 系统如何保证安全访问 |
| 成员二 | 20% | 文档上传、解析、切片与异步处理 | 文档如何转换为可检索知识 |
| 成员三 | 21% | AI Embedding、Chroma Cloud、RAG 检索 | AI 如何找到正确资料 |
| 成员四 | 20% | AI 对话、模型调用、流式输出与引用 | AI 如何基于资料生成回答 |
| 成员五 | 19% | 知识库管理、工作台、用户管理、审计与验收 | 企业管理功能与整体演示 |

## 3. 成员一：基础架构与安全认证

### 3.1 工作内容

- 完成 Spring Boot 项目基础配置。
- 设计统一 API 响应格式和全局异常处理。
- 配置 MySQL、Redis、Flyway 和 Swagger/OpenAPI。
- 实现 JWT 登录鉴权和请求过滤器。
- 实现 Redis Refresh Token 的签发、刷新、注销和失效。
- 实现 `ADMIN` 和 `USER` 角色权限控制。
- 实现前端登录页、Pinia 登录状态和用户信息管理。
- 实现 Axios Token 注入、401 处理和前端路由守卫。
- 配置公共布局、权限菜单和退出登录。
- 管理环境变量和本地密钥保护规则。

### 3.2 主要代码范围

- `backend/src/main/java/com/brainos/auth`
- `backend/src/main/java/com/brainos/common`
- `backend/src/main/java/com/brainos/foundation`
- `frontend/src/features/auth`
- `frontend/src/api`
- `frontend/src/router`
- `frontend/src/layouts`

### 3.3 交付与验收

- 正确账号可以登录，错误密码能够给出正确提示。
- 普通用户不能访问管理员接口和页面。
- Access Token 过期后可以使用 Refresh Token 刷新。
- 注销后原 Refresh Token 立即失效。
- `.env` 和真实 API Key 不会提交到 Git。

## 4. 成员二：文档导入与处理流水线

### 4.1 工作内容

- 实现 PDF、DOCX、TXT 和 Markdown 文档上传。
- 校验文件格式、大小、空文件和重复文件。
- 实现本地文件安全存储、读取和删除。
- 使用 Apache Tika 解析文档文本内容。
- 识别 Markdown 标题、段落和制度条款结构。
- 实现结构化切片和过长内容二次切分。
- 实现文档异步索引任务调度。
- 实现 `PARSING -> INDEXING -> READY/FAILED` 文档状态机。
- 实现失败重试、文档删除和资源清理。
- 实现前端文档上传、状态展示、刷新、重试和删除。

### 4.2 主要代码范围

- `backend/src/main/java/com/brainos/document/api`
- `backend/src/main/java/com/brainos/document/application`
- `backend/src/main/java/com/brainos/document/chunking`
- `backend/src/main/java/com/brainos/document/domain`
- `backend/src/main/java/com/brainos/document/parsing`
- `backend/src/main/java/com/brainos/document/persistence`
- `backend/src/main/java/com/brainos/document/storage`
- `frontend/src/features/document`

### 4.3 交付与验收

- 系统支持四种要求的文档格式。
- 员工手册能按制度条款切分为六个主要知识切片。
- 文档上传和索引过程不会长时间阻塞 HTTP 接口。
- 索引失败时能够展示原因并执行重试。
- 删除文档时同步清理文件、数据库记录和索引。

## 5. 成员三：AI 检索与 Chroma Cloud

### 5.1 工作内容

- 接入千问 `text-embedding-v4` Embedding 服务。
- 实现文档切片和用户问题的向量生成。
- 实现 Chroma Cloud API Key 鉴权。
- 实现 Chroma Tenant、Database 和 Collection 动态配置。
- 创建和维护 `brainos_documents` 集合。
- 实现文档向量写入、替换、查询和删除。
- 实现基于 Knowledge Base ID 的服务端过滤。
- 设计 TopK、相似度、精确短语和相对分数窗口策略。
- 实现 RAG 检索计划和低可信度拒答逻辑。
- 生成稳定的引用候选信息。
- 建立精确问法、同义问法和知识库外问题评测集。

### 5.2 主要代码范围

- `backend/src/main/java/com/brainos/ai`
- `backend/src/main/java/com/brainos/document/indexing`
- `backend/src/main/java/com/brainos/rag/retrieval`
- `backend/src/main/java/com/brainos/rag/application/RagPlanningService.java`
- `backend/src/main/java/com/brainos/rag/application/RagAnswerPlan.java`
- `backend/src/main/java/com/brainos/rag/model/RagPromptFactory.java`
- `docs/research/enterprise-rag-practices.md`

### 5.3 交付与验收

- Chroma Cloud 中能查看到真实数据库、集合和切片数据。
- “员工几点下班”能够召回工作时间条款。
- “发现可疑邮件怎么办”能够召回信息安全条款。
- 不同知识库之间不会发生知识数据泄漏。
- 知识库外问题能够返回“未找到可靠依据”。
- 回答所使用的来源与实际文档切片一致。

## 6. 成员四：AI 对话与引用展示

### 6.1 工作内容

- 配置千问和 DeepSeek 双模型。
- 实现按会话选择问答模型。
- 将检索上下文组装到 RAG Prompt。
- 实现会话创建、重命名和删除。
- 实现用户消息、AI 消息和来源数据持久化。
- 实现 SSE 流式回答接口。
- 实现流式异常、终止和完成事件。
- 实现前端历史会话列表和新建对话。
- 实现知识库选择、模型切换和问题发送。
- 实现 Markdown 回答渲染。
- 实现来源编号、来源卡片、切片内容和相似度展示。
- 实现停止生成、失败提示和回答重试。

### 6.2 主要代码范围

- `backend/src/main/java/com/brainos/rag/api`
- `backend/src/main/java/com/brainos/rag/application/ChatSessionService.java`
- `backend/src/main/java/com/brainos/rag/application/RagChatService.java`
- `backend/src/main/java/com/brainos/rag/application/ChatStreamEvent.java`
- `backend/src/main/java/com/brainos/rag/domain`
- `backend/src/main/java/com/brainos/rag/persistence`
- `backend/src/main/java/com/brainos/rag/model`
- `frontend/src/features/chat`

### 6.3 交付与验收

- AI 回答能够逐步流式显示。
- 刷新页面后历史会话和消息仍然存在。
- 用户可以切换千问和 DeepSeek。
- 回答中的 `[来源1]` 与页面下方的来源卡片对应。
- 模型异常时返回可理解的错误提示。

## 7. 成员五：企业管理功能与整体交付

### 7.1 工作内容

- 实现知识库创建、修改、查询和删除。
- 统计知识库的文档数和可用文档数。
- 实现工作台知识库数、文档数、问答数和用户数指标。
- 实现七天文档增长趋势图和最近文档。
- 实现管理员创建、修改、启用和禁用用户。
- 实现最后一名管理员保护。
- 记录登录、知识库、文档和用户管理操作审计日志。
- 实现审计日志分页、筛选和页面展示。
- 实现前端工作台、知识库卡片、用户管理和审计页面。
- 组织全链路联调、Playwright 验收和答辩演示。

### 7.2 主要代码范围

- `backend/src/main/java/com/brainos/knowledge`
- `backend/src/main/java/com/brainos/dashboard`
- `backend/src/main/java/com/brainos/admin`
- `frontend/src/features/knowledge`
- `frontend/src/features/dashboard`
- `frontend/src/features/admin`
- `frontend/src/styles`
- `docs/DEFENSE-DEMO.md`
- `docs/DEPLOYMENT.md`
- `docs/TEST-REPORT.md`

### 7.3 交付与验收

- 知识库创建、编辑和删除功能正常。
- 工作台数据来自真实数据库。
- 管理员可以管理用户，普通用户无法查看管理菜单。
- 关键操作能够在审计日志中查询。
- 完整答辩演示流程可以连续执行。

## 8. 成员间接口边界

| 提供方 | 输出 | 使用方 |
| --- | --- | --- |
| 成员一 | 登录用户、JWT、权限规则、统一 API 响应 | 全体成员 |
| 成员五 | Knowledge Base ID 和知识库权限 | 成员二、成员三、成员四 |
| 成员二 | 已解析的文档切片 `DocumentChunk` | 成员三 |
| 成员三 | RAG 上下文和引用候选 `RagAnswerPlan` | 成员四 |
| 成员四 | AI 回答、来源引用和会话数据 | 成员五的工作台统计 |

## 9. 测试与代码审查分工

- 每位成员编写自己模块的单元测试和至少一个集成测试。
- 成员一审查成员五的权限和管理接口。
- 成员二审查成员三的切片元数据和索引入参。
- 成员三审查成员四的 RAG 上下文和来源使用。
- 成员四审查成员二的文档状态和错误展示。
- 成员五负责组织最终的前后端全链路验收。

## 10. 答辩讲解顺序

1. 成员一：项目总体架构、登录鉴权和权限安全。
2. 成员二：文档上传、内容解析、切片和异步索引流程。
3. 成员三：千问 Embedding、Chroma Cloud 和 RAG 检索。
4. 成员四：AI 流式问答、多模型切换和来源引用。
5. 成员五：工作台、知识库管理、用户审计、测试结果和项目总结。
