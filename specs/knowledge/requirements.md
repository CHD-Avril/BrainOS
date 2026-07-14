---
module: knowledge
status: review
depends_on: [auth]
updated_at: 2026-07-14
---

# Knowledge 模块需求

- When 启用用户提交合法且全局唯一的名称时，系统 shall 创建企业共享知识库。
- When 名称为空或重复时，系统 shall 返回字段级校验错误。
- When 用户编辑知识库时，系统 shall 更新名称、描述和更新时间。
- When 用户删除知识库时，系统 shall 要求确认，并级联清理文档、原文件、Chroma 向量和会话。
- When 用户查询知识库时，系统 shall 返回其文档数、可用文档数和更新时间。

Acceptance：A-R3-01、A-R3-02、A-R3-03。
