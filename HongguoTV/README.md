# 果影 TV · Android TV 短剧播放器（非官方）

这是一个**独立 Android TV 原生应用**，旨在实现电视遥控器操控的短剧海报墙、搜索、分类、选集与全屏播放。它不是红果短剧官方客户端，也**不会自称能够读取红果官方片库**。

## 新增：0.4 红果官网真实片库（不依赖 NAS）

果影 TV 0.4 在侧边栏增加「红果官网片库」，通过 Android WebView 直接加载真实的 `https://hongguoduanju.com/`。官网已公开提供实时剧集目录、排行榜、分类、搜索入口、剧集详情和部分分集的网页播放器。本功能不依赖 JSON 测试片单、不需要用户搭建局域网服务器，也不调用逆向的手机 App 私有接口。

- 一键进入官网首页、热播榜、真人剧分类；电视遥控器方向键移动焦点，菜单键返回工具栏；浏览器提供后退、刷新和页面缩放。
- 仅允许顶层网页停留在 `hongguoduanju.com` 及 `www.hongguoduanju.com` 的 HTTPS 页面，禁止跳转其他网站；网页子资源由系统 WebView 按正常规则加载。
- 允许官方网页自主实现其原有视频播放器，但**不保证所有电视 WebView 能正常解码**，也不保证官网开放所有分集。已确认官网当前第 1–3 集有视频页，更多分集可能需要使用官方 App。
- 这个功能是访问真实官网，不是获得红果 App 私有接口的播放权限，更不提供破解 DRM、伪造设备签名或复制用户凭据。
- 尚未在真实 Android TV 设备上进行端到端 WebView 视频播放验收。如果加载失败，请更新 Android System WebView、检查网络区域及平台策略。

## 新增：0.3 局域网片库接入

果影 TV 0.3 增加侧边栏「局域网片库」入口，支持家庭电脑/NAS 运行的、采用公开 `hongguotv` API v1 协议的独立内容服务。具备连接测试、推荐分页、搜索、选集、Media3 播放、本机断点续播及失败重新解析媒体 URL 的能力。该模式与 `version=1/2` HTTPS JSON 片单并存。

**内容服务本身不包含在本 APK 内**。连接的第三方服务若访问了非官方平台接口，可能无法使用，且涉及平台条款和内容授权；本项目不提供破解签名/DRM、官方登录劫持或账号凭据复用。只有真实授权片源的服务才能可靠、合法地用于正式产品。

详见 [局域网桥接配置与使用说明](BRIDGE_SETUP.md)。GitHub CI 通过也不代表已经完成实际红果视频播放测试。

## 已实现

- Android 6.0+（API 23+）电视 / 盒子，横屏，Leanback 启动入口，完全遥控器操作。
- 精选剧场、题材分类、本地搜索、剧情详情、剧集选择。
- Media3（ExoPlayer）播放 HTTPS MP4 / HLS m3u8，保持视频原始比例（适合竖屏短剧）。
- 结束自动播放下一集；遥控器左右各跳转 10 秒、上下切集、确定键暂停、菜单键切换 1.0/1.25/1.5/2.0 倍速、返回退出。
- 收藏、最近观看、按集断点续播（Android SharedPreferences）。
- TV 设置页导入合法授权的 HTTPS JSON 剧集片单。失联时优先回退上次成功缓存。
- 首次启动展示注明来源的公开演示影片，不虚构红果剧集和账号功能。

## 使用方式

1. 在 Actions 工作流 `果影 TV - Android TV APK` 中选择运行，或向 `hongguo-tv-standalone` 分支提交变更。
2. 构建成功后从工作流的 **Artifacts** 下载 `GuoyingTV-AndroidTV-debug-APK`，解压获取 `app-debug.apk`。
3. 将 APK 拷贝到电视 / Android TV 盒子安装，或使用 `adb install -r app-debug.apk`。
4. 首次运行会看到公开演示片源。若有授权片源，在“设置”中填入 HTTPS JSON 地址，选择“保存并更新剧库”。

**注意：** GitHub Actions 的 Debug APK 是开发测试签名，适合个人安装。正式分发需要准备自有发布签名并遵守版权、隐私与平台规则。当前设备实测尚未完成。

## 自己构建

要求 JDK 17、Android SDK Platform 35 / Build Tools 35.0.0、Gradle 8.11.1：

```bash
cd HongguoTV
gradle :app:assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk
```

## 片单格式（version 1）

```json
{
  "version": 1,
  "dramas": [
    {
      "id": "your-series-001",
      "title": "你的短剧名称",
      "category": "都市",
      "description": "剧情简介",
      "poster": "https://your-authorized-cdn.example/poster.jpg",
      "episodes": [
        {
          "title": "第 1 集",
          "url": "https://your-authorized-cdn.example/001.m3u8"
        },
        {
          "title": "第 2 集",
          "url": "https://your-authorized-cdn.example/002.mp4"
        }
      ]
    }
  ]
}
```

- 片单 URL、封面 URL、视频 URL 仅支持 HTTPS。
- 片单大小限制 2 MB，最多 300 部剧，每剧最多 300 集；`id` 必须唯一。
- 服务器必须允许直接获取 JSON 和媒体，HLS 分片也必须可访问。本版不具备第三方登录、Cookie 同步、视频 DRM 许可证处理或携带特定私有请求头的能力。
- 可以把示例 JSON 放在自己控制的 HTTPS 静态托管（如 GitHub Pages），但**不要公开放置未获授权的媒体链接、访问令牌和账户信息**。
- 视频版权及公开传播权需要由片源提供者自行确认。

## 项目结构

```text
HongguoTV/
  app/src/main/
    AndroidManifest.xml       # 电视入口、网络权限
    java/.../MainActivity.java # 遥控器友好界面
    java/.../PlayerActivity.java # 播放、断点和切集
    java/.../CatalogStore.java # 受限 HTTPS 片单加载/缓存
    java/.../Drama.java        # 剧集数据校验
    assets/demo_catalog.json  # Google TV 官方公开演示样片（非红果）
  app/build.gradle
  settings.gradle
```

## 技术限制与下一步

1. **红果内容接入**：红果短剧的官方授权接口及第三方播放许可未被确认，当前只提供通用片源机制。社区中的非官方接口随时可能失效，且可能违反服务条款；**本仓库不内置这类接口**。
2. **设备测试**：GitHub 编译成功不等于真实电视兼容。需对低内存 ARM 盒子、竖版 H.264 / H.265、断网恢复、HLS 连播进行真机测试。
3. **进阶功能**：后续可在有授权和合适接口的前提下增加二维码配对、跨设备同步、拼音键盘、播放器轨道/字幕选择、TV 首页频道。
4. 项目是独立工程，不依赖也不修改墨案阅读器 `MoaanXReader`。

本项目由用户独立开发用途发起，不代表红果短剧官方。相关名称和影视作品的知识产权归各自权利人所有。
