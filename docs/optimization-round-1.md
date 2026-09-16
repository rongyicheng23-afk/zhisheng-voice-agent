# 文档驱动优化：第一批实现与待确认项

依据：《智声大模型流式交互系统企业命题解决方案》V1.0，第二、五、七、九、十一章。日期：2026-09-11。

## 范围决策

按文档先 P0、后 P1/P2，保留现有 Vue/Spring Boot/FunASR/XTTS/MySQL/MinIO 与纪要。
这一批是 **P0 基础组件 + 现有 XTTS 服务优化**，不是完整实时问答交付。
不引入未经确认的第三方账号，不上传用户资料，不增加未鉴权的公开 WebSocket。

| 文档痛点 | 本轮落地 | 尚未落地 |
| --- | --- | --- |
| 长等待 | XTTS 模型进程内复用；增量语义分段器；LLM/TTS 并行生产消费且限制队列；PCM16 播放缓冲原语 | 真实流式 LLM/TTS 接入、认证网关和页面串联、首音对照实验 |
| 插话后旧回答继续 | 编排核心的 turnId、单终态、取消与迟到事件丢弃；播放器按轮次清空与拒收旧包 | 按键插话 UI 接入、跨网关全链路验证、供应商真实取消能力验证；自动 AEC/VAD 属后续增强 |
| 规则资料缺少依据 | 不生成虚假引用，segment.ready 暂时仅输出空 citationIds | 授权知识库、有效期/版本/权限过滤、检索及播放时引用同步 |
| 无声纹会议发言整理 | 现有纪要保持不动，本轮不声称从零实现身份识别 | 既有匿名聚类 Adapter、标注集 DER 评估与修订回归 |

## 修改位置

- `layout-tts-0.22.0/layout/simple_tts_backend.py`：可直接影响现有服务的优化。
  - 模型懒加载并复用；推理串行化，忙时返回 503 + Retry-After，不无限排队。
  - 先校验再写文件；所有分支清理临时文件；使用绝对路径，不依赖 Flask 下载根目录。
  - 输出读入有界内存后删除临时文件，避免 Windows 文件句柄删除问题。
  - 不向接口返回堆栈、路径、密钥；限制 CORS 来源，直接运行默认只监听本机且关闭 debug。
  - 健康接口新增 modelLoaded、synthesisMode=segmented、supportsStreaming=false、supportsCancellation=false，保留已有字段。
  - **行为变化待复核：** 请求上限 20 MB、文本上限 500 字符、音频输出上限 32 MB；并发繁忙时拒绝新任务。现有 Java 客户端不会自动重试，用户需稍后重试。长文本需后续接入分段器。
- `services/realtime/chunking.py`：纯 Python 增量语义边界组件。
  - 综合标点、等待时间、长度、括号/引号、数字小数/千分位/时间边界。
  - 每 50 ms 可主动 poll，不必等下一个 token 才处理等待期限。
  - 超长无边界文本强制有界切分并标记 forced_buffer_limit，供评估统计。
  - **边界：** 启发式，不是完整语言学解析；等待上限优先让位于语义闭合，最长未闭合片段有硬限制；短尾段、复杂单位/列表/缩写仍需要测试集调参。
- `services/realtime/turns.py`：无网络入口、无凭据的异步编排组件。
  - LLM adapter 提供 `stream(prompt)` 异步字符串迭代器。
  - TTS adapter 提供 `synthesis_mode` 和 `stream(text)`，产出 AudioChunk(PCM16 little-endian、单声道)。两者必须在取消/关闭迭代器时释放上游资源。
  - 有界片段队列，单消费者按 segmentSequence 合成/发音频，避免引入乱序。
  - 事件含 sessionId、turnId、sequence、timestamp；不把生成完成等同于播放完成。audio.completed 后须收到 playback_finished(turnId) 才完成轮次，等待有超时。
  - 旧轮次取消时停止本地任务并拒收旧事件；若 adapter 不配合，cleanupPending 如实标识，旧任务释放前不接受新轮次。
  - **边界：** task.cancel 不证明供应商停止计费/推理。XTTS 当前不可抢占，不能借此宣称全链路取消已验收。
  - **非正式线协议：** 内部 audio.chunk 的 pcm 是 bytes。后续网关必须确定二进制帧/元数据封装，不能直接 JSON 序列化 bytes。turn.started、audio.completed 为本轮内部辅助事件，需与团队协议一起冻结。
- `layout-voice-factory/public/audio/turn-pcm-player.js`：AudioWorklet 处理器。
  - PCM16 转 Float32、最多四秒环形缓冲、取消清空、旧轮次/重复包丢弃。
  - 格式/序号缺口/容量异常显式失败，不乱播；Opus/WAV 容器不允许直接写入。
  - **尚未接入任何页面。** 宿主须创建匹配协商采样率的 AudioContext、在用户操作中 resume、配置单声道输出、根据缓冲反馈控制传输，并过滤会话/轮次。不能直接把远端 turn.start 当成本地控制命令转发。
  - 测试是 Node 逻辑测试，不是浏览器 AudioWorklet 集成、声卡时延或播放体验验收。

## 验证记录

1. Python 新增 20 项单元测试：分段无丢字、数字/时间/括号/引号、超时、取消清尾、事件顺序、播放完成确认、取消 LLM/TTS、旧轮次拒收、错误与重试、上传限制、临时文件清理、模型只加载一次。
2. Node 新增 6 项播放器逻辑测试：PCM 转换、播放排空、打断清空、旧轮次与重复包拒收、序号/格式错误、缓冲溢出。
3. 真实 XTTS 本机实验：同一进程、同一中文短句和项目自带参考样本，首次 17.125 s，第二次 2.113 s；两次均 HTTP 200 / 135756 bytes WAV，第二次不重新加载模型，临时文件清理验证通过。
   - 这是两次完整 HTTP 处理耗时，不是 streaming 首音、不是 P50/P95，也不是有统计意义的优化幅度结论。
   - 环境：当前 Apple Silicon Mac、Python 3.11、TTS 0.22.0、PyTorch/torchaudio 2.8.0、CPU 四线程。使用现有已缓存模型。
4. 本轮未启动完整项目，也没有启用麦克风或发送数据到外部模型服务。
5. Spring Boot 现有 6 项测试重新执行通过；Python 编排核心 14 项另在 Python 3.10 下复测通过。独立测试用例总计 32 项（20 Python + 6 Node + 6 Java），不将跨版本重复执行计为新增用例。

复测命令（项目根目录）：

```bash
python -m unittest discover -s services/realtime/tests -v
node --test layout-voice-factory/tests/turn-pcm-player.test.cjs
```

Python 编排核心使用标准库，要求 Python 3.10+；TTS 接口测试另外依赖 Flask/flask-cors，通过模型替身测试，不下载模型。

## 下一批决策与发布门禁

1. 确认已获授权的 LLM、真正支持增量音频块的 TTS、模型名称与服务区域。密钥只放本地环境变量/密钥管理，不进聊天、源码或日志。
2. 确认授权后实现供应商 Adapter，并验证首块早于合成结束、取消行为和失败降级；若未授权，仅保留当前分段模式，不伪装 streaming。
3. 接入 Spring Boot 一次性票据、Python 受控网关、Origin 白名单，再把组件接入现有页面；在此之前不新增裸露网关来绕过鉴权。
4. 接入按键取消与播放确认，做“回答尚未结束已播首音、插话后不回流”的真实浏览器端到端测试。
5. 之后建设授权引用检索与匿名聚类 Adapter/DER 评估，逐项覆盖另外两项痛点。

2026-09-12：上述第一批文件已从解压源码迁移到独立克隆的 `yonghaopu` 工作区，与已推送的第二批 ASR 安全接入改动并存。迁移后重新运行 Python 20 项和 Node 12 项测试，全部通过；`git diff --check` 通过。此轮未重启服务、未重复真实模型性能实验，历史性能数据仅代表上方记录的原实验。第一批仍为未提交、未推送的待审改动；用户确认后再提交。本机绝对路径、虚拟环境、录音或模型权重不进入提交。
