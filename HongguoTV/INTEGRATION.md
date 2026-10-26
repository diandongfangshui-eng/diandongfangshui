# 果影 TV：短剧真实片库的接入阶段说明（2026-10-26）

## 本阶段完成的部分

本阶段的目标是**接入层与数据验证**，不是声称取得红果官方影片访问权限或真实直链。

针对用户提供的 `红果免费短剧.apk`（`com.phoenix.read`，版本 `7.3.9.32`，SHA-256 `3e7a2bdb5b0019fada44f03613527e7b9378340521d43c1f2e56c94243b291c5`），静态分析发现以下原样的 DEX 方法描述：

- `$POST /novel/player/album_detail/v1/`
- `$POST /novel/player/video_detail/v1/`
- `$POST /novel/player/video_model/v1/`
- `$POST /novel/player/multi_video_detail/v1/`

**这些仅是静态字节码中的字符串；它们并非经过网络抓包验证的有效服务，也未确定合法访问所需的请求头、参数、响应 schema。** 本项目不会使用破解请求签名、克隆设备凭据、复用真实账号 Token 或绕过 DRM 的方式取得视频。

该 APK 还包含 `t_series_video_detail`、`t_series_video_model`、`t_video_history_record` 等本地数据结构，可确认实体层至少存在 `series_id`、`series_title`、`series_cover`、`episode_index`、`video_id`、`video_model` 等字段。

## TV 端已新增 `version = 2` 元数据适配层

保留已有片单 v1 直接播放模式。新增 v2 用于加载**从合法授权数据源导出的、具有类似字段名的 JSON**，但这只是本项目约定的导出格式，**不是声称已经解析出了红果实际的 API 响应 JSON**。

```json
{
  "version": 2,
  "dramas": [
    {
      "series_id": "series-abc",
      "series_title": "某授权短剧",
      "series_cover": "https://cdn.example.com/poster.jpg",
      "category": "都市",
      "description": "该描述只是示例",
      "episodes": [
        {
          "episode_index": 1,
          "video_id": "vid-001",
          "title": "第 1 集"
        },
        {
          "episode_index": 2,
          "video_id": "vid-002",
          "title": "第 2 集",
          "playback_url": "https://cdn.example.com/authorized/ep02.m3u8"
        }
      ]
    }
  ]
}
```

上述 `example.com` 是占位域名，不是实际片源。具体结构见 `app/src/main/assets/hongguo_v2_metadata_example.json`。

| v2 字段 | 在 TV 中的作用 | 限制 |
|---|---|---|
| `series_id` | 剧目唯一键、收藏进度键 | 不能当成播放参数直接发到红果 |
| `series_title` | 海报/详情标题 | 来源需合法可靠 |
| `series_cover` | 海报 HTTPS 地址 | 可空 |
| `episode_index` | 选集序号 | 正整数、同剧不重复 |
| `video_id` | 分集标识 | **并不是播放 URL** |
| `video_model` | 可在上游保留、供后续授权接口解析 | TV 端**不会读取它**来产生媒体地址 |
| `playback_url` | 明确提供的授权 HTTPS 媒体地址 | 缺失时该集**不可播放** |

安全策略：v2 至少提供一集 `episode_index` 与 `video_id`；单部剧 300 集上限，单次片单 300 部上限；所有可用媒体链接必须是 HTTPS；缺少媒体链接的剧集只显示信息、禁止调用播放器；不允许直接把私有平台 `video_model` 当成 URL 使用。

## 下一阶段的真实服务对接需要什么

**方案 A：红果视频版权/分发合作。** 在官方确认电视端的播放权限、可调用接口、鉴权方式、DRM、链接时效、地区限制及播放权之后，在独立授权后端按文档取得 `playback_url`，由自有客户端消费。不要把任何平台凭据提交到 GitHub。

**方案 B：获得资质的短剧提供商或抖音云短剧媒资。** 官方抖音云资料称短剧 OpenAPI 可生成小程序以外的播放链接，但对行业开发者开放且需要提交工单，**不表示红果消费者片库可复用**。

官方文件：
- https://developer.open-douyin.com/docs/resource/zh-CN/developer/tools/cloud/industry-solutions/short-drama-media-management
- https://developer.open-douyin.com/docs/resource/zh-CN/mini-app/open-capacity/solutions/vertical/micro-drama

**方案 C：个人观看先用手机官方红果客户端投屏到电视。** 这是平台 API 授权前最简单、账号一致性最强的路线，但属于不同于本项目 TV 原生播放的方案。

## 实测验收与阻塞项

- [x] 上传 APK 的静态元数据及候选播放路径分析
- [x] v1 / v2 数据模型兼容、分集标识、无地址不可播放的安全控制
- [x] 使用伪造 ID 的本地测试数据及单元测试纳入 CI
- [ ] 取得红果或内容授权方的第三方 TV 播放许可
- [ ] 合法测试账号/服务凭据及官方响应样本
- [ ] 验证授权端点的真实请求、响应、媒体格式、链接时效与 DRM
- [ ] 在真机用真实授权剧集测试并验证版权/地区约束

本项目已实现的是**接入基础设施**，并非「红果真实片库已接通」。
