# 与 feature/frank-dev 的开发边界

2026-09-13，已 fetch 对方分支，核对提交 f5022c5d4ff3d53751377ffc846b098738316606。
本地 HEAD 为 9ce9d0d，已有集中改进尚未提交。未 merge、checkout 或 push。

## 本轮改进

只修改独立的 services/evaluation，并新增 scripts/check_branch_overlap.py、其测试及本文档。
这些路径在已核对的对方分支变更中不存在。没有改动 DeepSeek 适配器、网关、票据接口或前端 WebSocket。

会议评估增加多录音汇总：输入对象的 recordings 数组，每项仍为 durationSeconds、reference、hypothesis。
按累计参考发言人秒数计算总体 DER，不简单平均每场百分比；各场独立匹配匿名标签。
无参考发言的录音仍累计误报；全部无参考发言时 DER 为 null。
补充布尔时间、缺失字段和无效录音集合的输入校验。

```bash
python services/evaluation/diarization.py corpus.json
python -m unittest discover -s services/evaluation -p 'test_*.py' -v
python -m unittest scripts.test_branch_overlap -v
python scripts/check_branch_overlap.py origin/feature/frank-dev
```

评估需 NumPy/SciPy；不调用模型、不上传录音。14 项评估测试均为构造样本，不能代替真实会议质量验收。

## 已存在的重叠，不是本轮新增

只读检查涵盖共同祖先之后的提交、暂存、未暂存和未忽略的新文件，重命名按删除和新增统计。
退出码 0：无同路径变更；1：有重叠、需要审核；2：Git 引用或共同祖先检查失败。
工具不自动 fetch，不执行合并，也不写入暂存区。应先更新目标远程引用再运行。
同路径变更不等于 Git 一定冲突；无同路径变更也不代表跨模块语义兼容。

当前发现 10 个重叠路径：

- .gitignore
- layout-tts-0.22.0/layout/simple_tts_backend.py
- layout-voice-factory/src/libs/websocket-client.ts
- minio-backend/springboot-minio/src/main/java/com/wc/config/WebConfig.java
- minio-backend/springboot-minio/src/main/java/com/wc/controller/UserInfoController.java
- minio-backend/springboot-minio/src/main/java/com/wc/funasr/websocket/FunasrRealtimeProxyHandler.java
- minio-backend/springboot-minio/src/main/java/com/wc/funasr/websocket/FunasrWebSocketConfig.java
- minio-backend/springboot-minio/src/main/java/com/wc/realtime/RealtimeTicketController.java
- minio-backend/springboot-minio/src/main/java/com/wc/utils/JwtUtil.java
- services/realtime/__init__.py

## 整合前必须确认

1. 建议对方维护 DeepSeek/gateway/semantic_segmenter；本分支继续会议分组和离线评估。该分工尚未获对方确认。
2. 票据 URL、一次性消费、Origin、身份字段需统一，不能保留两个同名 Controller 实现后直接合并。
3. 网关当前在 LLM 文本结束时发 turn.completed，未来接音频时需统一成真实播放结束；tts.segment_ready 不是已合成音频。
4. JWT、安全校验和用户资源访问控制不得在解决文本冲突时退回旧逻辑。
5. 整合必须在保留当前未提交工作的基础上进行，自检、用户确认后才提交/推送；本轮没有宣称整条分支已无冲突。

## 本地提交授权

2026-09-13 用户授权提交合适的改动。会议评估、重叠检查及会议校正改进分开纳入本地提交；既有鉴权、安全和实时链路集中改进仍保留在工作区，未混入此次提交。此次不推送远程，不合并对方分支；上文未提交状态为检查当时的记录。
