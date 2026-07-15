# BrainOS 部署说明

## 1. 开发/答辩环境

答辩时建议“Docker 基础设施 + 本机后端 + 本机前端”，便于查看日志与现场调整。

```bash
cp .env.example .env
# 编辑 .env，填写 QWEN_API_KEY，可选 DEEPSEEK_API_KEY
docker compose up -d --wait mysql redis chroma
```

终端 A：

```bash
cd backend
set -a && source ../.env && set +a
./mvnw spring-boot:run
```

终端 B：

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev --host 127.0.0.1
```

启动后检查：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8000/api/v2/heartbeat
```

## 2. 非开发环境变量

| 变量 | 必填 | 用途 |
| --- | --- | --- |
| `MYSQL_URL` / `MYSQL_USER` / `MYSQL_PASSWORD` | 是 | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | 是 | Redis 刷新令牌 |
| `CHROMA_URL` | 是 | Chroma HTTP 地址 |
| `BRAINOS_STORAGE_PATH` | 是 | 可持久化的文档目录 |
| `BRAINOS_JWT_SECRET` | 是 | 至少 32 字节的随机 JWT 密钥 |
| `BRAINOS_ADMIN_PASSWORD` | 是 | 初始管理员强密码 |
| `QWEN_API_KEY` | 是 | Embedding 与默认千问 Chat |
| `DEEPSEEK_API_KEY` | 否 | DeepSeek Chat |

生产必须关闭开发默认值，通过密钥管理服务注入凭据，并为 MySQL、Chroma 和文档目录挂载持久化卷。

## 3. 构建

```bash
cd backend && ./mvnw clean package
cd ../frontend && pnpm install --frozen-lockfile && pnpm build
```

- 后端产物：`backend/target/brainos-backend-0.0.1-SNAPSHOT.jar`
- 前端产物：`frontend/dist/`

## 3.1 Nginx 反向代理

生产环境用 Nginx 托管 `frontend/dist` 并将 `/api` 代理至 Spring Boot。仓库已提供两份现成配置：

- `deploy/nginx/templates/brainos.conf.template`：Docker 版，供 Compose 的 `nginx` 服务使用（`envsubst` 注入后端地址）。
- `deploy/nginx/brainos.conf`：宿主机直接安装 Nginx 时使用（复制到 `/etc/nginx/conf.d/`）。

两份配置都已包含关键处理：

- SSE 问答路径 `/api/v1/chat/sessions/{id}/messages/stream` 单独关闭 `proxy_buffering` 与 `proxy_cache`，并将读写超时提高到 1 小时，避免流式输出被缓冲或截断。
- `client_max_body_size 25m`，覆盖单文件 20MB 上传上限。
- 前端单页应用回退到 `index.html`。

### 方式 A：Docker Compose（推荐用于演示/单机）

```bash
# 先构建前端产物
cd frontend && pnpm install --frozen-lockfile && pnpm build && cd ..

# 启动可选的 nginx 服务（profile 隔离，不影响基础设施）
docker compose --profile web up -d nginx
```

- 后端在宿主机运行时，默认上游为 `host.docker.internal:8080`，无需额外配置。
- 后端若也在 Compose 网络内运行，设置 `BACKEND_UPSTREAM=backend:8080` 后再启动。
- 访问 `http://localhost`（80 端口）即可。

### 方式 B：宿主机安装 Nginx

1. 将 `frontend/dist` 复制到 `/var/www/brainos`（或修改配置中的 `root`）。
2. 将 `deploy/nginx/brainos.conf` 复制到 `/etc/nginx/conf.d/brainos.conf`，按需调整 `upstream` 后端地址与 `server_name`。
3. 执行 `nginx -t && nginx -s reload`。

## 4. 微信小程序部署

微信小程序继续复用现有 Spring Boot `/api/v1/**`，生产链路应为“微信小程序 → HTTPS 公网域名/Nginx → Spring Boot”。PC Web 可继续由同一 Nginx 托管，两端共享 MySQL、Redis、Chroma 与 AI 服务。

### 4.1 HTTPS 与微信合法域名

- 在公网域名上终止 HTTPS，使用受信任且完整的证书链；生产环境不得使用 `localhost`、局域网地址或自签名证书。
- 在微信公众平台将同一 API 主机名同时配置为 `request` 合法域名和 `uploadFile` 合法域名。
- `request` 承载登录、知识库、文档查询和问答；`uploadFile` 承载 `uni.uploadFile` 文档上传。
- 开发者工具的“不校验合法域名”开关仅供临时模拟，不能替代真机与发布配置。

### 4.2 Nginx 分块交付

SSE location 必须保留 `proxy_http_version 1.1`、空的上游 `Connection`、`proxy_buffering off`、`proxy_cache off` 和 3600 秒读写超时。不要配置 `chunked_transfer_encoding off`，否则微信端 `onChunkReceived` 可能无法及时收到分块。

部署前在仓库根目录执行：

```powershell
powershell -File scripts/verify-miniprogram-proxy.ps1
```

微信端使用现有 `/api/v1/chat/sessions/{id}/messages/stream`：支持时以 `enableChunked` + `onChunkReceived` 增量展示；不支持分块监听时，等待同一请求完成后解析整段响应，不需要新增后端协议。

### 4.3 环境配置与构建

```powershell
Set-Location miniprogram
Copy-Item .env.production.example .env.production.local
# 编辑 .env.production.local，设置证书有效且已登记的 HTTPS API 域名
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build:mp-weixin
```

`VITE_API_BASE_URL` 填写主机根地址即可，客户端会补全 `/api/v1`。真实域名、AppID、AppSecret、令牌和证书不得提交到 Git。

### 4.4 微信开发者工具与真机

在微信开发者工具中导入 `miniprogram/dist/build/mp-weixin`，并仅在本地开发配置中填写真实 AppID。上传发布前，按 [小程序真机验收清单](../miniprogram/README.md#真机验收清单) 验证登录刷新、知识库 CRUD、文档上传/轮询/重试/删除、分块与回退问答、引用、停止和退出。

## 5. 数据与恢复

- MySQL：定期执行逻辑备份，Flyway 只管理表结构变更。
- Chroma：备份持久化目录；也可保留原文档后重建索引。
- 文档：备份 `BRAINOS_STORAGE_PATH` 指向的目录。
- Redis：只保存可撤销的刷新令牌，丢失后用户重新登录即可。

## 6. 上线检查

1. `bash scripts/verify.sh` 全部通过。
2. `/actuator/health` 返回 `UP`。
3. Swagger UI 中不存在真实密钥或密码。
4. 上传演示文档能从解析到可用，问答引用指向正确文件。
5. 普通用户无法访问 `/api/v1/admin/**`。
