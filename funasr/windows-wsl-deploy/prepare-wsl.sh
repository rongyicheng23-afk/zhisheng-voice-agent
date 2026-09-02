#!/usr/bin/env bash
set -euo pipefail

echo "==> 安装基础依赖..."
sudo apt update
sudo apt install -y \
  nginx \
  mysql-server \
  openjdk-17-jdk \
  maven \
  nodejs \
  npm \
  python3 \
  python3-pip \
  python3-venv \
  curl \
  unzip \
  lsof \
  net-tools \
  ffmpeg

echo "==> 准备 MinIO..."
if ! command -v minio >/dev/null 2>&1; then
  curl -L "https://dl.min.io/server/minio/release/linux-amd64/minio" -o /tmp/minio
  chmod +x /tmp/minio
  sudo mv /tmp/minio /usr/local/bin/minio
fi

echo "==> 准备项目目录..."
mkdir -p "$HOME/voice-factory"/{frontend,backend,funasr,voiceprint,deploy,database,minio-data,runtime/logs}

echo
echo "基础环境已安装。下一步："
echo "1. 把 Mac 上的项目文件拷到: $HOME/voice-factory"
echo "2. cp app.env.example app.env，然后按实际路径和密码修改 app.env"
echo "3. 执行 ./import-db.sh 初始化数据库"
echo "4. 执行 ./start-voice-factory-wsl.sh --build 启动"
