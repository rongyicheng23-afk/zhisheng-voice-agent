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

运行时密钥、隧道令牌、证书、模型权重、数据库数据和构建产物不会提交到仓库。部署前请根据各目录中的示例配置准备本地环境。

## 第三方组件

本项目使用 FunASR、CAM++、Coqui TTS、Vue、Spring Boot 等第三方开源项目。各组件版权和许可证归原作者所有；修改和发布时应继续遵守对应许可证。

