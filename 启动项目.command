#!/bin/bash
PROJECT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
bash "$PROJECT_DIR/scripts/local-run.sh" start
result=$?
if [[ $result -eq 0 ]]; then open 'http://127.0.0.1:8081/'; fi
read -r -p '按回车关闭窗口，后台服务继续运行。'
exit "$result"
