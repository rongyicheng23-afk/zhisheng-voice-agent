#!/bin/bash
PROJECT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
bash "$PROJECT_DIR/scripts/local-run.sh" stop
result=$?
read -r -p '按回车关闭窗口。'
exit "$result"
