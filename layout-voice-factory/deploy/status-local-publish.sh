#!/bin/bash
set -euo pipefail

PROJECT_ROOT="/Users/rongyicheng/Desktop/voice.baconboat"
FRONTEND_DIR="$PROJECT_ROOT/layout-voice-factory"
DEPLOY_DIR="$FRONTEND_DIR/deploy"
RUNTIME_DIR="$DEPLOY_DIR/runtime"
LOG_DIR="$RUNTIME_DIR/logs"
ENV_FILE="$DEPLOY_DIR/cloudflared.env"
NATAPP_ENV_FILE="$DEPLOY_DIR/natapp.env"

BACKEND_PID_FILE="$RUNTIME_DIR/backend.pid"
CLOUDFLARED_PID_FILE="$RUNTIME_DIR/cloudflared.pid"
NATAPP_PID_FILE="$RUNTIME_DIR/natapp.pid"
NGINX_PID_FILE="$RUNTIME_DIR/nginx.pid"
FUNASR_HTTP_PID="$RUNTIME_DIR/funasr-http.pid"
FUNASR_WS_PID="$RUNTIME_DIR/funasr-ws.pid"
TTS_PID="$RUNTIME_DIR/tts.pid"
VOICEPRINT_PID="$RUNTIME_DIR/voiceprint.pid"
MINIO_PID="$RUNTIME_DIR/minio.pid"
CLOUDFLARED_LOG="$LOG_DIR/cloudflared.log"
NATAPP_LOG="$LOG_DIR/natapp.log"
MINIO_PORT="${MINIO_PORT:-19000}"

CLOUDFLARE_TUNNEL_MODE="quick"
PUBLIC_TUNNEL_URL=""
NATAPP_ENABLED="true"
NATAPP_PUBLIC_URL="http://voice.baconboat.cn"

find_natapp_pid() {
  pgrep -f "/opt/natapp/natapp|$DEPLOY_DIR/tools/natapp/natapp" | head -n 1 || true
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

load_cloudflared_env() {
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  fi

  CLOUDFLARE_TUNNEL_MODE="${CLOUDFLARE_TUNNEL_MODE:-quick}"
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
  NATAPP_PUBLIC_URL="${NATAPP_PUBLIC_URL:-http://voice.baconboat.cn}"
}

print_pid_status() {
  local name="$1"
  local pid_file="$2"
  local port="${3:-}"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      echo "$name: RUNNING (PID $pid)"
      return 0
    fi
  fi
  if [[ -n "$port" ]] && port_in_use "$port"; then
    echo "$name: RUNNING (手动/外部启动，端口 $port)"
    return 0
  fi
  echo "$name: STOPPED"
}

print_natapp_status() {
  if [[ "$NATAPP_ENABLED" != "true" ]]; then
    echo "NATAPP: DISABLED"
    return 0
  fi

  if [[ -f "$NATAPP_PID_FILE" ]]; then
    local pid
    pid="$(cat "$NATAPP_PID_FILE" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      echo "NATAPP: RUNNING (PID $pid)"
      return 0
    fi
  fi

  local detected_pid
  detected_pid="$(find_natapp_pid)"
  if [[ -n "$detected_pid" ]] && kill -0 "$detected_pid" >/dev/null 2>&1; then
    echo "NATAPP: RUNNING (手动/外部启动，PID $detected_pid)"
    return 0
  fi

  echo "NATAPP: STOPPED"
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

tunnel_healthy() {
  local url="$1"
  [[ -n "$url" ]] || return 1
  curl -I --max-time 8 -sS "$url" >/dev/null 2>&1
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

natapp_healthy() {
  [[ "$NATAPP_ENABLED" == "true" ]] || return 1
  [[ -n "$NATAPP_PUBLIC_URL" ]] || return 1
  curl -sS --max-time 10 -o /dev/null -w "%{http_code}" "$NATAPP_PUBLIC_URL" 2>/dev/null | grep -Eq '^(200|301|302)$'
}

print_tunnel_diagnosis() {
  [[ -f "$CLOUDFLARED_LOG" ]] || return 0

  if grep -Eq 'authentication failed|invalid token|Unauthorized|403' "$CLOUDFLARED_LOG"; then
    echo "诊断: 固定 Tunnel 的 token 不可用。"
    echo "建议: 重新从 Cloudflare Zero Trust 复制 token 到 $ENV_FILE。"
    return 0
  fi

  if grep -Eq '114\.114\.114\.114:53:.*timeout|Couldn'\''t resolve SRV record|no such host' "$CLOUDFLARED_LOG"; then
    echo "诊断: 当前网络的 DNS 无法稳定解析 Cloudflare Tunnel 节点。"
    echo "建议: 把当前网络 DNS 改成 1.1.1.1 / 8.8.8.8，或直接换手机热点后重试。"
    return 0
  fi

  if grep -Eq 'context deadline exceeded|TLS handshake.*EOF|i/o timeout|failed to request quick Tunnel' "$CLOUDFLARED_LOG"; then
    echo "诊断: 当前网络到 Cloudflare 边缘连接不稳定，Quick Tunnel 建立失败。"
    echo "建议: 换网络后重新执行 start-local-publish.sh。"
  fi
}

print_natapp_diagnosis() {
  [[ -f "$NATAPP_LOG" ]] || return 0

  if grep -Eq 'errorCode: 1202|当前已欠费' "$NATAPP_LOG"; then
    echo "诊断: NATAPP 当前已欠费或流量不足。"
    echo "建议: 登录 NATAPP 后台完成充值后，再重新执行 start-local-publish.sh。"
    return 0
  fi

  if grep -Eq 'Tunnel .* not found|标识域名不提供Web访问' "$NATAPP_LOG"; then
    echo "诊断: NATAPP 自定义域名尚未完全绑定成功。"
    echo "建议: 确认 NATAPP 后台绑定的是 voice.baconboat.cn，Cloudflare 中该记录为 CNAME 且仅限 DNS。"
  fi
}

load_cloudflared_env
load_natapp_env

echo "=== 进程状态 ==="
print_pid_status "MySQL" "/dev/null" "3306"
print_pid_status "MinIO" "$MINIO_PID" "$MINIO_PORT"
print_pid_status "FunASR HTTP" "$FUNASR_HTTP_PID" "8002"
print_pid_status "FunASR WebSocket" "$FUNASR_WS_PID" "10095"
print_pid_status "TTS" "$TTS_PID" "8003"
print_pid_status "Voiceprint" "$VOICEPRINT_PID" "8004"
print_pid_status "Spring Boot" "$BACKEND_PID_FILE" "18080"
print_pid_status "Nginx" "$NGINX_PID_FILE" "8088"
if [[ "$NATAPP_ENABLED" == "true" ]]; then
  print_natapp_status
else
  print_pid_status "cloudflared" "$CLOUDFLARED_PID_FILE"
fi

echo
echo "=== 端口状态 ==="
for port in 3306 "$MINIO_PORT" 8002 10095 8003 8004 18080 8088; do
  if port_in_use "$port"; then
    echo "PORT $port: LISTENING"
  else
    echo "PORT $port: NOT LISTENING"
  fi
done

echo
echo "=== 地址 ==="
echo "本地入口: http://localhost:8088"
if [[ "$NATAPP_ENABLED" == "true" ]]; then
  echo "公网方案: NATAPP 自定义域名"
  echo "公网地址: $NATAPP_PUBLIC_URL"
  if process_alive "$NATAPP_PID_FILE" || [[ -n "$(find_natapp_pid)" ]]; then
    if natapp_healthy; then
      echo "公网状态: HEALTHY"
    else
      echo "公网状态: UNHEALTHY (请检查 $NATAPP_LOG)"
      print_natapp_diagnosis
    fi
  else
    echo "公网状态: 已失效 (NATAPP 未运行)"
    print_natapp_diagnosis
  fi
else
  echo "Tunnel 模式: $CLOUDFLARE_TUNNEL_MODE"
  TUNNEL_URL="$(extract_tunnel_url)"
  if process_alive "$CLOUDFLARED_PID_FILE" && [[ -n "$TUNNEL_URL" ]]; then
    echo "公网地址: $TUNNEL_URL"
    if tunnel_healthy "$TUNNEL_URL"; then
      echo "公网状态: HEALTHY"
    else
      echo "公网状态: UNHEALTHY (建议重新运行 start-local-publish.sh)"
      print_tunnel_diagnosis
    fi
  elif [[ -n "$TUNNEL_URL" ]]; then
    echo "上次生成地址: $TUNNEL_URL"
    echo "公网状态: 已失效 (cloudflared 未运行)"
    print_tunnel_diagnosis
  else
    echo "公网地址: 未获取到，请检查 $CLOUDFLARED_LOG"
    print_tunnel_diagnosis
  fi
fi
