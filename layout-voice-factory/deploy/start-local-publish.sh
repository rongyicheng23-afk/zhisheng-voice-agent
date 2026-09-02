#!/bin/bash
set -euo pipefail

PROJECT_ROOT="/Users/rongyicheng/Desktop/voice.baconboat"
FRONTEND_DIR="$PROJECT_ROOT/layout-voice-factory"
BACKEND_DIR="$PROJECT_ROOT/minio-backend/springboot-minio"
FUNASR_DIR="$PROJECT_ROOT/funasr/FunASR"
FUNASR_SERVICE_DIR="$PROJECT_ROOT/services/funasr"
VOICEPRINT_DIR="$PROJECT_ROOT/speech_campplus_sv_zh-cn_16k-common"
DEPLOY_DIR="$FRONTEND_DIR/deploy"
RUNTIME_DIR="$DEPLOY_DIR/runtime"
LOG_DIR="$RUNTIME_DIR/logs"
ENV_FILE="$DEPLOY_DIR/cloudflared.env"
NATAPP_ENV_FILE="$DEPLOY_DIR/natapp.env"
PYTHON_BIN="/Users/rongyicheng/miniconda3/envs/openwebui/bin/python"
TTS_LAUNCHER="$DEPLOY_DIR/run_simple_tts_backend.py"

MYSQL_PASSWORD="${MYSQL_PASSWORD:-12345678}"
MINIO_DATA_DIR="${MINIO_DATA_DIR:-/Users/rongyicheng/minio/data}"
MINIO_PORT="${MINIO_PORT:-19000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-19001}"

NGINX_CONF="$DEPLOY_DIR/nginx.voice-factory.standalone.conf"
NGINX_PID="$RUNTIME_DIR/nginx.pid"
BACKEND_JAR="$BACKEND_DIR/target/springboot-minio-0.0.1-SNAPSHOT.jar"
BACKEND_PID_FILE="$RUNTIME_DIR/backend.pid"
CLOUDFLARED_PID_FILE="$RUNTIME_DIR/cloudflared.pid"
CLOUDFLARED_LOG="$LOG_DIR/cloudflared.log"
NATAPP_PID_FILE="$RUNTIME_DIR/natapp.pid"
NATAPP_LOG="$LOG_DIR/natapp.log"
BACKEND_LOG="$LOG_DIR/backend.log"
BACKEND_SCREEN_SESSION="voice-backend"
NATAPP_SCREEN_SESSION="natapp-voice"
MINIO_SCREEN_SESSION="minio-voice"
MYSQL_MARKER="$RUNTIME_DIR/mysql.started_by_script"

FUNASR_HTTP_PID="$RUNTIME_DIR/funasr-http.pid"
FUNASR_WS_PID="$RUNTIME_DIR/funasr-ws.pid"
TTS_PID="$RUNTIME_DIR/tts.pid"
VOICEPRINT_PID="$RUNTIME_DIR/voiceprint.pid"
MINIO_PID="$RUNTIME_DIR/minio.pid"

PUBLIC_PORT=8088
BACKEND_PORT="${BACKEND_PORT:-18080}"
TUNNEL_RETRY_COUNT=3

BUILD_MODE="${1:-}"

CLOUDFLARE_TUNNEL_MODE="quick"
CLOUDFLARE_TUNNEL_TOKEN=""
PUBLIC_TUNNEL_URL=""
NATAPP_ENABLED="true"
NATAPP_AUTHTOKEN=""
NATAPP_BIN="$DEPLOY_DIR/tools/natapp/natapp"
NATAPP_PUBLIC_URL="http://voice.baconboat.cn"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "缺少命令: $1"
    exit 1
  fi
}

load_cloudflared_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  fi

  CLOUDFLARE_TUNNEL_MODE="${CLOUDFLARE_TUNNEL_MODE:-quick}"
  CLOUDFLARE_TUNNEL_TOKEN="${CLOUDFLARE_TUNNEL_TOKEN:-}"
  PUBLIC_TUNNEL_URL="${PUBLIC_TUNNEL_URL:-}"
}

load_natapp_env() {
  if [[ -f "$NATAPP_ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$NATAPP_ENV_FILE"
    set +a
  fi

  NATAPP_ENABLED="${NATAPP_ENABLED:-true}"
  NATAPP_AUTHTOKEN="${NATAPP_AUTHTOKEN:-}"
  NATAPP_BIN="${NATAPP_BIN:-$DEPLOY_DIR/tools/natapp/natapp}"
  NATAPP_PUBLIC_URL="${NATAPP_PUBLIC_URL:-http://voice.baconboat.cn}"
}

port_in_use() {
  local port="$1"
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    return 0
  fi

  netstat -anv -p tcp 2>/dev/null | awk -v port=".$port" '
    $1 ~ /^tcp/ && $4 ~ port"$" && $6 == "LISTEN" { found=1 }
    END { exit found ? 0 : 1 }
  '
}

process_alive() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1
  else
    return 1
  fi
}

find_natapp_pid() {
  pgrep -f "/opt/natapp/natapp.*-authtoken=${NATAPP_AUTHTOKEN}|$DEPLOY_DIR/tools/natapp/natapp.*-authtoken=${NATAPP_AUTHTOKEN}" | head -n 1 || true
}

extract_tunnel_url() {
  if [[ "$CLOUDFLARE_TUNNEL_MODE" == "named" && -n "$PUBLIC_TUNNEL_URL" ]]; then
    echo "$PUBLIC_TUNNEL_URL"
    return 0
  fi

  if [[ -f "$CLOUDFLARED_LOG" ]]; then
    grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "$CLOUDFLARED_LOG" | grep -v '^https://api\.trycloudflare\.com$' | head -n 1 || true
  fi
}

print_tunnel_failure_hint() {
  [[ -f "$CLOUDFLARED_LOG" ]] || return 0

  if grep -Eq 'authentication failed|invalid token|Unauthorized|403' "$CLOUDFLARED_LOG"; then
    echo "检测到固定 Tunnel 的 token 不可用。"
    echo "请重新从 Cloudflare Zero Trust 复制 token 到 $ENV_FILE。"
    return 0
  fi

  if grep -Eq '114\.114\.114\.114:53:.*timeout|Couldn'\''t resolve SRV record|no such host' "$CLOUDFLARED_LOG"; then
    echo "检测到当前网络 DNS 无法稳定解析 Cloudflare Tunnel 节点。"
    echo "建议先把当前网络 DNS 改成 1.1.1.1 / 8.8.8.8，或直接换手机热点后再试。"
    return 0
  fi

  if grep -Eq 'context deadline exceeded|TLS handshake.*EOF|i/o timeout|failed to request quick Tunnel' "$CLOUDFLARED_LOG"; then
    echo "检测到当前网络到 Cloudflare 边缘连接不稳定。"
    echo "建议换网络后重新执行脚本。"
  fi
}

tunnel_healthy() {
  local url="$1"
  [[ -n "$url" ]] || return 1
  curl -I --max-time 8 -sS "$url" >/dev/null 2>&1
}

wait_for_tunnel_url() {
  local retries=30
  local pid="${1:-}"
  local url=""
  while (( retries > 0 )); do
    if [[ -n "$pid" ]] && ! kill -0 "$pid" >/dev/null 2>&1; then
      return 1
    fi
    url="$(extract_tunnel_url)"
    if [[ -n "$url" ]]; then
      echo "$url"
      return 0
    fi
    sleep 1
    retries=$((retries - 1))
  done
  return 1
}

wait_for_tunnel_ready() {
  local pid="$1"
  local url=""
  local retries=20

  if ! url="$(wait_for_tunnel_url "$pid")"; then
    return 1
  fi

  while (( retries > 0 )); do
    if tunnel_healthy "$url"; then
      echo "$url"
      return 0
    fi
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      return 1
    fi
    sleep 1
    retries=$((retries - 1))
  done

  return 1
}

wait_for_named_tunnel_ready() {
  local pid="$1"
  local retries=30

  if [[ -z "$PUBLIC_TUNNEL_URL" ]]; then
    return 1
  fi

  while (( retries > 0 )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      return 1
    fi
    if tunnel_healthy "$PUBLIC_TUNNEL_URL"; then
      echo "$PUBLIC_TUNNEL_URL"
      return 0
    fi
    sleep 2
    retries=$((retries - 1))
  done

  return 1
}

wait_for_port() {
  local port="$1"
  local retries="${2:-20}"
  while (( retries > 0 )); do
    if port_in_use "$port"; then
      return 0
    fi
    sleep 1
    retries=$((retries - 1))
  done
  return 1
}

natapp_healthy() {
  [[ -n "$NATAPP_PUBLIC_URL" ]] || return 1
  curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "$NATAPP_PUBLIC_URL" 2>/dev/null | grep -Eq '^(200|301|302)$'
}

natapp_tunnel_established() {
  [[ -f "$NATAPP_LOG" ]] || return 1
  grep -Eq 'Tunnel established at (http|https)://' "$NATAPP_LOG"
}

print_natapp_failure_hint() {
  [[ -f "$NATAPP_LOG" ]] || return 0

  if grep -Eq 'errorCode: 1202|当前已欠费' "$NATAPP_LOG"; then
    echo "检测到 NATAPP 返回欠费状态。"
    echo "请先到 NATAPP 后台充值或补足流量后再重新执行脚本。"
    return 0
  fi

  if grep -Eq '标识域名不提供Web访问|Tunnel .* not found' "$NATAPP_LOG"; then
    echo "检测到 NATAPP 隧道已建立，但自定义域名还未完全绑定成功。"
    echo "请确认 NATAPP 后台已绑定 voice.baconboat.cn，且 Cloudflare CNAME 为仅限 DNS。"
  fi
}

wait_for_natapp_ready() {
  local pid="$1"
  local retries=25

  while (( retries > 0 )); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      return 1
    fi
    if natapp_tunnel_established; then
      return 0
    fi
    sleep 2
    retries=$((retries - 1))
  done

  return 1
}

ensure_dirs() {
  mkdir -p "$RUNTIME_DIR" "$LOG_DIR" "$MINIO_DATA_DIR"
}

build_if_needed() {
  if [[ "$BUILD_MODE" == "--build" || ! -f "$FRONTEND_DIR/dist/index.html" ]]; then
    echo "==> 构建前端..."
    require_cmd npm
    (cd "$FRONTEND_DIR" && npm run build)
  fi

  if [[ "$BUILD_MODE" == "--build" || ! -f "$BACKEND_JAR" ]]; then
    echo "==> 打包 Spring Boot..."
    require_cmd mvn
    (cd "$BACKEND_DIR" && mvn -q -DskipTests package)
  fi
}

start_mysql() {
  if port_in_use 3306; then
    echo "==> MySQL 已在端口 3306 运行，跳过启动"
    return 0
  fi

  echo "==> 启动 MySQL (brew services)..."
  brew services start mysql >/dev/null 2>&1 || true

  if wait_for_port 3306 20; then
    touch "$MYSQL_MARKER"
    echo "==> MySQL 已启动"
    return 0
  fi

  echo "  [WARN] MySQL 未在 3306 上监听，Spring Boot 可能无法连接数据库"
}

start_minio() {
  require_cmd minio

  if port_in_use "$MINIO_PORT"; then
    echo "==> MinIO 已在端口 $MINIO_PORT 运行，跳过启动"
    return 0
  fi

  echo "==> 启动 MinIO..."
  require_cmd screen
  screen -S "$MINIO_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
  screen -dmS "$MINIO_SCREEN_SESSION" bash -lc "echo \"\$\$\" > '$MINIO_PID' && exec minio server '$MINIO_DATA_DIR' --address ':$MINIO_PORT' --console-address ':$MINIO_CONSOLE_PORT' >> '$LOG_DIR/minio.log' 2>&1"
  sleep 1

  if ! wait_for_port "$MINIO_PORT" 30; then
    echo "MinIO 启动失败，请检查日志: $LOG_DIR/minio.log"
    exit 1
  fi
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
  (cd "$workdir" && nohup "$@" >"$log_file" 2>&1 & echo $! > "$pid_file")

  if ! wait_for_port "$port" 120; then
    echo "$name 启动失败，请检查日志: $log_file"
    exit 1
  fi
}

start_funasr_http() {
  start_python_service \
    "FunASR HTTP" \
    "8002" \
    "$FUNASR_HTTP_PID" \
    "$LOG_DIR/funasr-http.log" \
    "$FUNASR_DIR" \
    "$PYTHON_BIN" "$FUNASR_SERVICE_DIR/asr_server.py"
}

start_funasr_ws() {
  start_python_service \
    "FunASR WebSocket" \
    "10095" \
    "$FUNASR_WS_PID" \
    "$LOG_DIR/funasr-ws.log" \
    "$FUNASR_SERVICE_DIR" \
    env PYTHONPATH="$FUNASR_DIR${PYTHONPATH:+:$PYTHONPATH}" "$PYTHON_BIN" "funasr_wss_server.py" \
      "--port" "10095" \
      "--device" "cpu" \
      "--ngpu" "0" \
      "--ncpu" "4" \
      "--asr_model" "paraformer-zh" \
      "--asr_model_online" "paraformer-zh-streaming" \
      "--vad_model" "fsmn-vad" \
      "--punc_model" "ct-punc" \
      "--certfile" "" \
      "--keyfile" ""
}

start_tts() {
  start_python_service \
    "TTS" \
    "8003" \
    "$TTS_PID" \
    "$LOG_DIR/tts.log" \
    "$DEPLOY_DIR" \
    "$PYTHON_BIN" "$TTS_LAUNCHER"
}

start_voiceprint() {
  start_python_service \
    "Voiceprint" \
    "8004" \
    "$VOICEPRINT_PID" \
    "$LOG_DIR/voiceprint.log" \
    "$VOICEPRINT_DIR" \
    env SV_SERVICE_PORT=8004 "$PYTHON_BIN" "$VOICEPRINT_DIR/sc_server.py"
}

start_backend() {
  if port_in_use "$BACKEND_PORT"; then
    echo "==> Spring Boot 已在端口 $BACKEND_PORT 运行，跳过启动"
    return 0
  fi

  require_cmd screen
  echo "==> 启动 Spring Boot..."
  screen -S "$BACKEND_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
  screen -dmS "$BACKEND_SCREEN_SESSION" bash -lc "cd '$BACKEND_DIR' && echo \"\$\$\" > '$BACKEND_PID_FILE' && exec env SERVER_PORT='$BACKEND_PORT' java -jar '$BACKEND_JAR' >> '$BACKEND_LOG' 2>&1"

  if ! wait_for_port "$BACKEND_PORT" 45; then
    echo "Spring Boot 启动失败，请检查日志: $BACKEND_LOG"
    exit 1
  fi
}

start_nginx() {
  require_cmd nginx

  if port_in_use "$PUBLIC_PORT"; then
    echo "==> Nginx 已在端口 $PUBLIC_PORT 运行，跳过启动"
    return 0
  fi

  echo "==> 启动 Nginx..."
  nginx -c "$NGINX_CONF"

  if ! wait_for_port "$PUBLIC_PORT" 10; then
    echo "Nginx 启动失败，请检查日志: $LOG_DIR/nginx-error.log"
    exit 1
  fi
}

start_natapp() {
  local pid=""
  local existing_pid=""

  if [[ "$NATAPP_ENABLED" != "true" ]]; then
    echo "==> NATAPP 已禁用，跳过启动"
    return 0
  fi

  if [[ ! -x "$NATAPP_BIN" ]]; then
    echo "缺少 NATAPP 客户端，可执行文件不存在: $NATAPP_BIN"
    echo "请先安装 NATAPP，或修改 $NATAPP_ENV_FILE 里的 NATAPP_BIN"
    exit 1
  fi

  if [[ -z "$NATAPP_AUTHTOKEN" ]]; then
    echo "NATAPP 缺少 authtoken，请编辑: $NATAPP_ENV_FILE"
    exit 1
  fi

  if process_alive "$NATAPP_PID_FILE"; then
    if natapp_healthy; then
      echo "==> NATAPP 已在运行"
      echo "公网地址: $NATAPP_PUBLIC_URL"
      return 0
    fi

    echo "==> 检测到 NATAPP 进程仍在，但公网域名未就绪，准备重启..."
    local old_pid
    old_pid="$(cat "$NATAPP_PID_FILE" 2>/dev/null || true)"
    if [[ -n "$old_pid" ]] && kill -0 "$old_pid" >/dev/null 2>&1; then
      kill "$old_pid" >/dev/null 2>&1 || true
      sleep 1
    fi
    rm -f "$NATAPP_PID_FILE"
  fi

  existing_pid="$(find_natapp_pid)"
  if [[ -n "$existing_pid" ]] && kill -0 "$existing_pid" >/dev/null 2>&1; then
    echo "==> 检测到 NATAPP 已在运行 (PID $existing_pid)，复用现有进程"
    echo "$existing_pid" > "$NATAPP_PID_FILE"
    if natapp_healthy; then
      echo "公网地址: $NATAPP_PUBLIC_URL"
      return 0
    fi
    if [[ -f "$NATAPP_LOG" ]] && natapp_tunnel_established; then
      echo "  [WARN] NATAPP 隧道在线，但自定义域名还未通过健康检查。"
      echo "  [WARN] 请确认 NATAPP 后台已绑定 voice.baconboat.cn，且 Cloudflare CNAME 仍指向 NATAPP 域名。"
      echo "公网地址: $NATAPP_PUBLIC_URL"
      return 0
    fi
    echo "  [WARN] 现有 NATAPP 进程未通过健康检查，准备重启..."
    kill "$existing_pid" >/dev/null 2>&1 || true
    sleep 1
    rm -f "$NATAPP_PID_FILE"
  fi

  echo "==> 启动 NATAPP..."
  rm -f "$NATAPP_LOG"
  require_cmd screen
  screen -S "$NATAPP_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
  screen -dmS "$NATAPP_SCREEN_SESSION" bash -lc "echo \"\$\$\" > '$NATAPP_PID_FILE' && exec '$NATAPP_BIN' -log=stdout -loglevel=INFO -authtoken='$NATAPP_AUTHTOKEN' >> '$NATAPP_LOG' 2>&1"
  sleep 1
  pid="$(cat "$NATAPP_PID_FILE" 2>/dev/null || true)"

  if ! wait_for_natapp_ready "$pid"; then
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      sleep 1
    fi
    rm -f "$NATAPP_PID_FILE"
    screen -S "$NATAPP_SCREEN_SESSION" -X quit >/dev/null 2>&1 || true
    echo "NATAPP 启动后未建立隧道。"
    echo "请检查日志: $NATAPP_LOG"
    print_natapp_failure_hint
    exit 1
  fi

  if ! natapp_healthy; then
    echo "  [WARN] NATAPP 隧道已建立，但自定义域名还未通过健康检查。"
    echo "  [WARN] 你仍可先查看日志，并确认 NATAPP 域名绑定和 Cloudflare CNAME 是否已完全生效。"
  fi
  echo "公网地址: $NATAPP_PUBLIC_URL"
}

start_cloudflared() {
  local cloudflared_bin="/opt/homebrew/opt/cloudflared/bin/cloudflared"
  local url=""
  local pid=""
  local attempt=1
  if [[ ! -x "$cloudflared_bin" ]]; then
    echo "缺少 cloudflared，可执行文件不存在: $cloudflared_bin"
    exit 1
  fi

  if [[ "$CLOUDFLARE_TUNNEL_MODE" == "named" ]]; then
    if [[ -z "$CLOUDFLARE_TUNNEL_TOKEN" || -z "$PUBLIC_TUNNEL_URL" ]]; then
      echo "固定域名模式缺少配置，请编辑: $ENV_FILE"
      echo "至少需要:"
      echo "  CLOUDFLARE_TUNNEL_MODE=named"
      echo "  CLOUDFLARE_TUNNEL_TOKEN=你的token"
      echo "  PUBLIC_TUNNEL_URL=https://voice.你的域名"
      exit 1
    fi
  fi

  if process_alive "$CLOUDFLARED_PID_FILE"; then
    local current_url
    current_url="$(extract_tunnel_url)"
    if [[ -n "$current_url" ]] && tunnel_healthy "$current_url"; then
      echo "==> cloudflared 已在运行"
      echo "公网地址: $current_url"
      return 0
    fi

    echo "==> 检测到 cloudflared 进程仍在，但公网 tunnel 已失效，准备重启..."
    local old_pid
    old_pid="$(cat "$CLOUDFLARED_PID_FILE" 2>/dev/null || true)"
    if [[ -n "$old_pid" ]] && kill -0 "$old_pid" >/dev/null 2>&1; then
      kill "$old_pid" >/dev/null 2>&1 || true
      sleep 1
    fi
    rm -f "$CLOUDFLARED_PID_FILE"
  fi

  while (( attempt <= TUNNEL_RETRY_COUNT )); do
    if [[ "$CLOUDFLARE_TUNNEL_MODE" == "named" ]]; then
      echo "==> 启动 Cloudflare Named Tunnel (http2)... [尝试 $attempt/$TUNNEL_RETRY_COUNT]"
    else
      echo "==> 启动 Cloudflare Quick Tunnel (http2)... [尝试 $attempt/$TUNNEL_RETRY_COUNT]"
    fi
    rm -f "$CLOUDFLARED_LOG"
    if [[ "$CLOUDFLARE_TUNNEL_MODE" == "named" ]]; then
      nohup "$cloudflared_bin" tunnel run --protocol http2 --token "$CLOUDFLARE_TUNNEL_TOKEN" >"$CLOUDFLARED_LOG" 2>&1 &
    else
      nohup "$cloudflared_bin" tunnel --protocol http2 --url "http://localhost:$PUBLIC_PORT" >"$CLOUDFLARED_LOG" 2>&1 &
    fi
    pid="$!"
    echo "$pid" > "$CLOUDFLARED_PID_FILE"

    if [[ "$CLOUDFLARE_TUNNEL_MODE" == "named" ]]; then
      url="$(wait_for_named_tunnel_ready "$pid" || true)"
    else
      url="$(wait_for_tunnel_ready "$pid" || true)"
    fi

    if [[ -n "$url" ]]; then
      echo "公网地址: $url"
      return 0
    fi

    echo "  [WARN] 第 $attempt 次启动 tunnel 失败，准备重试..."
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      sleep 1
    fi
    rm -f "$CLOUDFLARED_PID_FILE"
    attempt=$((attempt + 1))
  done

  echo "Cloudflare Tunnel 连续 $TUNNEL_RETRY_COUNT 次启动失败。"
  echo "请检查日志: $CLOUDFLARED_LOG"
  echo "这通常是当前网络到 Cloudflare 的 DNS / TLS / 边缘连接不稳定，建议换网络后再试。"
  print_tunnel_failure_hint
  exit 1
}

print_service_status() {
  echo
  echo "==> 服务状态"
  for service in \
    "MySQL:3306" \
    "FunASR HTTP:8002" \
    "FunASR WS:10095" \
    "TTS:8003" \
    "Voiceprint:8004" \
    "MinIO:$MINIO_PORT" \
    "Spring Boot:$BACKEND_PORT" \
    "Nginx:8088"
  do
    local label="${service%%:*}"
    local port="${service##*:}"
    if port_in_use "$port"; then
      echo "  [OK] $label 已运行 ($port)"
    else
      echo "  [WARN] $label 未监听端口 $port"
    fi
  done
}

main() {
  ensure_dirs
  load_cloudflared_env
  load_natapp_env
  require_cmd java
  require_cmd brew
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
  print_service_status

  echo
  echo "==> 本机统一入口"
  echo "  本地地址: http://localhost:$PUBLIC_PORT"
  echo "  公网地址: $NATAPP_PUBLIC_URL"
  echo "  Spring Boot 日志: $BACKEND_LOG"
  echo "  Nginx 日志: $LOG_DIR/nginx-error.log"
  echo "  NATAPP 日志: $NATAPP_LOG"
  echo
  echo "如果刚改过前端/后端代码，建议用:"
  echo "  $0 --build"
}

main "$@"
