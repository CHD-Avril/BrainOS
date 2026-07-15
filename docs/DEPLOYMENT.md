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

## 4. 数据与恢复

- MySQL：定期执行逻辑备份，Flyway 只管理表结构变更。
- Chroma：备份持久化目录；也可保留原文档后重建索引。
- 文档：备份 `BRAINOS_STORAGE_PATH` 指向的目录。
- Redis：只保存可撤销的刷新令牌，丢失后用户重新登录即可。

## 5. 上线检查

1. `bash scripts/verify.sh` 全部通过。
2. `/actuator/health` 返回 `UP`。
3. Swagger UI 中不存在真实密钥或密码。
4. 上传演示文档能从解析到可用，问答引用指向正确文件。
5. 普通用户无法访问 `/api/v1/admin/**`。
