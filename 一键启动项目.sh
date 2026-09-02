#!/bin/bash
set -euo pipefail

PROJECT_ROOT="/Users/rongyicheng/Desktop/voice.baconboat"
DEPLOY_DIR="$PROJECT_ROOT/layout-voice-factory/deploy"
SESSION_NAME="voice-baconboat"
START_LOG="$DEPLOY_DIR/runtime/logs/one-click-start.log"

mkdir -p "$DEPLOY_DIR/runtime/logs"

echo "==> 准备启动智能语音交互平台..."
echo "项目目录: $PROJECT_ROOT"

if ! command -v screen >/dev/null 2>&1; then
  echo "缺少 screen 命令，无法托管后台服务。"
  exit 1
fi

if screen -list | grep -q "[.]$SESSION_NAME[[:space:]]"; then
  echo "==> 检测到已有后台会话，先关闭旧会话..."
  screen -S "$SESSION_NAME" -X quit >/dev/null 2>&1 || true
  sleep 1
fi

echo "==> 清理可能残留的旧服务..."
"$DEPLOY_DIR/stop-local-publish.sh" >/dev/null 2>&1 || true

echo "==> 启动后台服务..."
screen -dmS "$SESSION_NAME" bash -lc "
  '$DEPLOY_DIR/start-local-publish.sh' > '$START_LOG' 2>&1
  echo '' >> '$START_LOG'
  echo '==> 后台守护中，请不要手动结束 screen 会话。' >> '$START_LOG'
  sleep 86400
"

echo "==> 等待服务启动，这一步通常需要 20-60 秒..."
for _ in {1..90}; do
  if "$DEPLOY_DIR/status-local-publish.sh" | grep -q "公网状态: HEALTHY"; then
    break
  fi
  sleep 2
done

echo
"$DEPLOY_DIR/status-local-publish.sh"
echo
echo "==> 启动日志:"
echo "$START_LOG"
echo
echo "本地入口: http://localhost:8088"
echo "公网入口: http://voice.baconboat.cn"
echo
echo "停止项目可运行:"
echo "$DEPLOY_DIR/stop-local-publish.sh"
echo "screen -S $SESSION_NAME -X quit"
