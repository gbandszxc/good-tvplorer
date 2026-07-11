# SMB Image Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 SMB 大图浏览闪退、重复目录加载和大于 12 MB JPEG 的重复无效读取。

**Architecture:** 保留现有 `FileSource`、Coil 和文件缓存结构。取消逻辑在共享注册表根治；导航复用现有 BrowserState；图片从 SMB 流式落盘一次，再由缩略图和原图共同使用；SMB 资源继续按代际复用并记录耗时。

**Tech Stack:** Kotlin、Coroutines、Jetpack Compose、Coil 3、SMBJ、JUnit、Gradle。

## Global Constraints

- 在当前 `master` 分支直接修改，不创建工作树或功能分支。
- 不引入新依赖或新数据库。
- 保留用户现有未提交改动并在其基础上修改。
- 所有生产行为修改先写失败测试并验证失败，再实现最小修复。
- 完成后运行全部单元测试和 `assembleDebug`，再用 MuMu ADB 回归。

---

### Task 1: 取消安全与预览返回状态

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/viewmodel/RefCountedRequestRegistry.kt`
- Modify: `app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt`
- Create/Modify: `app/src/test/java/com/goodtvplorer/viewmodel/RefCountedRequestRegistryTest.kt`
- Create/Modify: `app/src/test/java/com/goodtvplorer/viewmodel/MainViewModelTest.kt`

**Interfaces:**
- Produces: `cancelAll()` 可重入取消；预览返回复用已有 `BrowserState`。

- [ ] 写回归测试：Job 的 `finally` 删除注册项时 `cancelAll()` 不抛异常。
- [ ] 运行定向测试，确认以 `ConcurrentModificationException` 失败。
- [ ] 最小修改 `cancelAll()`：复制/清空注册表后再取消 Job。
- [ ] 写导航状态测试：图片预览返回不调用 `FileSource.list()`，BrowserState 不被清空。
- [ ] 运行测试确认失败，再实现预览来源 BrowserState/路径恢复。
- [ ] 写失败测试并实现按来源/路径保存的会话内目录快照；刷新移除当前快照，返回上一级复用快照。
- [ ] 运行 viewmodel 定向测试并确认通过。

### Task 2: 大 JPEG 一次下载并复用缓存

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/domain/ThumbnailRepository.kt`
- Modify: `app/src/main/java/com/goodtvplorer/domain/ImageModel.kt`
- Modify: `app/src/main/java/com/goodtvplorer/domain/FileSourceImageFetcher.kt`
- Modify: `app/src/test/java/com/goodtvplorer/domain/ThumbnailRepositoryTest.kt`
- Modify/Create: `app/src/test/java/com/goodtvplorer/domain/ImageModelTest.kt`

**Interfaces:**
- Produces: `ThumbnailRepository.cachedImageFile(source, item): File`；`ImageModel` 可携带已缓存原图。

- [ ] 写失败测试：13 MB 文件只调用一次流式复制并能生成持久缩略图。
- [ ] 写纯 JVM 失败测试：从 II/MM endian 的 JPEG APP1/EXIF IFD1 提取内嵌 JPEG，并拒绝越界偏移。
- [ ] 实现最多 256 KiB 前缀的 EXIF 内嵌缩略图快速路径。
- [ ] 写失败测试：第二次请求直接命中缩略图，不再次读取源。
- [ ] 实现原图临时文件、原子提交、采样解码；依赖 Android `cacheDir` 平台清理，不实现自定义 LRU。
- [ ] 写失败测试：同一缓存键并发请求只读取/复制源文件一次，不同键不互相串行。
- [ ] 写失败测试：原图 fetcher 在缓存文件存在时不打开 SMB 流。
- [ ] 实现 `ImageModel`/fetcher 的缓存文件优先路径。
- [ ] 运行 domain 定向测试并确认通过。

### Task 3: SMB 超时恢复与可观测性

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/data/SmbFileSource.kt`
- Modify/Create: `app/src/test/java/com/goodtvplorer/data/SmbRetryTest.kt`

**Interfaces:**
- Produces: 一次重试且旧连接代际立即失效；日志不包含主机凭据。

- [ ] 写失败测试：超时异常使旧资源失效，下一次尝试获取新代际。
- [ ] 保留一次重试，收紧失效顺序并确保打开文件被关闭。
- [ ] 添加连接、list、readRange 和 stream 的耗时/字节日志。
- [ ] 运行 data 定向测试并确认通过。

### Task 4: 集成与设备回归

**Files:**
- Modify if needed: `DESIGN.md`

**Interfaces:**
- Consumes: Tasks 1–3 的生产代码和测试。

- [ ] 运行 `gradlew.bat testDebugUnitTest`，修复所有回归。
- [ ] 运行 `gradlew.bat assembleDebug`。
- [ ] 安装 Debug APK 到 `emulator-5554`。
- [ ] 清空 logcat，回归 `pictures/s5m2/2024东农`：加载、打开 `P1000705.JPG`、返回、上一级。
- [ ] 检查进程未重启，日志无 FATAL/OOM，并记录目录与图片加载耗时。
