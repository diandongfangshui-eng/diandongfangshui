# 果影 TV 0.8.0

真实官网目录与公开分集的原生 Android TV 客户端。首页、热播、分类分页、详情、完整分集ID目录、关键词建议、本地收藏/进度；不需要手动JSON、电脑、NAS或局域网代理。

## 全部片库

首页进入「全部片库」，选择真人剧、漫剧、AI剧及服务返回的题材。各类服务当前返回800条/34页，每页24条，支持上一页、下一页、末页、跳页。各类可能重复，不代表2400部独立剧目，也不代表App全量片库。选剧进入完整分集目录，返回保留分页。

## 本版实际播放结果

官网前端本身提供POST /api/video/protected/fallback-play-info，正常请求在其公开范围内可返回带有效期的MP4媒体。本版通过该公开路径交给Media3，媒体授权以每集正常服务响应为准。

Android TV36模拟器实测《军需处长的账本》第1/2/3集已显示实际视频首帧、播放进度与音频时钟推进。竖屏居中、暂停、10秒快进、遥控切集、结束自动下一集、断点恢复均有运行证据。网页声明的3集是显示提示；本版逐集正常请求播放，样本第4集实际返回HTTP403，停止播放并显示错误，不将其标成已授权。

这条公开MP4路径不需要专用SDK License。加密DASH的官方SDK候选分支仍保留，当前没有本应用有效License，不能声称该分支解密成功。

## 遥控操作

方向键导航，确定打开详情/播放。播放器左右10秒、上下切集、确定暂停/继续、菜单倍速、长按菜单重新获取媒体地址、返回退出。本机保存按集进度。

## 手机号验证码登录

设置提供官网登录入口及本应用内WebView会话接入。手机号码和验证码只在官网表单输入，客户端不读取表单，不代发验证码，不读取其他App/外部浏览器Cookie。原生接口只把本应用官网Cookie发送至https://hongguoduanju.com，禁止跨源或降级跳转携带会话。

当前TV WebView实测未出现官网短信表单，未登录profile请求HTTP500；没有真实登录成功证据。红果App短信登录不等于已支持第三方客户端登录，也不等于播放器License。完整搜索result仍403，目前是关键词建议。外部系统浏览器登录不会自动同步本应用会话。

## 安装和构建

安装GuoyingTV-debug.apk，或adb install -r GuoyingTV-debug.apk。包含官方SDK的包面向arm64-v8a/armeabi-v7a。无WebView设备仍可尝试匿名公开目录/媒体路径，官网登录需要可用WebView。

JDK17+、SDK35；Windows执行gradlew.bat :app:testDebugUnitTest :app:assembleDebug，其他系统sh gradlew :app:testDebugUnitTest :app:assembleDebug。

若具有自己的加密SDK授权，将绑定包名com.diandongfangshui.guoyingtv的vod.lic放入app/src/main/assets/license/vod.lic，构建加-PvodAppId=自己的AppID。该文件已gitignore，不得上传。通用SDK License不能替代内容播放权限。

项目独立于MoaanXReader。当前35项自动测试通过；实际运行验证范围和未验证项见独立0.8.0报告。
