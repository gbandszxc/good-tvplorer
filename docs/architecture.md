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
- [已实现功能](#已实现功能)
- [后续计划](#后续计划)

## 架构总览

Good TVplorer 采用**单 Activity + Jetpack Compose** 架构，所有界面状态由 `MainViewModel` 集中管理：

```text
MainActivity
└── MainViewModel
    ├── LocalFileSource          本地文件访问
    ├── SmbFileSource (多个)      SMB/NAS 访问
    ├── Room / SQLite            连接、设置与浏览状态持久化
    ├── ThumbnailRepository      缩略图与原图缓存
    ├── AudioCacheManager        音频文件缓存
    └── BrowserMemoryCache       目录列表内存缓存
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
- 当前 SMB 密码仍为 MVP 明文存储，只是介质改为 SQLite；后续加密必须封装在同一持久化层，不能改变上层调用方式。

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

- `LocalFileSource`：基于 `java.io.File` 和 Android 公共目录 API，支持范围读取与流式复制。
- `SmbFileSource`：基于 [SMBJ](https://github.com/hierynomus/smbj)，支持连接复用、重试与流水线读取。

文件模型 `FileItem` 统一了本地与 SMB 的元数据（名称、大小、修改时间、类型），并通过 `cacheKey()` 为缓存提供稳定键值。

## SMB 实现

### 连接复用与生命周期

`SmbFileSource` 内部维护一个 `ReusableResource<SmbResources>` 池，每个 SMB 连接包含 client、connection、session、share 四层对象：

- **空闲检测**：`IdleResourceVerifier` 记录最后活跃时间，超过 5 秒未使用则视为不可用，下次操作自动重建连接。
- **引用计数**：文件流持有连接 lease，流关闭时释放，避免使用中连接被回收。
- **自动重试**：对 `IOException`、`TransportException`、非 API 的 `SMBRuntimeException` 执行一次重连重试。

### 读取优化

- **范围读取**：`readRange` 按协商的 `maxReadSize` 分块读取，支持协程取消。
- **流水线复制**：当 SMB 文件 ≥ 4MB 时，使用 SMBJ 的 `AsyncFileReader` 发起最多 4 个预读请求，边读边写入本地缓存，提升大文件缓存吞吐量。
- **两种连接模式**：
  - 缩略图/列表使用**长连接**连接池；
  - 文件打开/复制操作可选择 `freshConnectionPerOperation` 以避免与列表浏览争用连接。

### 身份验证

SMB 连接信息 `SmbConnectionInfo` 保存在统一 Room / SQLite 数据库中（当前为 MVP 明文存储，后续改为加密）。

## 缩略图与缓存

缩略图与原图缓存统一放在 `context.cacheDir` 下：

| 目录 | 用途 |
|------|------|
| `image-thumbs/` | 图片缩略图（JPEG，最大边 640px） |
| `image-cache/` | 原图缓存，用于大图预览与缩略图生成 |
| `media-preview/` | 视频/音频缓存文件，用于取封面或播放 |
| `covers/` | 音频内嵌封面 |
| `video-frames/` | 视频关键帧缩略图 |

### 缩略图生成策略

1. **EXIF 缩略图优先**：读取 JPEG 前 256KB，解析 IFD1 提取内置缩略图，避免全图下载。若 EXIF 缩略图不存在或损坏，则进入下一步。
2. **本地缩略图回退**：无 EXIF 缩略图时，先缓存原图，再按 640px 最大边降采样压缩为 JPEG（质量 70%）。
3. **并发控制**：按文件 key 加 `Mutex` 锁，避免同一文件重复下载或生成。

### 图片加载

自定义 Coil3 Fetcher `FileSourceImageFetcher` 直接通过 `FileSource.openStream()` 为图片提供 `ImageSource`，无需在加载大图前复制完整文件到本地。

## 媒体预览

| 类型 | 实现方式 |
|------|----------|
| 图片 | Coil3 加载 `ImageModel`，占位图使用已生成的缩略图 |
| 视频 | 缓存文件到本地后，使用 `ThumbnailUtils.createVideoThumbnail` 取帧（Android 10+） |
| 音频 | 缓存文件到本地，通过 `MediaMetadataRetriever` 提取内嵌封面；播放使用 Media3 ExoPlayer |
| 文本 | `readPrefix` 读取前 1MB，UTF-8 解码，超过 1MB 显示截断提示 |

> 当前视频预览会先缓存完整文件再取帧，后续计划：
> - 对 SMB 视频使用 `readRange` 只读取文件头部（包含 moov / ftyp 等元数据盒），尝试让 MediaMetadataRetriever 基于部分数据取帧；
> - 或让 NAS / SMB 服务端提供缩略图（如 Windows 共享的 `thumb.db`、Synology 缩略图 API 等），避免下载任何视频数据。

## UI 与导航

- **导航模型**：`Screen` 密封类表示 Home / Browser / ImagePreview / TextPreview / AudioPreview / VideoPreview。
- **返回处理**：`BackHandler` 统一拦截返回键，根据当前屏幕执行返回上级目录或退出预览。
- **焦点系统**：列表/网格项使用 `focusable()` + `onFocusChanged` + 自定义 `FocusSurface`，适配电视 D-pad；聚焦时高亮边框与背景变色。
- **浏览模式**：列表与网格可切换；右侧固定 260dp 预览面板显示当前焦点文件信息。
- **显示设置**：支持全局字体缩放，通过 `CompositionLocalProvider(LocalDensity)` 注入。
- **浏览恢复**：最近目录与目录焦点锚点持久化到 SQLite；切换来源或重启后可恢复上次目录和默认焦点。

## 已实现功能

- 首页入口：本地文件、添加 SMB、已保存 SMB 连接列表。
- 本地文件：从 App files、Download、Movies、Pictures、Music 开始浏览。
- SMB：保存名称、Host、Port、Share、用户名、密码、Domain，浏览共享目录。
- 统一文件源接口：`FileSource`、`LocalFileSource`、`SmbFileSource`、`FileItem`、`FileHandle`。
- D-pad 交互：列表项和按钮可聚焦，焦点有明显视觉反馈，OK 打开，Back 返回。
- 浏览模式：列表 / 网格切换，右侧快速预览当前焦点文件。
- 状态反馈：加载中、空目录、错误提示、刷新。
- 图片：本地/SMB 图片预览；SMB 列表缩略图先缓存到 app cache 再交给 Coil。
- 视频：本地/SMB 视频缩略图预览；SMB 视频缩略图 MVP 会先缓存文件。
- 文本：UTF-8 预览，最多读取前 1MB，并提示截断。
- 音频：封面预览；Media3/ExoPlayer 播放；SMB 音频先缓存到 app cache。

## 后续计划

按优先级大致排序：

1. **安全**：SMB 密码从明文 SQLite 字段改为 Android Keystore 保护的加密值。
2. **缓存治理**：增加缓存大小上限与 LRU 清理策略。
3. **权限演进**：本地文件改为 SAF / MediaStore 优先，减少对传统外部存储权限的依赖。
4. **SMB 连接管理**：收藏服务器、删除连接、编辑连接。
5. **文件操作**：复制、移动、删除文件。
6. **图片体验**：图片预览支持同目录左右切换。
7. **视频播放器**：接入 Media3 全功能视频播放器。
8. **测试**：补充最小化单元测试与 instrumentation 冒烟测试。

也欢迎通过 Issue 或 PR 提出新的方向。
