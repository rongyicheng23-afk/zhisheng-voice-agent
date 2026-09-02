#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/app.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
else
  APP_HOME="$HOME/voice-factory"
fi

RUNTIME_DIR="${APP_HOME:-$HOME/voice-factory}/runtime"

stop_pid_file() {
  local label="$1"
  local pid_file="$2"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      echo "==> 停止 $label ($pid)"
      kill "$pid" >/dev/null 2>&1 || true
    fi
    rm -f "$pid_file"
  fi
}

stop_pid_file "NATAPP" "$RUNTIME_DIR/natapp.pid"
stop_pid_file "Nginx" "$RUNTIME_DIR/nginx.pid"
stop_pid_file "Spring Boot" "$RUNTIME_DIR/backend.pid"
stop_pid_file "Voiceprint" "$RUNTIME_DIR/voiceprint.pid"
stop_pid_file "TTS" "$RUNTIME_DIR/tts.pid"
stop_pid_file "FunASR WebSocket" "$RUNTIME_DIR/funasr-ws.pid"
stop_pid_file "FunASR HTTP" "$RUNTIME_DIR/funasr-http.pid"
stop_pid_file "MinIO" "$RUNTIME_DIR/minio.pid"

echo "已停止脚本托管的服务。MySQL 默认不停止，如需停止执行: sudo service mysql stop"
