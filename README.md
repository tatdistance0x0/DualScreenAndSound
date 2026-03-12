README.md（中英文版）
# DualScreenAndSound

DualScreenAndSound 是一个 Android 示例项目，用于演示 **多屏显示（Dual Display）与音频路由（Audio Routing）** 的实现方式。

DualScreenAndSound is an Android demo project that demonstrates **multi-display presentation and audio routing**.

---

# 功能特性 | Features

• 支持 Android 多屏显示（Dual Screen / Multi Display）  
• 支持在指定副屏播放视频  
• 支持 HDMI / DP 音频输出路由  
• 支持检测副屏连接与断开  
• 支持多音频设备选择  

• Android multi-display support  
• Video playback on secondary display  
• HDMI / DP audio routing  
• Detect display connect / disconnect  
• Multiple audio device selection  

---

# 技术点 | Technologies

本项目主要涉及以下 Android 技术：

This project demonstrates several Android system APIs:

- `DisplayManager`
- `Presentation API`
- `MediaPlayer`
- `AudioDeviceInfo`
- `SurfaceView`
- Android 多屏显示架构

---

# 项目结构 | Project Structure


DualScreenAndSound
├── app
│ ├── src/main/java
│ ├── src/main/res
│ └── build.gradle
│
├── gradle
├── build.gradle
├── settings.gradle
└── README.md


---

# 使用方法 | How to Build

## 1 克隆项目 | Clone


git clone https://github.com/tatdistance0x0/DualScreenAndSound.git


## 2 使用 Android Studio 打开

Open with **Android Studio**


File → Open → DualScreenAndSound


## 3 编译运行 | Run


Run → app


---

# 运行效果 | Demo

当设备连接第二块屏幕时：

When a secondary display is connected:

• 副屏会创建 `Presentation`  
• 视频将在指定屏幕播放  
• 音频可以选择指定输出设备  

---

# 适用场景 | Use Cases

该示例适用于：

This project is useful for:

- 工业控制屏  
- 商显设备  
- 车载系统  
- 双屏广告机  
- Android 多屏系统开发  

---

# 作者 | Author

GitHub  
https://github.com/tatdistance0x0

---

# License

MIT License
README最终效果

GitHub 页面会变成类似这样：

DualScreenAndSound
Android demo for multi-display and audio routing

Features
✔ Dual screen presentation
✔ HDMI / DP audio routing
✔ Display connect detection
✔ Media playback

Technologies
DisplayManager
Presentation API
MediaPlayer
AudioDeviceInfo

看起来就像一个 完整开源项目。
