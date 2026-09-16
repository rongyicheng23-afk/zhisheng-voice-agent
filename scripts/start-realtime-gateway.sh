#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "$0")/.." && pwd)"
if [[ -z "${DEEPSEEK_API_KEY:-}" || -z "${REALTIME_GATEWAY_INTERNAL_KEY:-}" || ! -f "${REALTIME_TTS_REFERENCE:-}" ]]; then
  echo '请先配置 DEEPSEEK_API_KEY、REALTIME_GATEWAY_INTERNAL_KEY 和 REALTIME_TTS_REFERENCE（获授权的参考音频路径）。'
  echo 'Spring Boot 必须使用同一个 REALTIME_GATEWAY_INTERNAL_KEY 启动。'
  exit 1
fi
cd "$PROJECT_DIR"
exec "${REALTIME_PYTHON:-python3}" -m uvicorn services.realtime.gateway:app --host 127.0.0.1 --port 18081 --ws-max-size 16384
