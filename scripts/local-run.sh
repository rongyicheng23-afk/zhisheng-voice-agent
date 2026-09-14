#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "$0")/.." && pwd)"
RUNNER="${ZHISHENG_RUNNER:-$PROJECT_DIR/.local-run/manage.py}"
ACTION="${1:-status}"
case "$ACTION" in start|stop|status) ;; *) echo '用法: scripts/local-run.sh start|stop|status'; exit 2;; esac
if [[ ! -f "$RUNNER" ]]; then
  echo '未配置本机运行环境。请将 ZHISHENG_RUNNER 指向已配置的 manage.py，或建立 .local-run 链接。'
  exit 1
fi
export ZHISHENG_PROJECT="$PROJECT_DIR"
exec "${ZHISHENG_PYTHON:-python3}" "$RUNNER" "$ACTION"
