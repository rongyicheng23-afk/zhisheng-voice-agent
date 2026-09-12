# 第二批：现有实时转写的安全接入与连接隔离

2026-09-11。本轮针对解决方案第七章的连接/取消语义与第十章的接入安全要求。
不是 LLM 问答上线：新增 Python 问答编排和 AudioWorklet 仍待供应商及页面接入。

## 已修改

1. 新增 `POST /api/realtime/ticket`：先经现有登录拦截器校验，再确认用户存在。返回 30 秒有效的 256 bit 随机票据，绑定用户、来源与当前 FunASR 接口用途；响应 `Cache-Control: no-store`。
2. `/ws/funasr` 握手前原子消费票据，拒绝重放、过期、缺失票据、旧 `token=JWT` 和不允许的 Origin。内存只存票据摘要，单用户 30 秒最多签发 10 张，全局缓存有界。
3. WebSocket Origin 改为明确白名单，默认仅 `http://127.0.0.1:8081`、`http://localhost:8081`。可通过 `REALTIME_ALLOWED_ORIGINS` 设置逗号分隔的精确来源，不允许通配符。
4. 前端先用 Authorization 请求票据，WebSocket URL 不再包含长期 JWT；连接状态接口不返回票据；不记录包含敏感 URL 的错误对象。
5. 连接使用 generation 隔离：停止或新连接使旧票据请求、消息和关闭回调失效。连接 Promise 在后端发送 `session.ready` 后才成功，而非仅凭浏览器 HTTP 升级。
6. `session.ready` 表示 Java 已连接 FunASR 并建立上下文。`asr.completed` 表示 Python 已处理停止请求并发送本轮稳定结果，前端收到后关闭，不再固定 800 ms 截断；若 10 秒未确认，显示超时而不是虚报完成。
7. 修复旧停止定时器误关新连接；修复等待 getUserMedia 时取消/离开页面，迟到授权可能继续占用麦克风的问题；正常停止显式停止 MediaStream tracks 并清理音频节点。
8. “测试连接”不再自动打开麦克风，只验证模型通道就绪并关闭连接。
9. 恢复 `chunk_interval=10` 与现有 60 ms PCM / `[5,10,5]` 模型配置匹配。对照排查中同一样本在 5 时未返回稳定结果，在 10 时返回稳定文字。因此不能仅减半间隔就声称延迟下降；后续需统一模型、VAD 和实际帧时长契约再优化。

## 文件

- Java：`com/wc/realtime/` 的票据存储、Origin 配置、签发控制器、握手拦截器；现有 WebConfig、FunasrWebSocketConfig、FunasrRealtimeProxyHandler。
- 前端：`src/libs/websocket-client.ts`、`src/libs/audio-recorder.ts`、`src/views/RealtimeVoice.vue`。
- FunASR：`services/funasr/funasr_wss_server.py` 增加停止处理完成确认。
- 测试：RealtimeTicketsTests.java、realtime-connection.test.cjs、verify_ticket_connection.py。

## 兼容与部署边界

- **前端、Java 和 FunASR 服务必须一起更新并重启。** 旧前端的 JWT URL 会被拒绝；旧 FunASR 不发送完成确认时新页面会超时关闭。`wsStart()` 现为异步并等待模型就绪，所有仓库内调用点已调整。
- 这是现有 Java 转发 ASR 路径的安全过渡方案，不是文档拟建的 Python `/realtime/*` 问答网关。票据 `purpose=funasr` 不能直接用于未来网关。
- 票据缓存为单 JVM 内存：重启使未消费票据失效。多实例需共享的原子消费存储（如 Redis），并继续验证重放/限流；当前不能直接无状态水平扩容。
- Origin 白名单不是独立身份验证，非浏览器客户端仍须合法登录并申请票据；非浏览器验证请求需要显式传 Origin。
- 30 秒是建立连接的票据有效期，不是整个 WebSocket 的存活上限；连接级配额、登录撤销联动、空闲/最长会话时限仍待增强。
- 正式环境仍须 HTTPS/WSS、网关限流、日志脱敏和现有 JWT 密钥迁移。**本轮没有把整个项目宣称为生产安全。** 原有其他 API 的宽泛 CORS 未统一整改。
- 一次性票据仍属于敏感数据：代理日志不可记录 `$request` / `$request_uri` 的完整查询串。部署应使用不带参数的 `$uri` 等字段并保留必要审计；本轮未修改或部署原有 Nginx 文件。
- 当前实时录音仍按旧业务自动保存历史，录音保存授权、隐私告知与可选落盘尚未整改。本轮测试仅使用项目自带示例音频。
- “模型通道就绪”不等于识别质量通过；真实音频另行测试。自动插话、LLM/TTS 全链路取消和知识引用尚未完成。

## 验证命令

本地累计已验证：Java 12 项、Node 12 项、Python 20 项测试通过；前端生产构建与后端打包通过。其中包含第一批尚未迁移到本分支的编排与播放器测试；本次仅提交第二批改动，可独立运行 Java 12 项及连接 Node 6 项。构建仍有已有的包体积、Browserslist 数据陈旧及 CSS/API 弃用警告，未将其误报为本轮已解决。

真实本地票据链路使用示例 PCM，收到稳定文字及 `asr.completed`，并验证未登录签发返回 401、非法来源返回 403、旧 JWT URL 与票据重放返回 401。Chrome 中点击“测试连接”显示“连接测试成功（未开启麦克风）”。这些是固定样本功能检查，不是首音/并发性能报告。

项目根目录：

```bash
node --test layout-voice-factory/tests/realtime-connection.test.cjs
```

后端目录：`mvn -B test package -Dstyle.color=never`。
前端目录：`NODE_OPTIONS=--openssl-legacy-provider pnpm run build`。

完整本地栈运行后，在项目根目录运行下列集成测试。它使用本地 admin 测试账号、示例 PCM，创建测试历史，不开启麦克风，不打印票据和登录令牌：

```bash
python services/realtime/tests/verify_ticket_connection.py
```

完整本地业务冒烟回归通过：文件 ASR、实时 ASR、声纹比对、TTS、会议处理及对应音频下载。测试历史和生成音频保留，未删除业务数据。

2026-09-12 收尾复核：最后的前端改动重新生产构建成功，12 项 Node 测试再次通过。测试启动的 7 个项目服务已全部停止并复查状态；共享 MySQL 未停止，数据库、音频和日志均保留。

用户确认第二批改动后，单独克隆 `yonghaopu` 分支并迁移本批代码；第一批编排、播放器与 XTTS 优化不包含在本次提交中。本机环境、模型、录音及构建产物不进入提交。
