# Good TVplorer

<p align="center">
  <img src="docs/raw/icon/raw_icon.png" width="200" alt="Good TVplorer">
</p>

Good TVplorer 是面向 Android TV、电视盒子和投影设备的遥控器优先文件浏览器，用于统一访问本地存储与 SMB / NAS 媒体库。

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin)
![AGP](https://img.shields.io/badge/AGP-9.2.1-3DDC84?logo=android)
![minSdk](https://img.shields.io/badge/minSdk-23-3DDC84)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84)

## 核心能力

- **电视端交互**：提供 Leanback 启动入口、完整 D-pad 焦点导航、OK / Back 键操作和基础触屏兼容。
- **统一文件浏览**：使用同一套列表、网格和快速预览界面访问本地目录与 SMB 共享。
- **SMB 连接管理**：支持新增、编辑和删除连接，配置 Host、Port、Share、用户名、密码与 Domain。
- **网络读取优化**：复用 SMB 会话，支持失效重连、单次重试、范围读取和大文件流水线复制。
- **媒体预览**：支持图片浏览、音频播放与 LRC 歌词、视频播放与 SRT / 内嵌字幕、文本行号与翻页。
- **状态恢复**：使用 Room 保存显示设置、最近目录和 D-pad 焦点锚点。

## 安全与数据

SMB 密码在写入 Room / SQLite 前使用 AES-256-GCM 加密，密钥由 Android Keystore 生成并保存在当前设备。每条密文使用独立随机 IV，并绑定对应的连接 ID；从旧版本升级时，已有明文密码会在数据库迁移中自动转换。

`good_tvplorer.db` 已从 Android 云备份和设备间迁移中排除，因此 SMB 连接、显示设置与浏览恢复状态不会自动转移到新设备。应用建立 SMB 会话时仍需在进程内临时解密密码；该机制用于降低数据库被离线复制后的凭据暴露风险，不能替代可信设备、系统锁屏和操作系统安全更新。

应用不会主动记录密码，相关错误信息也不包含凭据内容。

## 运行环境

### 目标设备

- Android TV、电视盒子或投影设备
- Android 6.0（API 23）及以上
- `armeabi-v7a` 或 `arm64-v8a`
- 遥控器 / D-pad；触屏为兼容输入方式

### 开发环境

- Windows PowerShell
- JDK 17
- Android SDK API 36
- Android Studio，或项目内置的 Gradle Wrapper

在项目根目录创建已被 Git 忽略的 `local.properties`：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
java.home=C\:\\path\\to\\jdk-17
```

发布构建还需要在 `key.properties` 中提供签名配置。不要提交本机路径、密码或签名文件。

## 构建与验证

```powershell
# Debug APK
.\gradlew.bat :app:assembleDebug

# JVM 单元测试
.\gradlew.bat :app:testDebugUnitTest

# 已连接设备上的 instrumentation 测试
.\gradlew.bat :app:connectedDebugAndroidTest

# Android Lint
.\gradlew.bat :app:lintDebug

# 安装 Debug 版本
.\gradlew.bat :app:installDebug
```

Debug 使用 `com.github.gbandszxc.goodtvplorer.debug` application ID，可与 Release 版本同时安装。Debug APK 位于 `app/build/outputs/apk/debug/`。

Release 默认启用 R8 代码压缩、混淆和资源压缩，并按 ARM ABI 输出独立 APK：

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

发布后应妥善保存 `app/build/outputs/mapping/release/mapping.txt`，以便还原线上崩溃堆栈。

## 使用说明

1. 首次启动时允许应用读取媒体文件。
2. 通过顶部 Dock 在本机和网络来源之间切换。
3. 尚未配置网络来源时，选择“去配置”，或从左侧 Dock 打开连接管理。
4. 从设置页调整字体缩放、查看缓存信息和项目版本。

## 项目结构

```text
app/src/main/java/com/github/gbandszxc/goodtvplorer/
├── data/            文件源、SMB 实现与数据模型
│   └── persistence/ Room 实体、DAO、迁移与 Repository
├── domain/          缩略图、缓存、元数据与媒体适配
├── ui/              Compose 屏幕、组件与主题
├── viewmodel/       浏览、导航和预览状态
├── MainActivity.kt
├── ConnectionManagementActivity.kt
└── SettingsActivity.kt
```

实现细节、数据流和设计约束参见：

- [`docs/architecture.md`](docs/architecture.md)：架构、持久化、SMB 与媒体实现
- [`DESIGN.md`](DESIGN.md)：电视端 UI、焦点和交互规范
- [`PRODUCT.md`](PRODUCT.md)：产品定位与设计原则

## 路线图

当前重点包括：

1. 缓存容量上限与 LRU 治理；
2. 使用 SAF / MediaStore 扩展本地文件访问；
3. 复制、移动和删除等文件操作；
4. 更多歌词、字幕格式及字幕轨选择；
5. 扩充设备端回归测试。

详细说明参见 [`docs/architecture.md#路线图`](docs/architecture.md#路线图)。

## License

MIT License © 2026 Good TVplorer Contributors
