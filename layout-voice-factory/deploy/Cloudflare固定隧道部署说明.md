# Cloudflare 固定域名部署说明

适用场景：
- 你已经买好了域名
- 你希望把本机 `http://localhost:8088` 稳定映射成固定公网地址
- 你不想继续使用不稳定的 `trycloudflare.com`

---

## 1. 先把域名接入 Cloudflare

1. 登录 Cloudflare
2. 选择 `Domains`
3. 点 `Add a domain`
4. 输入你买好的域名
5. 按 Cloudflare 提示到域名注册商那里修改 NS
6. 等待域名状态变成激活

---

## 2. 创建 Named Tunnel

1. 进入 `Zero Trust`
2. 选择 `Networks`
3. 选择 `Tunnels`
4. 点 `Create a tunnel`
5. 选择 `Cloudflared`
6. 给 tunnel 起一个名字，例如：
   - `voice-factory-local`

创建后，Cloudflare 会生成一段 token。

---

## 3. 配置公网域名映射

在 tunnel 里增加一条 `Public hostname`：

- 子域名：`voice`
- 域名：你的域名，例如 `baconboat.cn`
- Type：`HTTP`
- URL：`http://localhost:8088`

最终你的固定地址会是：

```text
https://voice.baconboat.cn
```

---

## 4. 配置本机脚本

先复制模板：

```bash
cp /Users/rongyicheng/Desktop/layout-voice-factory/deploy/cloudflared.env.example \
   /Users/rongyicheng/Desktop/layout-voice-factory/deploy/cloudflared.env
```

然后编辑这个文件：

```text
/Users/rongyicheng/Desktop/layout-voice-factory/deploy/cloudflared.env
```

改成这样：

```bash
CLOUDFLARE_TUNNEL_MODE=named
CLOUDFLARE_TUNNEL_TOKEN=这里填 Cloudflare 给你的 token
PUBLIC_TUNNEL_URL=https://voice.baconboat.cn
```

---

## 5. 重启本机发布脚本

```bash
/Users/rongyicheng/Desktop/layout-voice-factory/deploy/stop-local-publish.sh
/Users/rongyicheng/Desktop/layout-voice-factory/deploy/start-local-publish.sh
```

---

## 6. 检查是否生效

```bash
/Users/rongyicheng/Desktop/layout-voice-factory/deploy/status-local-publish.sh
```

理想输出应包含：

```text
Tunnel 模式: named
公网地址: https://voice.baconboat.cn
公网状态: HEALTHY
```

---

## 7. 常见问题

### 7.1 提示 token 不可用

重新从 Cloudflare Zero Trust Tunnel 页面复制 token，覆盖到：

```text
/Users/rongyicheng/Desktop/layout-voice-factory/deploy/cloudflared.env
```

### 7.2 域名能打开但页面异常

先检查本地入口：

```text
http://localhost:8088
```

如果本地入口能用，再检查：
- Spring Boot 是否正常
- Nginx 是否正常
- Cloudflare Public hostname 是否指向 `http://localhost:8088`

### 7.3 WebSocket 不通

确认前端已经是公网版配置，并且 Nginx 正常代理：

- `/ws/funasr`

---

## 8. 推荐做法

比赛正式提交时，建议提交固定域名地址，例如：

```text
https://voice.baconboat.cn
```

这会比 `trycloudflare.com` 更稳定，也更正式。
