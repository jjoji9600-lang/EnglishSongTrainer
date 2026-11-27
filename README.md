# English Song Trainer

安卓英文歌曲 / 视频精听播放器，0.1.0 初始原型。

## 功能
- 本地 MP4、MP3、MKV、WebM 等（实际兼容性取决于设备和 Media3 解码器）。
- 外部 UTF-8 LRC、SRT、VTT 时间轴字幕；点击歌词定位，上一句、下一句、重听。
- 连续播放、句末暂停、单句循环；循环次数（含无限）、0–10 秒跟读留白。
- 0.5–1.5 倍变速保持音调，AB 片段循环，整体字幕偏移 ±0.1 秒。
- 长按收藏，记忆最近媒体、字幕、位置与偏移。
- 无网络权限，无账号或 API Key。

## 安装
在 Actions 中打开成功的 Android Debug APK 工作流，在 Artifacts 下载 EnglishSongTrainer-debug，解压安装 APK。测试签名仅用于个人测试。

## 本地构建
JDK 17、Gradle 8.9、Android SDK 35。执行 gradle :app:testDebugUnitTest :app:assembleDebug。
输出 app/build/outputs/apk/debug/app-debug.apk。

## 使用与限制
先打开媒体，再导入字幕，选择训练模式并点击歌词。没有字幕可以用 AB 循环。LRC 最后一句默认持续到媒体结束。整体时间轴可调整，尚无逐句时间编辑。此版本不自动识别烧录歌词，不内置下载、AI 分析或录音。初始原型需实际编译和真机测试，循环精度受播放器与关键帧影响。横屏及小屏布局待优化。请自行提供有权使用的媒体和歌词。
