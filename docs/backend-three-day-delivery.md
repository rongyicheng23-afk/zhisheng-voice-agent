# 三天后端改进与演示说明

本次交付实现一次性实时连接票据和依赖状态接口。仍使用现有 Java /ws/funasr 代理；未来 Python 网关需接入共享票据消费协议，本次不宣称已完成该迁移。

## 启动

在 VS Code 的后端终端使用 Java 17，并设置已有 MYSQL_PASSWORD 后执行 mvn spring-boot:run。重启前先停止旧后端。前端仍执行 npm run serve。

JWT_SECRET 可设置为至少 32 字符的随机密钥；通过本地环境注入，不提交仓库。未设置时自动生成进程内临时密钥，重启后需重新登录。JWT_TTL_SECONDS 默认 43200 秒。app.allowed-origins 可由 APP_ALLOWED_ORIGINS 注入，默认仅允许 http://localhost:8081,http://127.0.0.1:8081（逗号分隔，无空格）。上线前需配置实际页面来源。

## API

POST /api/realtime/tickets 使用 Authorization: Bearer 登录凭据，返回 ticket、expiresAt、expiresInSeconds。缓存策略为 no-store。有效期固定 60 秒，每用户最多 5 张未消费票据，全局最多 10000 张；超限返回 429。

/ws/funasr?ticket=... 在升级握手前检查 Origin 并原子消费票据。无票据、过期、伪造或重复消费返回 401，不允许的 Origin 返回 403。连接模型失败也不会恢复票据；重连必须重新申请。旧 token 参数已停用。一次性票据本身仍是短期凭据，网关访问日志应屏蔽查询参数。

GET /api/system/status 同样需要登录。返回整体 UP/DEGRADED，及 mysql、minio、funasr-http、funasr-ws、tts、voiceprint 的 status、latencyMs、message 和 checkedAt。采用 5 秒缓存，单次等待预算约 3 秒。DOWN 表示探测失败，UNKNOWN 表示超时或探测线程繁忙。可在模型全部关闭时演示，不需要加载大模型。

当前权限为已登录用户可见脱敏状态，不包含内部 URL 和异常堆栈；尚未建立管理员角色隔离。HTTP 探测校验状态码，WebSocket 探测完成握手后断开，因此 UP 不代表推理质量或端到端播放成功。

## 五分钟演示

1. 重启前后端并重新登录。在页面开发者工具 Network 中开启实时识别，展示先 POST tickets、再 WebSocket handshake，URL 不再携带登录 JWT。
2. 展示自动测试中并发 20 次消费仅一个成功、60 秒到期拒绝、Origin 拒绝以及重放失败。
3. 登录后在开发者工具 Console 执行以下代码查看真实状态（不打印凭据）：

```javascript
fetch('http://localhost:18080/api/system/status', {
  headers: {Authorization: 'Bearer ' + (localStorage.getItem('token') || sessionStorage.getItem('token'))}
}).then(r => r.json()).then(s => { console.log(s.status, s.checkedAt); console.table(s.services); });
```

4. 按需启动或停止一个模型，等待 5 秒后重新请求，展示对应服务状态变化。
5. 展示测试报告与改动列表。服务时延是探测耗时，不是语音首音时延。

## 验证与限制

后端：JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test。
前端：npm run build。
测试不依赖大模型。新增票据单次消费、过期边界、并发重放、配额、来源拒绝等用例。

本次票据存放在单 Java 进程内，重启失效；多实例部署需共享存储与原子消费。尚未实现 Python 编排、LLM/TTS 全链路取消和管理员观察页面。状态接口可交给前端同学渲染为状态卡片。

汇报建议：第一天说明票据协议与密钥整改；第二天展示握手拒绝与前端兼容；第三天展示依赖状态、测试和剩余集成边界。用实测输出作为证据，不预填性能提升百分比。

