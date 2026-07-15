# BrainOS 微信小程序

该客户端使用 uni-app、Vue 3、TypeScript 与 Pinia，复用现有 Spring Boot `/api/v1/**` 登录、知识库、文档和问答接口。PC Web 与微信小程序共用后端，不需要新增小程序专用问答协议。

## 环境要求

- Node.js 20 或更高版本。
- pnpm 11.7.0。
- 当前版本的微信开发者工具。
- 一个可用的微信小程序 AppID；真实 AppID 不提交到仓库。
- 一个可公网访问、证书有效的 HTTPS API 域名。

## 安装、测试与构建

在 `miniprogram/` 目录执行：

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build:mp-weixin
```

构建输出位于 `miniprogram/dist/build/mp-weixin`。

## 本地配置

按目标环境复制样例文件；`.local` 文件已被 Git 忽略：

```powershell
Copy-Item .env.development.example .env.development.local
# 生产构建时使用：Copy-Item .env.production.example .env.production.local
```

将 `VITE_API_BASE_URL` 改为后端公网 HTTPS 域名，例如 `https://api.example.com`。客户端会统一补全 `/api/v1`，不要提交真实域名、令牌或证书。

仓库中的 `manifest.json` 不包含真实 AppID。仅在微信开发者工具或被忽略的本地开发配置中设置 AppID，禁止提交小程序密钥。

## 导入微信开发者工具

1. 先执行 `pnpm build:mp-weixin`。
2. 在微信开发者工具中选择“导入项目”。
3. 项目目录选择 `miniprogram/dist/build/mp-weixin`。
4. 选择本地 AppID，并使用与目标环境匹配的基础库和真机调试配置。

## 微信公众平台服务器域名

在小程序管理后台的“开发管理 → 开发设置 → 服务器域名”中，将同一个 HTTPS 主机名同时登记为：

- `request` 合法域名：JSON API 与 `enableChunked`/`onChunkReceived` 流式问答使用。
- `uploadFile` 合法域名：文档上传使用。

生产环境不能请求 `localhost`、局域网地址或使用自签名/无效证书的域名。域名、证书链和 HTTPS 端口必须满足微信平台要求。开发者工具中临时关闭域名校验不能替代公众平台配置，也不能用于真机发布验收。

## 文档上传

小程序使用 `chooseMessageFile` 从微信会话中选择文件，并通过 `uni.uploadFile` 以字段名 `file` 上传。支持 PDF、DOCX、TXT、MD、MARKDOWN，单个文件最大 20 MiB；不使用浏览器 `FormData`。

## 流式问答

微信端在同一现有 SSE 请求上启用 `enableChunked`，通过 `onChunkReceived` 增量解析 UTF-8 与 SSE。若设备或基础库没有分块监听能力，客户端会等待同一请求完成后一次性解析并展示，不会切换后端协议。Nginx 必须关闭该 SSE location 的代理缓冲与缓存，并允许分块传输。

## 真机验收清单

请记录微信开发者工具版本、基础库版本、设备/操作系统、API 域名、是否观察到分块回调、是否验证完成响应回退，以及最终结果。不得在记录中保存令牌或凭据。

1. 使用现有用户名/密码登录，并验证访问令牌过期后的刷新。
2. 新建、编辑、查看和删除知识库。
3. 从微信会话选择文档上传，观察状态从解析/索引变为可用。
4. 对失败文档执行重试和删除。
5. 发起问答并观察 `onChunkReceived` 分块增量显示中文回答。
6. 展开并核对回答引用。
7. 在生成过程中点击停止，确认保留已生成文本且可以重试。
8. 在不支持分块监听的设备/模拟条件下验证同一请求完成后的整段回退显示。
9. 退出登录，确认返回登录页且本地会话已清除。
