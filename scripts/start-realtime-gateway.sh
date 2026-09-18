#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "$0")/.." && pwd)"
if [[ -z "${REALTIME_GATEWAY_INTERNAL_KEY:-}" || ! -f "${REALTIME_TTS_REFERENCE:-}" ]]; then
  echo '请先配置 REALTIME_GATEWAY_INTERNAL_KEY 和 REALTIME_TTS_REFERENCE（获授权的参考音频路径）。'
  echo 'Spring Boot 必须使用同一个 REALTIME_GATEWAY_INTERNAL_KEY 启动。'
  exit 1
fi
if [[ -z "${DEEPSEEK_API_KEY:-}" ]]; then
  echo '未配置 DeepSeek：资料原文模式可用，通用模型回答不可用。'
fi
cd "$PROJECT_DIR"
exec "${REALTIME_PYTHON:-python3}" -m uvicorn services.realtime.gateway:app --host 127.0.0.1 --port 18081 --ws-max-size 16384
