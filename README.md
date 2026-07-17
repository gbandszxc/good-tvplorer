# Good TVplorer

<p align="center">
  <img src="docs/raw/icon/raw_icon.png" width="200" alt="Good TVplorer">
</p>

为 Android TV 与电视遥控器设计的文件管理器，同时浏览**本地存储**与 **SMB / NAS** 媒体库。

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin)
![AGP](https://img.shields.io/badge/AGP-9.2.1-3DDC84?logo=android)
![minSdk](https://img.shields.io/badge/minSdk-23-3DDC84)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84)

> ⚠️ **安全提示**：当前版本为 MVP，SMB 密码以明文存储在应用 SQLite 数据库中。请勿在不可信设备上保存重要凭据，加密方案已列入后续计划。

---

## 功能特性

- **Android TV 原生体验**：Leanback 启动器入口、D-pad 焦点导航、固定本机/网络顶部 Dock 与连接/设置侧边 Dock。
- **本地 + SMB 统一浏览**：同一套界面同时浏览 Android 本地目录与 SMB / NAS 共享。
- **SMB 性能优化**：连接复用、空闲重连、单次重试、≥4MB 大文件流水线读取。
- **媒体预览**：低内存友好的图片查看与胶卷缩略图懒加载；音频封面、LRC 歌词与播放；沉浸式视频播放、内嵌/SRT 字幕、倍速和画面模式；带行号和翻页的文本预览。
- **列表 / 网格双模式**：右侧实时预览当前焦点文件。
- **连接管理与显示设置**：独立横屏页面管理 SMB 新增、编辑、删除；显示设置提供字体缩放。
- **统一持久化**：Room + SQLite 统一保存连接、设置和浏览恢复状态。
- **现代 Android 技术栈**：Kotlin + Jetpack Compose + Material3 + Room + Media3 + Coil3。

## 界面示意

```text
┌─────────────────────────────────────────────────────────────────────┐
│      │                    [ 本机 ] [ 网络 / SMB ]                 │
├──────┼──────────────────────────────────────────────┬───────────────┤
│      │ 媒体入口 / 下载       [网格] [刷新] [返回]     │   快速预览    │
│      │ [IMG] vacation.jpg        [VID] demo.mp4      │   ┌───────┐   │
│ 连接 │ [DIR] documents           [AUD] song.flac     │   │ 缩略图 │   │
│ 设置 │                                                │   └───────┘   │
│      │ 网络为空时：             [ 去配置 ]           │  vacation.jpg │
└──────┴──────────────────────────────────────────────┴───────────────┘
```

> 实际截图待补充。当前 UI 包含深色主题首页、文件浏览器（列表/网格）、右侧快速预览面板。

## 快速开始

### 支持的设备

- **推荐**：Android TV、电视盒子、投影等带遥控器的大屏设备（`LEANBACK_LAUNCHER` 入口）。
- **可运行**：普通 Android 手机/平板（`minSdk = 23`），但 UI 为 D-pad / 遥控器优化，触屏操作不便。
- **模拟器**：Android TV 模拟器或普通 Android 模拟器均可。
- **CPU 架构**：`armeabi-v7a`、`arm64-v8a`。

### 环境要求

- JDK 17
- Android SDK API 36
- 一台已开启开发者选项并连接好的设备/模拟器

### 配置本地路径

项目根目录下需要一个 `local.properties`（已加入 `.gitignore`，不会提交到仓库），同时指定 Android SDK 和 JDK 路径：

```properties
# local.properties（gitignored）
sdk.dir=C\:\\path\\to\\Android\\Sdk
java.home=C\:\\path\\to\\jdk-17
```

首次构建前请根据本机环境创建此文件。参考本机已有配置：

```properties
sdk.dir=C\:\\Users\\gbandszxc\\AppData\\Local\\Android\\Sdk
java.home=D\:\\Develop\\Java\\zulu17.58.21
```

### 构建与安装

```powershell
# 调试构建
.\gradlew.bat :app:assembleDebug

# 安装到已连接设备
.\gradlew.bat :app:installDebug
```

也可以直接用 Android Studio 打开项目目录，在 Android TV 模拟器或真机上运行 `app`。

### 运行后

1. 首次启动会请求媒体读取权限，请允许。
2. 启动后默认进入本机公共目录。
3. 从顶部选择**网络 / SMB**；未配置时选择**去配置**，或在左侧 Dock 打开**连接管理**新增、编辑、删除 SMB。
4. 在左侧 Dock 打开**设置**，可调整显示字体缩放。

## 开发命令速查

```powershell
# 调试构建与安装
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug

# 清理构建产物
.\gradlew.bat clean

# 直接运行到已连接设备
.\gradlew.bat :app:connectedDebugAndroidTest

# 查看所有可用任务
.\gradlew.bat tasks

# 静态检查（lint）
.\gradlew.bat :app:lintDebug

# 依赖树
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath

# 发布构建（需先配置 key.properties）
.\gradlew.bat :app:assembleRelease

# 发布 AAB（Google Play）
.\gradlew.bat :app:bundleRelease

# ADB 常用
adb devices
adb logcat -s SmbFileSource:D MainViewModel:D  # 查看 SMB 与 ViewModel 日志
adb shell pm clear com.github.gbandszxc.goodtvplorer           # 清空应用数据
adb uninstall com.github.gbandszxc.goodtvplorer                # 卸载
```

Release 构建默认启用 R8 代码压缩、混淆与资源压缩，并按 `armeabi-v7a`、`arm64-v8a` 输出独立 APK。发布后请保留 `app/build/outputs/mapping/release/mapping.txt`，用于还原线上崩溃堆栈。Debug 使用 `.debug` applicationId 后缀，可与 Release 同时安装。

> 提示：Windows 终端使用 PowerShell 时，反斜杠可省略为 `.\gradlew.bat`；若使用 Git Bash，可改用 `./gradlew`。

## 项目结构

```text
├── gradle.properties        # Gradle 共享配置（提交到仓库）
├── app/
│   ├── src/main/java/com/github/gbandszxc/goodtvplorer/
│   │   ├── data/            # 文件源抽象、本地/SMB 实现、数据模型
│   │   │   └── persistence/ # Room 数据库、DAO 与统一存储仓库
│   │   ├── domain/          # 缩略图、缓存、文件类型识别、自定义 Coil Fetcher
│   │   ├── ui/              # Compose 屏幕与组件、主题
│   │   ├── viewmodel/       # MainViewModel、UI 状态与导航
│   │   └── MainActivity.kt
│   └── build.gradle.kts     # 应用模块构建配置
├── build.gradle.kts         # 项目级插件配置
├── gradle.properties        # Gradle JVM 与 Android 配置
├── settings.gradle.kts      # 项目结构
└── docs/
    └── architecture.md      # 详细架构、已实现功能与后续计划
```

## 了解更多

想了解以下内容的实现细节，请参阅 [`docs/architecture.md`](docs/architecture.md)：

- 统一文件源抽象 `FileSource`
- SMB 连接复用、重试与流水线读取
- 缩略图生成策略（EXIF 优先、降采样、并发锁）
- 图片/视频/音频/文本预览实现
- UI 导航与 D-pad 焦点系统
- 完整已实现功能列表

## 后续计划

详见 [`docs/architecture.md#后续计划`](docs/architecture.md#后续计划)。

短期重点：SMB 密码加密、缓存上限与 LRU 清理、SAF/MediaStore 优先、SMB 连接编辑与删除。

## 常见问题

**Q：可以在普通 Android 手机上用吗？**
A：可以安装运行，但所有交互都为电视遥控器设计（D-pad 焦点、OK/Back 键），触屏没有优化。

**Q：为什么首次启动要请求存储权限？**
A：本地文件浏览需要读取设备上的图片、视频、音频和下载目录。

**Q：SMB 密码安全吗？**
A：当前 MVP 版本使用明文存储，建议只在私人可信设备使用。加密存储已列入开发计划。

**Q：SMB 音视频播放会先下载完整文件吗？**
A：不会。Media3 通过 `FileSource.readRange` 按需读取和跳转；仅旧的视频缩略图生成路径仍可能完整缓存文件。
**Q：构建时报 JDK 相关错误怎么办？**
A：检查 `local.properties` 中 `java.home` 是否指向本机 JDK 17 根目录，并确认该目录下有 `bin/java.exe`。

## License

MIT License © 2026 Good TVplorer Contributors
