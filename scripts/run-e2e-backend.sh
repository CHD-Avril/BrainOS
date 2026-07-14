#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose -f "$ROOT/docker-compose.yml" up -d --wait mysql redis

# This account is test-only and is granted access only to the recreated E2E database.
docker compose -f "$ROOT/docker-compose.yml" exec -T mysql \
  sh -c 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --protocol=socket -uroot' <<'SQL'
drop database if exists brainos_e2e;
create database brainos_e2e character set utf8mb4 collate utf8mb4_unicode_ci;
create user if not exists 'brainos_e2e'@'%' identified by 'BrainOS-e2e-test-only-2026';
alter user 'brainos_e2e'@'%' identified by 'BrainOS-e2e-test-only-2026';
revoke all privileges, grant option from 'brainos_e2e'@'%';
grant all privileges on brainos_e2e.* to 'brainos_e2e'@'%';
SQL

export MYSQL_URL='jdbc:mysql://localhost:3306/brainos_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
export MYSQL_USER='brainos_e2e'
export MYSQL_PASSWORD='BrainOS-e2e-test-only-2026'

cd "$ROOT/backend"
exec ./mvnw --no-transfer-progress spring-boot:run
