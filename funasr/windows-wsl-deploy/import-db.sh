#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/app.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 app.env，请先执行: cp app.env.example app.env"
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

echo "==> 启动 MySQL..."
sudo service mysql start >/dev/null 2>&1 || true

if [[ ! -f "$SCHEMA_SQL" ]]; then
  echo "找不到数据库结构文件: $SCHEMA_SQL"
  echo "请把 schema.sql 放到 database 目录，或修改 app.env 里的 SCHEMA_SQL。"
  exit 1
fi

echo "==> 创建数据库和用户..."
sudo mysql <<SQL
CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'localhost' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${MYSQL_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "==> 导入表结构..."
mysql -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$SCHEMA_SQL"

echo "数据库初始化完成: $MYSQL_DATABASE"
