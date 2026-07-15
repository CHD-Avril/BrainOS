# BrainOS 轻量企业 AI 知识库设计规格

## 背景与目标

BrainOS 将在空仓库中实现一个适合课程答辩的 PC Web 企业 AI 知识库。成品要比基础 CRUD 项目更完整，能够真实演示文档导入、Chroma 向量索引、RAG 流式问答、来源引用和后台管理；同时避免微服务、多租户、OCR、复杂权限等会显著增加实现与演示风险的能力。

本规格的正式需求、详细架构和任务入口分别位于：

- `specs/overview/requirements.md`
- `specs/overview/design.md`
- `specs/overview/tasks.md`
- `specs/overview/status.yaml`
- `design-system/MASTER.md`

## 已确认决策

1. 产品范围为“答辩轻量完整版”。
2. 仅开发 PC Web，完全排除微信小程序、鸿蒙、iOS 和 Android。
3. 架构采用 Spring Boot 模块化单体，不拆微服务。
4. 前端采用 Vite + Vue 3 + Vue Router + Pinia + Element Plus。
5. 后端采用 Spring Boot 3、Spring MVC、Spring Security、MyBatis-Plus、MySQL 和 Redis。
6. Chroma 是演示向量数据库；本地目录保存原文件；MySQL 保存业务元数据。
7. 千问负责 Embedding 和默认聊天；DeepSeek 是可切换聊天模型。
8. 文档格式为 PDF、DOCX、TXT、Markdown，单文件上限 20MB，不做 OCR。
9. 用户只有管理员和普通用户两种角色，不开放注册。
10. UI 使用 UI-UX Pro Max 推荐的 Minimalism + Swiss Style，追求简约、美观、易操作。
11. 工程辅助使用 Lombok、MapStruct、springdoc-openapi、Swagger UI 和 Mermaid，满足指定开发规范。

## 产品范围

### 必须完成

- 登录、刷新令牌、退出和角色访问控制。
- 真实数据工作台。
- 知识库新建、编辑、查询和级联删除。
- 文档上传、解析、切片、异步向量化、状态、重试和删除。
- Chroma 知识库过滤、向量写入、检索和清理。
- 千问/DeepSeek 流式问答、可靠性兜底、来源引用。
- 会话历史和所有权保护。
- 管理员用户管理和操作日志。
- Docker Compose、初始化管理员、自动化测试和答辩演示说明。
- 可浏览和调用的 OpenAPI/Swagger API 文档。

知识库和文档是企业共享资源，全部启用用户均可查看和维护；会话是个人资源，只允许所属用户访问。管理员在这些能力之外拥有用户管理和操作日志权限。

### 明确排除

- 移动端和移动端响应式交付。
- OCR、图片/音视频/Excel 解析。
- 多租户、组织架构、SSO、LDAP、审批和细粒度数据权限。
- 微服务、消息队列、分布式调度、Serverless。
- 模型训练、Agent、联网搜索、语音和计费。

## 架构

Vue SPA 通过 JSON API 调用一个 Spring Boot 应用，AI 回答使用 SSE。Spring Boot 内部按 `auth`、`dashboard`、`knowledge`、`document`、`rag`、`admin`、`common` 分包，模块通过应用服务协作，不绕过边界直接共享业务 Mapper。

MySQL 保存用户、知识库、文档状态、会话、消息和审计日志；Redis 保存刷新令牌与短期缓存；Chroma 保存切片文本、向量和检索元数据；原始文件保存到服务端配置目录。Docker Compose 统一提供三个基础服务。

## 核心数据流

### 文档导入

前端上传文件后，后端完成类型、MIME、大小、文件名和哈希校验，保存原文件并创建 `PARSING` 记录。受控异步执行器解析文本、切片并将状态改为 `INDEXING`，随后调用千问 Embedding 并写入 Chroma。全部成功后记录 `READY` 与切片数；任何阶段失败都记录可读错误并清理当次向量。

### RAG 问答

用户创建绑定知识库和聊天模型的会话。每次提问先用千问 Embedding 生成查询向量，再按 `knowledgeBaseId` 过滤 Chroma，获取 Top 5 且达到阈值的片段。没有可靠结果时直接返回固定兜底答案；存在结果时将编号上下文交给千问或 DeepSeek，通过 SSE 流式输出，结束后保存回答和实际引用。

### 删除一致性

删除文档会清理原文件、Chroma 记录和 MySQL 文档记录；删除知识库会先清理其文档和会话。清理失败必须留下审计与可重试状态，不能静默产生孤儿向量。

## 界面

应用使用 224px 左侧导航、64px 顶栏和灰白主内容区。一级页面只有工作台、知识库、AI 问答，以及管理员可见的用户管理和操作日志；文档管理位于知识库详情内。

设计使用蓝色 `#2563EB`、背景 `#F8FAFC`、白色表面和深灰文字，字体为 Inter + PingFang SC。表格用于文档、用户和日志，知识库允许轻量卡片；AI 问答使用会话侧栏和单一阅读区，引用紧跟回答折叠展示。详细规则由 `design-system/MASTER.md` 定义。

## 安全与错误处理

访问令牌有效期 2 小时，刷新令牌摘要存入 Redis 7 天。密码使用 BCrypt；管理员资源同时执行 URL 和方法授权；停用账号不能登录或刷新。上传路径由服务端生成，真实密钥只来自环境变量，日志不得记录密码、令牌和供应商密钥。

普通 API 返回稳定错误码、可读消息、追踪 ID 和时间。模型超时、限流、连接失败、文档无文本、重复文件和 Chroma 写入失败都映射为明确状态。SSE 中断不能保存空 AI 消息。

## 测试与验收

- 后端使用测试先行方式覆盖认证、权限、状态机、向量过滤/清理、RAG 兜底和引用。
- Testcontainers 验证 MySQL、Redis 与 Chroma 集成。
- 前端使用 Vitest 和 Vue Test Utils 验证表单、上传、状态、模型选择、SSE 和引用。
- Playwright 验证“管理员登录 -> 新建知识库 -> 上传文档 -> 等待可用 -> 提问 -> 查看引用”以及普通用户越权失败。
- 桌面视觉在 1024px 和 1440px 检查，不承担移动端验收。
- 每条实现任务必须映射 `A-Rx-xx`，未写入 Spec 的功能不得直接开发。

## 交付门槛

overview 状态必须先由 `review` 更新为 `approved`。随后每个功能模块创建独立的 `requirements.md`、`design.md`、`tasks.md` 和 `status.yaml`，按依赖顺序开发。全部自动化测试、浏览器主链路、桌面视觉对照和 README 启动复验通过后，项目才能标记为完成。
