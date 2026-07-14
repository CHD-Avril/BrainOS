---
module: foundation
status: review
depends_on: [overview]
deliverables: [backend, frontend, compose, migrations, quality-scripts]
---

# Foundation 模块设计

- `backend/`：Spring Boot 3.5.16、Spring AI 1.1.8、Java 21、Maven Wrapper。
- `frontend/`：Vue 3.4+、Vite、TypeScript、Vue Router、Pinia、Element Plus、Vitest。
- `docker-compose.yml`：MySQL 8.x、Redis 7.x、Chroma 1.x，均配置健康检查和命名卷。
- `backend/src/main/resources/db/migration/`：Flyway 版本化 SQL。
- `backend/common`：`ApiResponse<T>`、`PageResponse<T>`、`ErrorCode`、全局异常和追踪 ID。
- `scripts/verify.sh`：依次运行后端测试、前端类型检查/测试/构建。
- `.env.example`：只列变量名和安全说明，不包含可用凭据。

后端端口为 `8080`，前端开发端口为 `5173`，Chroma 为 `8000`，MySQL 为 `3306`，Redis 为 `6379`。API 根路径统一为 `/api/v1`。
