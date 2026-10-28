# 果影 TV 0.3：局域网内容服务接入

此版为 Android TV 增加可选的「局域网片库」入口，对接公开文档描述的 `hongguotv` 家庭服务 HTTP API v1。它是**独立开发的客户端协议适配器**，并没有复制第三方 GPL 源码，也没有把红果平台的内部接口、账号凭据、算法、签名或 DRM 破解代码写入 APK。

## 使用前提与准确定位

- 内容服务独立运行在个人电脑、NAS 或家庭服务器；果影 TV 不会安装、启动、配置这套服务。
- 此类服务可能使用非官方接口，可能受到技术限制或服务条款约束，**不代表红果授权第三方访问其影视内容**。实际访问是否合法、能否正常播放，要由用户核实授权和当地法律、平台条款。
- 我们只是实现其公开的本地服务通信格式；**截至本次构建，没有在真实电视或真实第三方桥接服务上做过端到端播放验收**。
- 若希望面向公众发布，优先改为对接取得正式版权、分发及播放权限的服务端。
- 仅可填电脑/NAS 的局域网 IPv4 地址（10.x.x.x、172.16–31.x.x 或 192.168.x.x），不允许公网域名、任意代理 URL、localhost、请求中携带密码、路径或参数。

## 在电脑或 NAS 上部署兼容内容服务

一个已公开实现上述协议的独立项目是 [buryix/hongguo-tv](https://github.com/buryix/hongguo-tv)，其 `docs/API.md` 定义了接口。以下仅为第三方文档中的安装方法，不是本仓库自带的服务：

```sh
git clone https://github.com/buryix/hongguo-tv.git
cd hongguo-tv
npm ci --prefix server
node server/index.mjs --lan
```

需要 Node.js 22+。此服务据其文档可在 `http://电脑局域网IP:8787` 暴露 HTTP API。电脑和电视须在同一可信局域网，开启路由器/防火墙允许设备连接 TCP 8787。**不要向公网开放该服务；HTTP 未加密；不要把红果账户或密码作为 BRIDGE_TOKEN。** 口令由用户在其信任的电脑/NAS 上管理，并只用于这个本地服务。

## 电视端操作

1. 安装 GitHub Actions 新生成的果影 TV 0.3 Debug APK。
2. 打开左侧「局域网片库」。
3. 填写本机服务 IPv4，例如 `http://192.168.1.10:8787`；如设置了服务的可选访问口令，在第二个框输入。
4. 点击「连接」，客户端先调用 `GET /health` 验证 `service=hongguotv`、`apiVersion=1`。
5. 连接成功后出现「精选首页」。可点击「搜索」或「下一页」，进入详情选集。
6. 播放时客户端调用 `GET /api/episodes/:id/play` 获取受限于本地服务的 `/api/episodes/:id/stream` 路径，再通过 Media3 ProgressiveMediaSource 向该本地服务请求视频数据。发生播放错误时尝试带 `fresh=1` 重新解析一次。

## 协议映射

| 调用 | 期望内容 | 行为 |
|---|---|---|
| `GET /health` | `{service:"hongguotv", apiVersion:1}` | 连接测试，不保证上游可用 |
| `GET /api/home?page=N` | `{items:[Series],page:N}` | 精选列表 |
| `GET /api/search?q=...&page=N` | 同上 | 遥控器输入关键词 |
| `GET /api/series/:id` | Series 带 `episodes` | 选集详情 |
| `GET /api/episodes/:id/play?fresh=1` | `{path:"/api/episodes/.../stream", contentType:"video/mp4"}` | 只接收同一桥接服务上的媒体路径 |
| `GET /api/episodes/:id/stream` | 视频数据 / 支持 Range | Media3 原比例播放、可 seek |

客户端不直接访问 `api.fqnovel.com` 等可能属于红果的内部生产 API，不会在应用中注册/伪造设备，也不会从 `video_model` 绕过平台授权提取媒体。

## 安全边界

- JSON 请求不跟随 HTTP 302 / 301 重定向，响应限制 2MB；播放路径必须为预期的同源 `/api/episodes/:id/stream`。
- Android 开启 cleartext 网络是为了允许个人可信 LAN 的 `http://192.168.*` 服务。**若输入口令，HTTP 明文传输有风险**；请仅在可信内网使用，生产环境应通过 HTTPS 代理与正式鉴权。
- 当前可选口令保存在本机普通 SharedPreferences，不是加密凭据库；不要使用平台登录密码或高价值密钥。
- 第三方服务若恶意返回媒体跳转/构造异常流量，客户端的防御能力有限；请只连接可信服务。
- 开源客户端的独立构建和第三方项目的 LICENSE 各自保持原状。不要把 GPL 第三方组件直接复制到 MIT 目录并以 MIT 对外发布。

## 质量与验收

GitHub Actions 执行 `app:testDebugUnitTest` 和 `app:assembleDebug`。BridgeApiTest 验证局域网地址筛选及头注入字符串被拒；原有 DramaAdapterTest 验证 v1/v2 数据格式兼容。

即使 CI 成功，也只证明 Java 编译和单元测试通过。**仍需用户的电视/盒子与独立内容服务联调，才能确认实时剧集和播放能够实际使用**。
