#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/app.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少 app.env，请先执行:"
  echo "  cp $SCRIPT_DIR/app.env.example $SCRIPT_DIR/app.env"
  echo "然后按 Win/WSL 台式机的真实路径修改 app.env。"
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

BUILD_MODE="${1:-}"
RUNTIME_DIR="$APP_HOME/runtime"
LOG_DIR="$RUNTIME_DIR/logs"
NGINX_CONF="$RUNTIME_DIR/nginx.voice-factory.generated.conf"

mkdir -p "$RUNTIME_DIR" "$LOG_DIR" "$MINIO_DATA_DIR"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1"
    exit 1
  fi
}

port_in_use() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

wait_for_port() {
  local port="$1"
  local retries="${2:-25}"
  while (( retries > 0 )); do
    if port_in_use "$port"; then
      return 0
    fi
    sleep 1
    retries=$((retries - 1))
  done
  return 1
}

build_if_needed() {
  if [[ "$BUILD_MODE" == "--build" || ! -f "$FRONTEND_DIST/index.html" ]]; then
    echo "==> 构建前端..."
    require_cmd npm
    cd "$FRONTEND_DIR"
    npm install
    npm run build
  fi

  if [[ "$BUILD_MODE" == "--build" || ! -f "$BACKEND_JAR" ]]; then
    echo "==> 打包 Spring Boot..."
    require_cmd mvn
    cd "$BACKEND_DIR"
    mvn -DskipTests package
  fi
}

start_mysql() {
  if port_in_use "$MYSQL_PORT"; then
    echo "==> MySQL 已在端口 $MYSQL_PORT 运行，跳过启动"
    return 0
  fi
  echo "==> 启动 MySQL..."
  sudo service mysql start >/dev/null 2>&1 || true
  wait_for_port "$MYSQL_PORT" 20 || echo "  [WARN] MySQL 未监听 $MYSQL_PORT"
}

start_minio() {
  require_cmd minio
  if port_in_use "$MINIO_PORT"; then
    echo "==> MinIO 已在端口 $MINIO_PORT 运行，跳过启动"
    return 0
  fi
  echo "==> 启动 MinIO..."
  export MINIO_ROOT_USER MINIO_ROOT_PASSWORD
  nohup minio server "$MINIO_DATA_DIR" \
    --address ":$MINIO_PORT" \
    --console-address ":$MINIO_CONSOLE_PORT" \
    >"$LOG_DIR/minio.log" 2>&1 &
  echo $! > "$RUNTIME_DIR/minio.pid"
  wait_for_port "$MINIO_PORT" 20 || { echo "MinIO 启动失败，查看 $LOG_DIR/minio.log"; exit 1; }
}

start_python_service() {
  local name="$1"
  local port="$2"
  local pid_file="$3"
  local log_file="$4"
  local workdir="$5"
  shift 5

  if port_in_use "$port"; then
    echo "==> $name 已在端口 $port 运行，跳过启动"
    return 0
  fi

  echo "==> 启动 $name..."
  cd "$workdir"
  nohup "$@" >"$log_file" 2>&1 &
  echo $! > "$pid_file"
  wait_for_port "$port" 30 || { echo "$name 启动失败，查看 $log_file"; exit 1; }
}

start_funasr_http() {
  start_python_service \
    "FunASR HTTP" \
    "$FUNASR_HTTP_PORT" \
    "$RUNTIME_DIR/funasr-http.pid" \
    "$LOG_DIR/funasr-http.log" \
    "$FUNASR_DIR" \
    "$PYTHON_BIN" "$PROJECT_ROOT/services/funasr/asr_server.py"
}

start_funasr_ws() {
  start_python_service \
    "FunASR WebSocket" \
    "$FUNASR_WS_PORT" \
    "$RUNTIME_DIR/funasr-ws.pid" \
    "$LOG_DIR/funasr-ws.log" \
    "$PROJECT_ROOT/services/funasr" \
    env PYTHONPATH="$FUNASR_DIR${PYTHONPATH:+:$PYTHONPATH}" "$PYTHON_BIN" "funasr_wss_server.py" \
      --port "$FUNASR_WS_PORT" \
      --device cpu \
      --ngpu 0 \
      --ncpu 4 \
      --asr_model paraformer-zh \
      --asr_model_online paraformer-zh-streaming \
      --vad_model fsmn-vad \
      --punc_model ct-punc \
      --certfile "" \
      --keyfile ""
}

start_tts() {
  if [[ ! -f "$TTS_LAUNCHER" ]]; then
    echo "  [WARN] 未找到 TTS 启动脚本，跳过: $TTS_LAUNCHER"
    return 0
  fi
  start_python_service \
    "TTS" \
    "$TTS_PORT" \
    "$RUNTIME_DIR/tts.pid" \
    "$LOG_DIR/tts.log" \
    "$DEPLOY_DIR" \
    "$PYTHON_BIN" "$TTS_LAUNCHER"
}

start_voiceprint() {
  if [[ ! -f "$VOICEPRINT_DIR/sc_server.py" ]]; then
    echo "  [WARN] 未找到声纹服务脚本，跳过: $VOICEPRINT_DIR/sc_server.py"
    return 0
  fi
  start_python_service \
    "Voiceprint" \
    "$VOICEPRINT_PORT" \
    "$RUNTIME_DIR/voiceprint.pid" \
    "$LOG_DIR/voiceprint.log" \
    "$VOICEPRINT_DIR" \
    env SV_SERVICE_PORT="$VOICEPRINT_PORT" "$PYTHON_BIN" "$VOICEPRINT_DIR/sc_server.py"
}

start_backend() {
  require_cmd java
  if port_in_use "$BACKEND_PORT"; then
    echo "==> Spring Boot 已在端口 $BACKEND_PORT 运行，跳过启动"
    return 0
  fi
  if [[ ! -f "$BACKEND_JAR" ]]; then
    echo "找不到后端 jar: $BACKEND_JAR"
    exit 1
  fi
  echo "==> 启动 Spring Boot..."
  nohup java -jar "$BACKEND_JAR" >"$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$RUNTIME_DIR/backend.pid"
  wait_for_port "$BACKEND_PORT" 30 || { echo "Spring Boot 启动失败，查看 $LOG_DIR/backend.log"; exit 1; }
}

generate_nginx_conf() {
  sed "s#/home/REPLACE_WITH_YOUR_WSL_USER/voice-factory/frontend/dist#$FRONTEND_DIST#g" \
    "$SCRIPT_DIR/nginx.voice-factory.wsl.conf" > "$NGINX_CONF"
}

start_nginx() {
  require_cmd nginx
  if port_in_use "$PUBLIC_PORT"; then
    echo "==> Nginx 已在端口 $PUBLIC_PORT 运行，跳过启动"
    return 0
  fi
  echo "==> 启动 Nginx..."
  generate_nginx_conf
  nginx -p "$RUNTIME_DIR" -c "$NGINX_CONF"
  if [[ -f "$RUNTIME_DIR/logs/nginx.pid" ]]; then
    cp "$RUNTIME_DIR/logs/nginx.pid" "$RUNTIME_DIR/nginx.pid" 2>/dev/null || true
  fi
  wait_for_port "$PUBLIC_PORT" 10 || { echo "Nginx 启动失败"; exit 1; }
}

start_natapp() {
  if [[ "${NATAPP_ENABLED:-false}" != "true" ]]; then
    echo "==> NATAPP 已禁用，跳过启动"
    return 0
  fi
  if [[ ! -x "$NATAPP_BIN" ]]; then
    echo "  [WARN] 未找到 NATAPP 客户端，跳过: $NATAPP_BIN"
    echo "  [WARN] 先确认本地入口 http://localhost:$PUBLIC_PORT 可访问，再安装 NATAPP。"
    return 0
  fi
  if [[ -z "${NATAPP_AUTHTOKEN:-}" ]]; then
    echo "  [WARN] NATAPP_AUTHTOKEN 为空，跳过 NATAPP"
    return 0
  fi
  if pgrep -f "$NATAPP_BIN.*$NATAPP_AUTHTOKEN" >/dev/null 2>&1; then
    echo "==> NATAPP 已在运行，跳过启动"
    echo "公网地址: ${NATAPP_PUBLIC_URL:-}"
    return 0
  fi
  echo "==> 启动 NATAPP..."
  nohup "$NATAPP_BIN" -log=stdout -loglevel=INFO -authtoken="$NATAPP_AUTHTOKEN" >"$LOG_DIR/natapp.log" 2>&1 &
  echo $! > "$RUNTIME_DIR/natapp.pid"
  sleep 4
  echo "公网地址: ${NATAPP_PUBLIC_URL:-请查看 NATAPP 日志}"
}

print_status() {
  echo
  "$SCRIPT_DIR/status-voice-factory-wsl.sh"
  echo
  echo "日志目录: $LOG_DIR"
}

build_if_needed
start_mysql
start_minio
start_funasr_http
start_funasr_ws
start_tts
start_voiceprint
start_backend
start_nginx
start_natapp
print_status
