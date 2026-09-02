# 智能语音 SaaS 平台 Windows 台式机迁移说明

这套目录和脚本用于把你当前在 Mac 上运行的项目迁到另一台 Windows 台式机上。推荐方式是：

```text
Windows 11 + WSL2 Ubuntu 22.04
```

不要优先走原生 Windows 分别安装 Java、Python、Nginx、MySQL 的路线，后续维护会更乱。

## 一、迁移目标

把当前项目迁移到 Win 台式机后，形成这套可运行链路：

```text
浏览器
  -> Nginx :8088
  -> Spring Boot :8080
  -> MySQL / MinIO
  -> FunASR :8002 / 10095
  -> TTS :8003
  -> Voiceprint :8004
  -> NATAPP / voice.baconboat.cn
```

## 二、Win 台式机执行清单

### 1. 安装 WSL2

在 Windows 管理员 PowerShell 执行：

```powershell
wsl --install -d Ubuntu-22.04
```

重启后打开 Ubuntu，设置用户名和密码。

### 2. 安装基础依赖

在 Ubuntu 执行：

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk maven nginx mysql-server python3 python3-pip python3-venv curl wget unzip lsof screen
```

检查：

```bash
java -version
mvn -v
python3 --version
nginx -v
mysql --version
```

### 3. 拷贝项目文件

建议最终目录：

```text
~/voice.baconboat/
├── layout-voice-factory/
├── minio-backend/
├── funasr/
├── layout-tts-0.22.0/
├── speech_campplus_sv_zh-cn_16k-common/
├── minio_schema_only.sql
└── deploy-runtime-data/
```

如果你先把文件放到 Windows 桌面，例如：

```text
C:\Users\你的Windows用户名\Desktop\voice.baconboat
```

那么在 WSL 执行：

```bash
mkdir -p ~/voice.baconboat
cp -r /mnt/c/Users/你的Windows用户名/Desktop/voice.baconboat/* ~/voice.baconboat/
```

检查：

```bash
find ~/voice.baconboat -maxdepth 2 -type d | sort
```

## 三、必须拷的文件

### 必须拷

1. 前端目录

```text
layout-voice-factory
```

2. 后端目录

```text
minio-backend
```

3. FunASR 目录

```text
funasr
```

4. TTS 目录

```text
layout-tts-0.22.0
```

5. 声纹目录

```text
speech_campplus_sv_zh-cn_16k-common
```

6. 数据库结构文件

```text
minio_schema_only.sql
```

### 可以不拷

这些可以先不带，减小体积：

- `node_modules/`
- `dist/`，除非你想跳过前端重建
- `target/`，除非你想跳过后端重打包
- `.idea/`
- `.vscode/`
- `.git/`
- `runtime/`
- 各类日志文件
- `__pycache__/`
- `.pytest_cache/`
- 比赛提交材料、PPT、视频

## 四、初始化部署脚本

把 `windows-wsl-deploy` 目录本身也拷过去，例如放到：

```text
~/voice.baconboat/windows-wsl-deploy
```

执行：

```bash
cd ~/voice.baconboat/windows-wsl-deploy
chmod +x *.sh
cp app.env.example app.env
```

然后编辑：

```bash
nano app.env
```

## 五、必须改的配置

这些必须按 Win 台式机实际路径确认：

### 1. `app.env`

重点确认：

```bash
APP_HOME="$HOME/voice.baconboat"
FRONTEND_DIR="$APP_HOME/layout-voice-factory"
BACKEND_DIR="$APP_HOME/minio-backend/springboot-minio"
FUNASR_DIR="$APP_HOME/funasr/FunASR"
VOICEPRINT_DIR="$APP_HOME/speech_campplus_sv_zh-cn_16k-common"
SCHEMA_SQL="$APP_HOME/minio_schema_only.sql"
```

### 2. Python 解释器

如果你后面建了虚拟环境，要把：

```bash
PYTHON_BIN="python3"
```

改成例如：

```bash
PYTHON_BIN="$HOME/voice-baconboat-venv/bin/python"
```

### 3. NATAPP 配置

```bash
NATAPP_AUTHTOKEN="你的 NATAPP token"
NATAPP_BIN="/opt/natapp/natapp"
NATAPP_PUBLIC_URL="http://voice.baconboat.cn"
```

## 六、安装 Python 依赖

建议先建统一虚拟环境：

```bash
python3 -m venv ~/voice-baconboat-venv
source ~/voice-baconboat-venv/bin/activate
pip install --upgrade pip setuptools wheel
```

安装主要依赖：

```bash
pip install funasr modelscope "huggingface_hub<=0.25.2" uvicorn fastapi python-multipart websockets jieba
pip install modelscope fastapi uvicorn python-multipart librosa soundfile
pip install TTS flask
```

## 七、安装 MinIO

```bash
wget https://dl.min.io/server/minio/release/linux-amd64/minio
chmod +x minio
sudo mv minio /usr/local/bin/minio
minio --version
mkdir -p ~/voice.baconboat/deploy-runtime-data/minio-data
```

## 八、初始化 MySQL

```bash
sudo service mysql start
sudo mysql
```

进入 MySQL 后执行：

```sql
CREATE DATABASE voice_factory DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'voice_user'@'localhost' IDENTIFIED BY '12345678';
CREATE USER 'voice_user'@'%' IDENTIFIED BY '12345678';
GRANT ALL PRIVILEGES ON voice_factory.* TO 'voice_user'@'localhost';
GRANT ALL PRIVILEGES ON voice_factory.* TO 'voice_user'@'%';
FLUSH PRIVILEGES;
EXIT;
```

然后导入结构：

```bash
cd ~/voice.baconboat/windows-wsl-deploy
./import-db.sh
```

## 九、构建前后端

前端：

```bash
cd ~/voice.baconboat/layout-voice-factory
npm install
npm run build
```

后端：

```bash
cd ~/voice.baconboat/minio-backend/springboot-minio
mvn -DskipTests package
```

## 十、启动项目

第一次建议：

```bash
cd ~/voice.baconboat/windows-wsl-deploy
./start-voice-factory-wsl.sh --build
```

查看状态：

```bash
./status-voice-factory-wsl.sh
```

停止服务：

```bash
./stop-voice-factory-wsl.sh
```

## 十一、访问地址

本地：

```text
http://localhost:8088
```

公网：

```text
http://voice.baconboat.cn
```

## 十二、建议的排错顺序

如果打不开，不要一上来查公网，先按这个顺序排：

1. `MySQL` 是否正常
2. `MinIO` 是否正常
3. `FunASR / TTS / Voiceprint` 是否正常
4. `Spring Boot` 是否监听 `8080`
5. `Nginx` 是否监听 `8088`
6. 本地 `http://localhost:8088` 是否能开
7. 最后再查 `NATAPP` 和域名

## 十三、最简落地顺序

如果你想最快迁成功，就按这个顺序：

1. Win 装 WSL2
2. 拷目录到 `~/voice.baconboat`
3. 装依赖
4. 导入 MySQL
5. 本地跑通 `http://localhost:8088`
6. 最后接 NATAPP 和 `voice.baconboat.cn`

