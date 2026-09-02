#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/app.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
else
  PUBLIC_PORT=8088
  BACKEND_PORT=8080
  MYSQL_PORT=3306
  MINIO_PORT=9000
  FUNASR_HTTP_PORT=8002
  FUNASR_WS_PORT=10095
  TTS_PORT=8003
  VOICEPRINT_PORT=8004
  NATAPP_PUBLIC_URL="http://voice.baconboat.cn"
fi

check_port() {
  local name="$1"
  local port="$2"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "$name: RUNNING ($port)"
  else
    echo "$name: STOPPED ($port)"
  fi
}

echo "=== 服务状态 ==="
check_port "MySQL" "$MYSQL_PORT"
check_port "MinIO" "$MINIO_PORT"
check_port "FunASR HTTP" "$FUNASR_HTTP_PORT"
check_port "FunASR WebSocket" "$FUNASR_WS_PORT"
check_port "TTS" "$TTS_PORT"
check_port "Voiceprint" "$VOICEPRINT_PORT"
check_port "Spring Boot" "$BACKEND_PORT"
check_port "Nginx" "$PUBLIC_PORT"

echo
echo "=== 地址 ==="
echo "本地入口: http://localhost:$PUBLIC_PORT"
echo "公网地址: ${NATAPP_PUBLIC_URL:-未配置}"

if [[ -n "${NATAPP_PUBLIC_URL:-}" ]]; then
  code="$(curl -sS --max-time 8 -o /dev/null -w "%{http_code}" "$NATAPP_PUBLIC_URL" 2>/dev/null || true)"
  if [[ "$code" =~ ^(200|301|302)$ ]]; then
    echo "公网状态: HEALTHY ($code)"
  else
    echo "公网状态: UNHEALTHY (${code:-NO_RESPONSE})"
  fi
fi
