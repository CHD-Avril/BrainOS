#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[verify] infrastructure"
docker compose -f "$ROOT/docker-compose.yml" up -d --wait mysql redis chroma
docker compose -f "$ROOT/docker-compose.yml" ps mysql redis chroma
docker compose -f "$ROOT/docker-compose.yml" exec -T mysql \
  sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --protocol=socket -uroot' <<'SQL'
drop database if exists brainos_e2e;
create database brainos_e2e character set utf8mb4 collate utf8mb4_unicode_ci;
grant all privileges on brainos_e2e.* to 'brainos'@'%';
SQL

echo "[verify] backend"
(cd "$ROOT/backend" && ./mvnw --no-transfer-progress verify)

echo "[verify] frontend dependencies"
(cd "$ROOT/frontend" && pnpm install --frozen-lockfile)

echo "[verify] frontend unit tests"
(cd "$ROOT/frontend" && pnpm test)

echo "[verify] frontend typecheck"
(cd "$ROOT/frontend" && pnpm typecheck)

echo "[verify] frontend build"
(cd "$ROOT/frontend" && pnpm build)

echo "[verify] browser authentication flow"
(cd "$ROOT/frontend" && pnpm exec playwright test)

echo "[verify] PASS"
