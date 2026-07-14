#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[verify] infrastructure"
docker compose -f "$ROOT/docker-compose.yml" up -d --wait mysql redis chroma
docker compose -f "$ROOT/docker-compose.yml" ps mysql redis chroma

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
