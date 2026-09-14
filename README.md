# Zhisheng Voice Agent

智声大模型流式交互系统的现有工程基础。项目包含实时语音识别、语音合成、声纹分析、智能会议纪要、前端控制台和 Java 业务服务，并将在此基础上建设大模型增量话术生成、流式语音合成与可打断交互链路。

## 项目结构

- `layout-voice-factory/`：Vue 3 与 TypeScript 前端
- `minio-backend/springboot-minio/`：Spring Boot 业务后端
- `services/funasr/`：项目使用的 FunASR 服务适配文件
- `funasr/FunASR/`：FunASR 上游依赖
- `layout-tts-0.22.0/`：本地语音合成服务基础
- `speech_campplus_sv_zh-cn_16k-common/`：说话人识别服务与配置
- `docs/`：设计和开发文档

## 本地配置

当前优化工作区使用根目录 `启动项目.command` / `停止项目.command`。命令行可运行：

```bash
bash scripts/local-run.sh start
bash scripts/local-run.sh status
bash scripts/local-run.sh stop
```

运行器是本机环境，不随源码发布：将 `ZHISHENG_RUNNER` 指向配置好的 `manage.py`，或建立 `.local-run` 到该运行器目录的链接。脚本明确将当前仓库作为 `ZHISHENG_PROJECT`，不会再启动另一份旧解压源码。停止不会关闭共享 MySQL，也不会删除音频与历史。

初次在新机器运行仍需安装 Java、Node、Python、MySQL、MinIO、模型及各服务依赖；此入口不代表一键自动配置任意云服务器。旧 `一键启动项目.sh` 与 `启动voice.baconboat项目.command` 属于原团队公网发布流程，不用于本次本地环境。

## 登录与隐私变更

- `JWT_SECRET` 至少 32 个 UTF-8 字节。未设置时开发环境生成进程内随机密钥，重启后必须重新登录；不再接受原硬编码密钥签发的令牌。
- 新密码使用带随机盐的 PBKDF2，旧 MD5 密码在成功登录时自动升级，**不改变用户输入的密码**。升级后旧版仅支持 MD5 的后端不能验证这些账号，回退需保留新密码验证器。
- `/api/users` 仅返回当前账号；用户资料、上传与下载按资源所属用户校验，不提供未经实现的“管理员查看所有用户”权限。
- 实时 WebSocket 默认不保存录音/转写。页面勾选保存后，该次连接才创建历史；上传文件型 ASR、TTS、会议等原有保存流程不受此开关控制。
- 实时连接单 JVM 总数最多 20、单用户最多 2；单次 PCM 最多 20 MiB、空闲 30 秒、最长 30 分钟。触及限制会断开，不保证供应商任务已取消。
- 登录/注册采用直连 IP 每分钟 30 次的单 JVM 防滥用限制。反向代理后须结合可信代理和网关限流设计，否则代理后的用户可能共享额度；多实例仍需共享限流/票据存储。

## 云部署前置条件

启用 `SPRING_PROFILES_ACTIVE=prod`，显式配置 `JWT_SECRET`、`MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`。生产模式禁止自动初始化数据库，需备份并审核后单独执行迁移。

HTTP 与 WebSocket 共用 `REALTIME_ALLOWED_ORIGINS` 精确来源白名单（如 `https://your-domain.example`）。TLS/WSS、反向代理、私有桶、查询串日志脱敏、进程守护及云端容量测试仍需结合实际服务器配置。本项目尚不应直接暴露默认测试账号与模型端口到公网。

## 验证与未完成项

见 `docs/consolidated-improvements.md`。流式问答供应商接入、授权知识检索、全链路打断和真实标注集评估尚未全部交付，不能把组件测试当作完整演示验收。

运行时密钥、隧道令牌、证书、模型权重、数据库数据和构建产物不会提交到仓库。部署前请根据各目录中的示例配置准备本地环境。

## 第三方组件

本项目使用 FunASR、CAM++、Coqui TTS、Vue、Spring Boot 等第三方开源项目。各组件版权和许可证归原作者所有；修改和发布时应继续遵守对应许可证。
