---
spec_id: brainos-overview-tasks
version: 1.0.0
stage: overview
status: approved
updated_at: 2026-07-14
---

# BrainOS 项目总体任务

## 1. 阶段任务表

`D` 表示 overview 获得最终批准的日期。模块开发严格按编号和依赖执行；任何新增功能必须先修改对应 Spec 并更新状态文件。

| 编号 | 类型 | 负责人 | 状态 | 截止时间 | 依赖 | 关联需求 |
| --- | --- | --- | --- | --- | --- | --- |
| 0.1 | 需求 | Codex | [done] | 2026-07-14 | — | R1-R10 |
| 0.2 | 设计 | Codex | [done] | 2026-07-14 | 0.1 | R1-R10 |
| 0.3 | 审阅 | Baron | [done] | 2026-07-14 | 0.2 | R1-R10 |
| 1.0 | 工程 | Codex | [doing] | D+1 | 0.3 | R10 |
| 2.0 | 后端/前端 | Codex | [todo] | D+2 | 1.0 | R1 |
| 3.0 | 后端/前端 | Codex | [todo] | D+3 | 2.0 | R2、R3 |
| 4.0 | 后端/前端 | Codex | [todo] | D+5 | 3.0 | R4、R5 |
| 5.0 | 后端/前端 | Codex | [todo] | D+7 | 4.0 | R6、R7 |
| 6.0 | 后端/前端 | Codex | [todo] | D+8 | 2.0 | R8、R9 |
| 7.0 | 测试/部署 | Codex | [todo] | D+9 | 3.0、4.0、5.0、6.0 | R1-R10 |
| 8.0 | 交付 | Codex | [todo] | D+10 | 7.0 | R10 |

## 2. 总体任务明细

- [done] **0.1 汇总项目需求与硬性约束**
  - 已读取 README、开发规范 Markdown、开发规范 MindManager、项目文档 PDF 和技术栈示意图。
  - 已确认排除移动端，采用答辩轻量完整版范围。
  - _关联验收：A-R10-04 及 R1-R10 的范围约束_

- [done] **0.2 完成总体架构与 UI 方向设计**
  - 已确认模块化单体、MySQL + Redis + Chroma、本地文件存储、千问 Embedding、千问/DeepSeek Chat。
  - 已使用 UI-UX Pro Max 确定 Minimalism + Swiss Style 桌面设计基线。
  - _关联验收：A-R2-01 至 A-R2-03、A-R6-01 至 A-R6-04、A-R10-04_

- [done] **0.3 审阅并批准 overview Spec**
  - 审阅 `requirements.md`、`design.md`、`tasks.md` 和 `status.yaml` 的一致性与范围。
  - 用户已批准书面 Spec，`status.yaml` 已更新为 `approved`，允许创建模块 Spec 并编写实施计划。
  - _关联验收：R1-R10 全部条目_

- [doing] **1.0 建立工程与验证基线**
  - 创建 Maven 后端、Vite Vue 前端、Docker Compose、Flyway、Lombok、MapStruct、springdoc-openapi、环境变量模板和统一质量脚本。
  - 创建 `foundation` 模块 Spec，逐项执行测试先行任务。
  - _关联验收：A-R10-01、A-R10-02_

- [todo] **2.0 实现认证与权限模块**
  - 创建 `auth` 模块 Spec；实现管理员初始化、登录、刷新、退出、当前用户和角色保护。
  - 先完成认证、刷新令牌、停用用户和越权失败测试，再实现功能。
  - _关联验收：A-R1-01 至 A-R1-04_

- [todo] **3.0 实现工作台与知识库模块**
  - 创建 `dashboard` 和 `knowledge` 模块 Spec。
  - 实现真实聚合统计、七日趋势、最近文档以及知识库生命周期。
  - _关联验收：A-R2-01 至 A-R2-03、A-R3-01 至 A-R3-03_

- [todo] **4.0 实现文档导入与 Chroma 索引模块**
  - 创建 `document` 模块 Spec。
  - 实现上传验证、本地存储、哈希去重、解析、切片、异步状态机、千问 Embedding、Chroma 写入/过滤/清理和重试。
  - _关联验收：A-R4-01 至 A-R4-04、A-R5-01 至 A-R5-03_

- [todo] **5.0 实现 RAG 问答与会话模块**
  - 创建 `rag` 模块 Spec。
  - 实现知识库限定检索、Top 5 和阈值、提示词、千问/DeepSeek 切换、SSE 协议、引用与会话所有权。
  - _关联验收：A-R6-01 至 A-R6-04、A-R7-01 至 A-R7-03_

- [todo] **6.0 实现用户管理与审计模块**
  - 创建 `admin` 模块 Spec。
  - 实现用户新建/编辑/启停、最后管理员保护、审计写入和筛选分页。
  - _关联验收：A-R8-01 至 A-R8-03、A-R9-01 至 A-R9-03_

- [todo] **7.0 完成集成、端到端与桌面视觉验收**
  - 执行后端单元/集成测试、前端组件测试和 Playwright 主链路。
  - 在 1024px 与 1440px 验证布局、键盘操作、空状态、错误状态和答辩主流程。
  - _关联验收：A-R10-02 至 A-R10-04，以及各模块 Acceptance_

- [todo] **8.0 完成交付材料**
  - 完善 README、Swagger/OpenAPI 使用说明、部署与演示说明、测试总结和答辩功能/技术亮点摘要。
  - 更新所有模块状态和 `specs/index.md`，确保需求、任务、代码和测试可追溯。
  - _关联验收：A-R10-01 至 A-R10-05_

## 3. Acceptance 追踪矩阵

| Acceptance | 总体任务 | 模块 Spec |
| --- | --- | --- |
| A-R1-01 至 A-R1-04 | 2.0、7.0 | auth |
| A-R2-01 至 A-R2-03 | 3.0、7.0 | dashboard |
| A-R3-01 至 A-R3-03 | 3.0、7.0 | knowledge |
| A-R4-01 至 A-R4-04 | 4.0、7.0 | document |
| A-R5-01 至 A-R5-03 | 4.0、7.0 | document |
| A-R6-01 至 A-R6-04 | 5.0、7.0 | rag |
| A-R7-01 至 A-R7-03 | 5.0、7.0 | rag |
| A-R8-01 至 A-R8-03 | 6.0、7.0 | admin |
| A-R9-01 至 A-R9-03 | 6.0、7.0 | admin |
| A-R10-01 至 A-R10-05 | 1.0、7.0、8.0 | foundation |

## 4. 初始模块优先级

| 优先级 | 模块 | 原因 |
| --- | --- | --- |
| P0 | foundation、auth | 所有页面和接口的运行与权限基础 |
| P0 | knowledge、document | RAG 可演示数据的入口 |
| P0 | rag | 项目核心价值与答辩主链路 |
| P1 | dashboard | 提升完成度，复用已产生的业务数据 |
| P1 | admin | 提供企业后台特征与可追溯性 |

## 5. 状态维护规则

1. 每次只允许一个总体任务为 `[doing]`。
2. 模块开始前，模块目录必须包含 `requirements.md`、`design.md`、`tasks.md`、`status.yaml`。
3. 模块状态只能按 `draft -> review -> approved -> in-dev -> done` 前进。
4. 每个开发任务必须引用 Acceptance ID；没有映射的功能不得开发。
5. 需求或任务变化必须先更新 Spec 和 `status.yaml`，再修改代码。
