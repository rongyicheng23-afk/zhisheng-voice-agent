#!/bin/bash
set -euo pipefail

PROJECT_ROOT="/Users/rongyicheng/Desktop/voice.baconboat"
FRONTEND_DIR="$PROJECT_ROOT/layout-voice-factory"
DEPLOY_DIR="$FRONTEND_DIR/deploy"
RUNTIME_DIR="$DEPLOY_DIR/runtime"

NGINX_PID="$RUNTIME_DIR/nginx.pid"
BACKEND_PID_FILE="$RUNTIME_DIR/backend.pid"
BACKEND_SCREEN_SESSION="voice-backend"
CLOUDFLARED_PID_FILE="$RUNTIME_DIR/cloudflared.pid"
NATAPP_PID_FILE="$RUNTIME_DIR/natapp.pid"
NATAPP_SCREEN_SESSION="natapp-voice"
MINIO_SCREEN_SESSION="minio-voice"
FUNASR_HTTP_PID="$RUNTIME_DIR/funasr-http.pid"
FUNASR_WS_PID="$RUNTIME_DIR/funasr-ws.pid"
TTS_PID="$RUNTIME_DIR/tts.pid"
VOICEPRINT_PID="$RUNTIME_DIR/voiceprint.pid"
MINIO_PID="$RUNTIME_DIR/minio.pid"
MYSQL_MARKER="$RUNTIME_DIR/mysql.started_by_script"

find_natapp_pid() {
  pgrep -f "/opt/natapp/natapp|$DEPLOY_DIR/tools/natapp/natapp" | head -n 1 || true
}

stop_by_pid_file() {
  local name="$1"
  local pid_file="$2"

  if [[ ! -f "$pid_file" ]]; then
    return 0
  fi

  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  if [[ -z "$pid" ]]; then
    rm -f "$pid_file"
    return 0
  fi

  if kill -0 "$pid" >/dev/null 2>&1; then
    echo "==> 停止 $name (PID $pid)"
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
  fi

  rm -f "$pid_file"
}

stop_mysql_if_started_by_script() {
  if [[ -f "$MYSQL_MARKER" ]]; then
    echo "==> 停止 MySQL (brew services)"
    brew services stop mysql >/dev/null 2>&1 || true
    rm -f "$MYSQL_MARKER"
  fi
}

main() {
  stop_by_pid_file "NATAPP" "$NATAPP_PID_FILE"
  local natapp_pid
  natapp_pid="$(find_natapp_pid)"
  if [[ -n "$natapp_pid" ]] && kill -0 "$natapp_pid" >/dev/null 2>&1; then
    echo "==> 停止 NATAPP (PID $natapp_pid)"
    kill "$natapp_pid" >/dev/null 2>&1 || true
  fi
  screen -S "$NATAPP_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
  stop_by_pid_file "cloudflared" "$CLOUDFLARED_PID_FILE"
  stop_by_pid_file "Spring Boot" "$BACKEND_PID_FILE"
  screen -S "$BACKEND_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
  stop_by_pid_file "Voiceprint" "$VOICEPRINT_PID"
  stop_by_pid_file "TTS" "$TTS_PID"
  stop_by_pid_file "FunASR WebSocket" "$FUNASR_WS_PID"
  stop_by_pid_file "FunASR HTTP" "$FUNASR_HTTP_PID"
  stop_by_pid_file "MinIO" "$MINIO_PID"
  screen -S "$MINIO_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
  stop_by_pid_file "Nginx" "$NGINX_PID"
  stop_mysql_if_started_by_script
}

main "$@"
