# Good TVplorer 架构与实现

本文档面向希望理解项目实现或参与开发的读者。如果你想快速运行项目，请先看根目录的 [README](../README.md)。

## 目录

- [架构总览](#架构总览)
- [统一持久化](#统一持久化)
- [统一文件源抽象](#统一文件源抽象)
- [SMB 实现](#smb-实现)
- [缩略图与缓存](#缩略图与缓存)
- [媒体预览](#媒体预览)
- [UI 与导航](#ui-与导航)
- [开发与构建](#开发与构建)
- [已实现功能](#已实现功能)
- [路线图](#路线图)

## 架构总览

Good TVplorer 使用 Jetpack Compose 构建界面，并按职责拆分为三个 Activity。文件浏览和媒体预览状态由 `MainViewModel` 集中管理，连接与设置页面直接依赖各自的 Repository：

```text
Application
├── MainActivity
│   └── MainViewModel
│       ├── LocalFileSource / SmbFileSource
│       ├── ThumbnailRepository / PreviewMetadataRepository
│       └── Room Repository
├── ConnectionManagementActivity
│   └── SmbConnectionRepository
└── SettingsActivity
    ├── DisplaySettingsRepository
    └── CacheRepository
```

- **data 层**：文件源抽象、数据模型、本地/SMB 访问实现。
- **domain 层**：文件类型识别、缩略图生成、自定义 Coil Fetcher、格式化工具。
- **ui 层**：Compose 屏幕与组件、主题。
- **viewmodel 层**：UI 状态、导航、资源生命周期管理。

## 统一持久化

所有可恢复的业务状态都由 `data/persistence` 中的 Room 数据库 `good_tvplorer.db` 管理。该目录是应用唯一的持久化入口：DAO 只供 Repository 使用，UI 和 ViewModel 只能依赖 Repository。

| 表 | 用途 |
| --- | --- |
| `smb_connections` | SMB 连接配置 |
| `app_preferences` | 应用偏好（当前为字体缩放） |
| `browser_locations` | 每个本机 / SMB 来源的最近目录与访问时间 |
| `browser_focus_anchors` | 每级目录最后进入的子目录，用于恢复 D-pad 焦点 |

- 删除 SMB 连接时，连接与其最近目录、焦点锚点在同一 SQLite 事务中删除。
- 首次切换到 SQLite 不迁移旧 DataStore；旧文件保留但不再读取，新数据库使用默认设置和空连接列表。
- 后续新增持久化需求必须先在此目录扩展 Entity、DAO、Repository、测试和本节文档；不得新增 DataStore、SharedPreferences、临时配置文件或绕过 Repository 的直接 SQLite 调用。
- `smb_connections.password` 保存版本化的 AES-256-GCM 密文信封；密钥由 Android Keystore 按设备生成，随机 IV 与认证标签随密文存储，连接 ID 作为 AAD。
- `SmbConnectionRepository` 在保存与读取边界完成加解密，`SmbConnectionInfo` 和上层调用方式保持不变。密文格式无效、被篡改或无法认证时直接失败，不回退为明文。
- 数据库版本 1 升级至版本 2 时，在同一迁移事务中加密旧密码；随后执行一次安全删除、WAL checkpoint 和 `VACUUM`，清理数据库文件中的旧值残留。
- 数据库域从 Android 云备份和设备间迁移中排除，避免设备绑定密钥缺失时恢复出无法解密的连接数据。

## 统一文件源抽象

所有文件操作都通过 `FileSource` 接口统一：

```kotlin
interface FileSource {
    val key: String
    val kind: SourceKind
    val title: String

    suspend fun list(path: String): List<FileItem>
    suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray
    suspend fun openStream(path: String): InputStream
    suspend fun copyTo(path: String, target: File)
}
```

实现：

- `LocalFileSource`：以 Android 主要共享存储为逻辑根目录，使用相对路径和 canonical 边界校验，支持范围读取与流式复制。
- `SmbFileSource`：基于 [SMBJ](https://github.com/hierynomus/smbj)，支持连接复用、重试与流水线读取。

文件模型 `FileItem` 统一了本地与 SMB 的元数据（名称、大小、修改时间、类型），并通过 `cacheKey()` 为缓存提供稳定键值。

当前识别的扩展名：

- 图片：JPG / JPEG / PNG / WebP / GIF
- 文本：TXT / LOG / MD / JSON / XML / YML / YAML / INI / CONF
- 音频：MP3 / FLAC / WAV / M4A / AAC / OGG
- 视频：MP4 / MKV / WebM / AVI / MOV / M4V / TS

## SMB 实现

### 连接复用与生命周期

`SmbFileSource` 内部维护一个 `ReusableResource<SmbResources>` 池，每个 SMB 连接持有 client、connection、share：

- **连接复用**：目录与范围读取复用同一连接；图片原图流关闭或取消时强制断开并淘汰其 transport，下一张使用新连接，避免复用被取消读取污染的会话，也避免失效 share 优雅关闭时等待网络超时。
- **自动重试**：对 `IOException`、`TransportException`、非 API 的 `SMBRuntimeException` 执行一次重连重试。

### 读取优化

- **范围读取**：`readRange` 按协商的 `maxReadSize` 分块读取，支持协程取消。
- **流水线复制**：当 SMB 文件 ≥ 4MB 时，使用 SMBJ 的 `AsyncFileReader` 发起最多 4 个预读请求，边读边写入本地缓存，提升大文件缓存吞吐量。
- **流式读取**：打开、范围读取和内部缓存复制共用可复用连接；流关闭或读取失败后淘汰当前连接，避免后续操作复用异常会话。

### 身份验证

SMB 连接信息由 `SmbConnectionRepository` 保存到统一 Room 数据库。密码仅以 Android Keystore 保护的密文落库；建立 SMB 会话时，Repository 返回的模型在应用进程内持有临时明文，并由 `AuthenticationContext` 转换为字符数组完成认证。日志、错误消息和性能事件均不得包含密码。

## 缩略图与缓存

缩略图与原图缓存统一放在 `context.cacheDir` 下：

| 目录 | 用途 |
|------|------|
| `image-thumbs/` | 图片缩略图（JPEG，最大边 640px） |
| `covers/` | 音频内嵌封面 |
| `video-frames/` | 视频关键帧缩略图 |

### 缩略图生成策略

1. **EXIF 缩略图优先**：读取 JPEG 前 256KB，解析 IFD1 提取内置缩略图，避免全图下载。若 EXIF 缩略图不存在或损坏，则进入下一步。
2. **流式缩略图回退**：无 EXIF 缩略图时，先读取图片尺寸，再从文件流按 640px 最大边降采样并压缩为 JPEG（质量 70%），不保留原图缓存。
3. **并发控制**：按文件 key 加 `Mutex` 锁，避免同一文件重复下载或生成。

### 图片加载

自定义 Coil3 Fetcher `FileSourceImageFetcher` 直接通过 `FileSource.openStream()` 为图片提供 `ImageSource`；Coil 磁盘缓存已禁用，SMB 原图不会写入 App cache。全屏查看器的原图请求同时禁用 Coil 内存缓存和硬件位图，切图后只保留当前画面，避免低内存电视连续浏览时累积大尺寸图形缓冲。

## 媒体预览

| 类型 | 实现方式 |
|------|----------|
| 图片 | Coil3 加载 `ImageModel`，占位图使用已生成的缩略图 |
| 视频 | Media3 ExoPlayer + `PlayerView`，支持沉浸控制层、内嵌/SRT 字幕、倍速、seek 与六种画面模式；缩略图由 `MediaMetadataRetriever` 通过 `FileSourceMediaDataSource` 范围读取取帧 |
| 音频 | Media3 ExoPlayer 播放，展示播放器解析的内嵌封面、同名 LRC 歌词并支持 seek |
| 文本 | `readPrefix` 读取前 1MB，UTF-8 解码，显示行号、自动换行和遥控器翻页 |

`FileSourceDataSource` 将 Media3 的读取请求映射为 `FileSource.readRange`，用 256 KiB 内存窗口合并解析器的小范围顺序读取，单次最多读取 1 MiB。SMB 播放和进度跳转不完整缓存媒体文件；歌词与字幕仅加载同目录同名的小文件。

## UI 与导航

- **导航模型**：`Screen` 密封类表示 Network / Browser / ImageViewer / TextPreview / AudioPreview / VideoPreview。
- **返回处理**：`BackHandler` 统一拦截返回键，根据当前屏幕执行返回上级目录或退出预览。
- **焦点系统**：列表/网格项使用 `focusable()` + `onFocusChanged` + 自定义 `FocusSurface`，适配电视 D-pad；聚焦时高亮边框与背景变色。
- **浏览模式**：列表与网格可切换；右侧固定 260dp 预览面板显示当前焦点文件信息。
- **搜索与排序**：按名称递归搜索当前目录树；按名称、大小或修改时间升序 / 降序排列，目录始终优先。
- **路径跳转**：工具栏可直接编辑当前路径并跳转，路径仍经过文件源边界校验。
- **显示设置**：支持全局字体缩放，通过 `CompositionLocalProvider(LocalDensity)` 注入。
- **浏览恢复**：最近目录与目录焦点锚点持久化到 SQLite；切换来源或重启后可恢复上次目录和默认焦点。
- **图片胶卷懒加载**：LazyRow 仅请求可见缩略图；查看器内部切图保留其他可见项的任务，离屏或退出查看器时再取消。

## 开发与构建

### 环境要求

- Windows PowerShell
- JDK 17
- Android SDK API 36
- Android Studio，或项目内置的 Gradle Wrapper

在项目根目录创建已被 Git 忽略的 `local.properties`：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
java.home=C\:\\path\\to\\jdk-17
```

发布构建还需在 `key.properties` 中提供签名配置。不得提交本机路径、密码或签名文件。

### 构建与验证

```powershell
# Debug APK
.\gradlew.bat :app:assembleDebug

# JVM 单元测试
.\gradlew.bat :app:testDebugUnitTest

# 已连接设备上的 instrumentation 测试
.\gradlew.bat :app:connectedDebugAndroidTest

# Android Lint
.\gradlew.bat :app:lintDebug

# 覆盖安装 Debug 版本
.\gradlew.bat :app:installDebug
```

Debug 使用 `com.github.gbandszxc.goodtvplorer.debug` application ID，可与 Release 版本同时安装。Debug APK 位于 `app/build/outputs/apk/debug/`。

Release 默认启用 R8 代码压缩、混淆和资源压缩，并按 ARM ABI 输出独立 APK：

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

发布后应保存 `app/build/outputs/mapping/release/mapping.txt`，用于还原线上崩溃堆栈。

### 项目结构

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

## 已实现功能

- 顶部 Dock：本机与网络来源切换；网络页展示已保存的 SMB 连接。
- 本地文件：从 Android 主要共享存储根目录开始浏览，不能导航到该目录之外。
- SMB：新增、编辑和删除连接，使用加密密码访问共享目录。
- 统一文件源接口：`FileSource`、`LocalFileSource`、`SmbFileSource`、`FileItem`、`FileHandle`。
- D-pad 交互：列表项和按钮可聚焦，焦点有明显视觉反馈，OK 打开，Back 返回。
- 浏览模式：列表 / 网格切换，右侧快速预览当前焦点文件。
- 文件查找：递归搜索当前目录树，支持按名称、大小或修改时间排序及直接输入路径。
- 状态反馈：加载中、空目录、错误提示、刷新。
- 图片：本地/SMB 图片预览、同类型媒体切换和胶卷缩略图懒加载。
- 视频：本地/SMB 流式播放、同名 SRT / 内嵌字幕、倍速、进度控制与六种画面模式；缩略图通过范围读取取帧。
- 文本：UTF-8 预览，最多读取前 1MB，显示行号、自动换行和翻页。
- 音频：本地/SMB 流式播放、内嵌封面、同名 LRC 歌词与进度控制。

## 路线图

按优先级大致排序：

1. **缓存治理**：增加缓存大小上限与 LRU 清理策略。
2. **权限演进**：本地文件改为 SAF / MediaStore 优先，减少对传统外部存储权限的依赖。
3. **文件操作**：复制、移动、删除文件。
4. **连接增强**：增加服务器收藏与最近连接入口。
5. **媒体增强**：支持更多歌词 / 字幕格式与字幕轨选择。
6. **测试**：扩充连接管理、设置、数据库迁移和 D-pad 导航的设备端回归覆盖。

也欢迎通过 Issue 或 PR 提出新的方向。
