# 果影 TV 0.6.0

独立Android TV Java工程，首次启动读取红果官网真实目录，不需要JSON片单、电脑或NAS。支持首页、热播、分类分页、真实详情、完整返回的分集目录、官网关键词建议与本地收藏。

本版新增真实播放模型请求、有效期缓存、强制刷新，以及公开下载的官方Android播放器SDK。分离DASH模型按SDK实际结构映射，播放凭据只在内存中传递。未从原版复制SDK、应用身份或License。

## 实测状态

- 正常播放API、缓存命中与强制重取已通过真实网络测试。
- TV API36模拟器中实际加载ARM SDK库，License返回0：本应用尚无有效授权。
- 实际SDK解析真实响应：视频轨1、音频轨1，均加密，DASH=true。
- 25项自动测试通过。模型解析成功不代表解密或首帧成功。
- 完整搜索结果接口仍403；当前是官网关键词建议，不是完整分页搜索。App登录/跨设备同步尚未接入。

## 安装与操作

安装GuoyingTV-debug.apk，或adb install -r GuoyingTV-debug.apk。本版包含的官方原生库面向arm64-v8a/armeabi-v7a，未提供x86客户端。

方向键导航、确定选集。原生播放页先检查本应用SDK授权；也可独立检查真实模型而不播放。已接入原生遥控器左右10秒、确定暂停/继续、菜单倍速、上下切集、完成后下一集、5秒保存进度及返回后恢复的代码，但尚无有效License，因此这些原生解码与播放行为未完成实际验收。官网网页入口保留，受官方浏览器支持范围影响。

## 配置自己的正式播放器授权

需要控制台绑定本应用包名com.diandongfangshui.guoyingtv的AppID和有效License，并确认内容方允许该客户端播放相应内容。通用播放器License不自动授予红果片库内容权限。

将自己的文件放入app/src/main/assets/license/vod.lic（已gitignore）；构建参数为-PvodAppId=自己的AppID。Env使用License成功回调重查授权，不将异步加载中的0当作最终失败。License/播放凭据不得提交GitHub。

Windows：gradlew.bat -PvodAppId=自己的AppID :app:testDebugUnitTest :app:assembleDebug
其他系统：sh gradlew -PvodAppId=自己的AppID :app:testDebugUnitTest :app:assembleDebug
未配置该参数仍可编译浏览目录与授权诊断版，但不能宣称可以解密播放。

官方说明：https://docs.volcengine.com/docs/video-on-demand/android-player-license-validation?lang=en
SDK依赖来源：https://artifact.bytedance.com/repository/Volcengine/

项目不修改MoaanXReader。原版App在当前ARM翻译模拟器中接受协议后发生native崩溃，无法用该环境完成原版未登录播放参照；详情见独立验收材料。
