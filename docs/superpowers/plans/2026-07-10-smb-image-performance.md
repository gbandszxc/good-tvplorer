# SMB 图片性能优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让约 1000 张、单张约 10 MB 的 SMB 图片目录只按可见范围加载缩略图，并通过连接复用和流式解码显著缩短原图打开时间。

**Architecture:** `SmbFileSource` 懒加载并复用 SMB 客户端、连接、会话和共享，提供有界范围读取与可关闭流。缩略图由 Compose 可见生命周期驱动，最多读取远端前 4 MB；原图交给现有 Coil 自定义 Fetcher 按屏幕尺寸解码和缓存。

**Tech Stack:** Kotlin 2.3、Android 23+、Jetpack Compose、Coroutines、Coil 3.4、SMBJ 0.14、JUnit 4/Kotlin Test

## Global Constraints

- 开发与验证命令必须使用 Windows PowerShell 语法。
- Java 源码与字节码目标保持 17；若默认 JDK 不兼容，使用 `C:\D\Develop\Java\` 下的 JDK 17。
- 不新增运行时依赖；仅增加 Kotlin/JUnit 单元测试依赖。
- 不改变现有页面布局、颜色、焦点或遥控器操作。
- 缩略图单次生命周期累计最多读取 4 MB，不能回退为完整原图复制。
- 同一 SMB 文件源必须复用连接；连接型错误最多自动重连一次。
- 所有生产代码改动遵循测试先失败、最小实现、测试再通过。
- 完成后必须执行 `./gradlew.bat testDebugUnitTest assembleDebug --console=plain`。

---

### Task 1: 缓存身份与测试基础

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/goodtvplorer/data/FileModels.kt`
- Create: `app/src/test/java/com/goodtvplorer/data/FileModelsTest.kt`

**Interfaces:**
- Consumes: `FileItem`, `FileHandle`
- Produces: `FileItem.cacheKey(): String`，供缩略图、Coil Keyer 和 ViewModel 使用

- [ ] **Step 1: 增加测试依赖并写失败测试**

```kotlin
// app/build.gradle.kts
testImplementation(kotlin("test-junit"))
```

```kotlin
class FileModelsTest {
    @Test fun `cache key changes with size and modification time`() {
        val base = item(size = 10, modified = 20)
        assertNotEquals(base.cacheKey(), base.copy(size = 11).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(modifiedAtMillis = 21).cacheKey())
    }
}
```

- [ ] **Step 2: 运行测试并确认因 `cacheKey` 不存在而失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.goodtvplorer.data.FileModelsTest" --console=plain`

Expected: `Unresolved reference 'cacheKey'`。

- [ ] **Step 3: 添加最小缓存键实现**

```kotlin
fun FileItem.cacheKey(): String = listOf(
    handle.sourceKey,
    handle.path,
    size?.toString().orEmpty(),
    modifiedAtMillis?.toString().orEmpty(),
).joinToString("|")
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.goodtvplorer.data.FileModelsTest" --console=plain`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交**

```powershell
git add app/build.gradle.kts app/src/main/java/com/goodtvplorer/data/FileModels.kt app/src/test/java/com/goodtvplorer/data/FileModelsTest.kt
git commit -m "test: 覆盖图片缓存身份"
```

### Task 2: SMB 连接复用、范围读取与原子复制

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/data/FileSource.kt`
- Modify: `app/src/main/java/com/goodtvplorer/data/LocalFileSource.kt`
- Modify: `app/src/main/java/com/goodtvplorer/data/SmbFileSource.kt`
- Create: `app/src/test/java/com/goodtvplorer/data/SmbRetryTest.kt`

**Interfaces:**
- Consumes: `SmbConnectionInfo`, SMBJ 0.14
- Produces: `readRange(path, offset, maxBytes)`, `openStream(path)`, `close()` 与 `retryConnectionOnce`

- [ ] **Step 1: 写重连次数失败测试**

```kotlin
@Test fun `connection operation retries only once`() {
    var attempts = 0
    assertFails { retryConnectionOnce({ true }) { attempts++; error("offline") } }
    assertEquals(2, attempts)
}

@Test fun `non retryable operation is not repeated`() {
    var attempts = 0
    assertFails { retryConnectionOnce({ false }) { attempts++; error("denied") } }
    assertEquals(1, attempts)
}
```

- [ ] **Step 2: 运行测试并确认因函数不存在而失败**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.goodtvplorer.data.SmbRetryTest" --console=plain`

Expected: `Unresolved reference 'retryConnectionOnce'`。

- [ ] **Step 3: 扩展文件源接口及本地实现**

```kotlin
interface FileSource {
    suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray
    suspend fun readPrefix(path: String, maxBytes: Int) = readRange(path, 0, maxBytes)
    suspend fun openStream(path: String): InputStream
    suspend fun copyTo(path: String, target: File)
}
```

`LocalFileSource` 使用 `RandomAccessFile.seek(offset)` 实现范围读取，使用 `File.inputStream()` 实现流。

- [ ] **Step 4: 实现一次重试与持久 SMB 资源**

```kotlin
internal inline fun <T> retryConnectionOnce(
    retryable: (Throwable) -> Boolean,
    reset: () -> Unit = {},
    block: () -> T,
): T {
    try { return block() } catch (first: Throwable) {
        if (!retryable(first) || first is CancellationException) throw first
        reset()
        return block()
    }
}
```

`SmbFileSource` 用同步锁保护懒创建资源，复用 `SMBClient/Connection/Session/DiskShare`；配置协商缓冲区和有限超时。`readRange` 调用 `SmbFile.read(buffer, offset, 0, maxBytes)`；`openStream` 返回关闭时同时关闭远端文件句柄的包装流；`close()` 逆序关闭所有 SMB 资源。

- [ ] **Step 5: 将 `copyTo` 改为临时文件后重命名**

```kotlin
val partial = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
try {
    openStream(path).use { input -> partial.outputStream().use(input::copyTo) }
    check(partial.renameTo(target)) { "无法提交缓存文件" }
} finally {
    partial.delete()
}
```

- [ ] **Step 6: 运行单测与编译**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.goodtvplorer.data.SmbRetryTest" compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 提交**

```powershell
git add app/src/main/java/com/goodtvplorer/data app/src/test/java/com/goodtvplorer/data/SmbRetryTest.kt
git commit -m "perf: 复用 SMB 连接与范围读取"
```

### Task 3: 有界缩略图生成与请求去重

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/domain/ThumbnailRepository.kt`
- Modify: `app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt`
- Create: `app/src/test/java/com/goodtvplorer/domain/ThumbnailRepositoryTest.kt`

**Interfaces:**
- Consumes: `FileItem.cacheKey()`, `FileSource.readRange`
- Produces: `thumbnailFile(source, item): File?`, `requestThumbnail(item)`, `releaseThumbnail(item)`

- [ ] **Step 1: 写失败测试，证明 4 MB 后停止且不复制原图**

```kotlin
@Test fun `thumbnail failure reads at most four MiB and never copies original`() = runBlocking {
    val source = RecordingFileSource(totalSize = 10 * 1024 * 1024)
    val result = repository(decoder = { null }).thumbnailFile(source, imageItem)
    assertNull(result)
    assertEquals(4 * 1024 * 1024L, source.totalBytesRead)
    assertEquals(0, source.copyCount)
}
```

- [ ] **Step 2: 运行测试并确认旧实现读取策略不满足断言**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.goodtvplorer.domain.ThumbnailRepositoryTest" --console=plain`

Expected: FAIL，旧实现回退到 `copyTo` 或签名不匹配。

- [ ] **Step 3: 实现分段范围读取和可空结果**

```kotlin
private val limits = intArrayOf(512 * 1024, 2 * 1024 * 1024, 4 * 1024 * 1024)
var bytes = ByteArray(0)
for (limit in limits) {
    val chunk = source.readRange(item.handle.path, bytes.size.toLong(), limit - bytes.size)
    bytes += chunk
    decode(bytes)?.let { return writeThumbnailAtomically(item.cacheKey(), it) }
    if (chunk.size < limit - bytes.size) break
}
return null
```

实际实现须在计算期望长度前保存 `requested`，避免追加后长度变化；缩略图按目标尺寸计算 `inSampleSize`，使用 `RGB_565` 和 JPEG 质量 70。

- [ ] **Step 4: 在 ViewModel 中实现可见项引用计数、同键去重和三个并发许可**

`requestThumbnail` 对同键只创建一个 Job；`releaseThumbnail` 在引用数归零时取消；目录切换、打开图片和 `onCleared` 取消全部请求。完成结果在约 50 ms 窗口合并后一次更新 `thumbnails`。

- [ ] **Step 5: 运行缩略图测试与完整单测**

Run: `.\gradlew.bat testDebugUnitTest --console=plain`

Expected: `BUILD SUCCESSFUL`，且记录读取总量为 4 MB、复制次数为 0。

- [ ] **Step 6: 提交**

```powershell
git add app/src/main/java/com/goodtvplorer/domain/ThumbnailRepository.kt app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt app/src/test/java/com/goodtvplorer/domain/ThumbnailRepositoryTest.kt
git commit -m "perf: 按需生成有界 SMB 缩略图"
```

### Task 4: Coil SMB 原图流式加载

**Files:**
- Create: `app/src/main/java/com/goodtvplorer/domain/ImageModel.kt`
- Create: `app/src/main/java/com/goodtvplorer/domain/FileSourceImageFetcher.kt`
- Modify: `app/src/main/java/com/goodtvplorer/MainActivity.kt`
- Modify: `app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt`
- Modify: `app/src/main/java/com/goodtvplorer/ui/preview/PreviewScreens.kt`
- Create: `app/src/test/java/com/goodtvplorer/domain/ImageModelTest.kt`

**Interfaces:**
- Consumes: `FileSource.openStream`, `FileItem.cacheKey`
- Produces: `ImageModel`, Coil `Keyer<ImageModel>`、`Fetcher.Factory<ImageModel>`

- [ ] **Step 1: 写缓存键失败测试**

```kotlin
@Test fun `image model delegates stable cache identity`() {
    assertEquals(imageItem.cacheKey(), ImageModel(source, imageItem).cacheKey)
}
```

- [ ] **Step 2: 运行测试并确认 `ImageModel` 不存在**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.goodtvplorer.domain.ImageModelTest" --console=plain`

Expected: `Unresolved reference 'ImageModel'`。

- [ ] **Step 3: 实现 Coil 模型、Keyer 和 Fetcher**

```kotlin
data class ImageModel(val source: FileSource, val item: FileItem) {
    val cacheKey = item.cacheKey()
}

class ImageModelKeyer : Keyer<ImageModel> {
    override fun key(data: ImageModel, options: Options) = data.cacheKey
}
```

Fetcher 在 IO 上调用 `source.openStream(path)`，包装为 Okio `BufferedSource` 和 Coil `ImageSource`，返回 `SourceFetchResult`；SMB 使用 `DataSource.NETWORK`，本地使用 `DataSource.DISK`。

- [ ] **Step 4: 在 MainActivity 注册现有 Coil 单例组件**

```kotlin
SingletonImageLoader.setSafe { context ->
    ImageLoader.Builder(context).components {
        add(ImageModelKeyer())
        add(FileSourceImageFetcher.Factory())
    }.build()
}
```

- [ ] **Step 5: 原图页使用流模型和缩略图渐进替换**

`prepareImage` 不再调用 `thumbnailFile` 后再 `imageFile`，而是立即设置 `ImageModel` 与已有缩略图。`ImagePreview` 先绘制缩略图，再由 Coil 原图请求覆盖；加载失败保留缩略图并显示错误。

- [ ] **Step 6: 运行测试和编译**

Run: `.\gradlew.bat testDebugUnitTest compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7: 提交**

```powershell
git add app/src/main/java/com/goodtvplorer/MainActivity.kt app/src/main/java/com/goodtvplorer/domain app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt app/src/main/java/com/goodtvplorer/ui/preview/PreviewScreens.kt app/src/test/java/com/goodtvplorer/domain/ImageModelTest.kt
git commit -m "perf: 使用 Coil 流式加载 SMB 原图"
```

### Task 5: Compose 可见项生命周期与设计规范

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/ui/browser/BrowserScreen.kt`
- Modify: `app/src/main/java/com/goodtvplorer/MainActivity.kt`
- Modify: `DESIGN.md`

**Interfaces:**
- Consumes: `requestThumbnail(item)`, `releaseThumbnail(item)`
- Produces: 可见项驱动的缩略图请求生命周期

- [ ] **Step 1: 将回调贯通到 `MediaThumb`**

`BrowserScreen` 新增 `onThumbnailVisible` 和 `onThumbnailHidden`。图片无缓存时用 `DisposableEffect(item.cacheKey())` 请求，离开组合时释放；非图片项不触发网络。

- [ ] **Step 2: 保持 UI 结构不变并同步 DESIGN.md**

```text
[IMG] [IMG] [IMG]  ->  [缩略图] [缩略图] [IMG]
只请求 LazyGrid/LazyColumn 当前组合的图片；离屏或切目录取消。

[已有缩略图] -> [适配电视屏幕的清晰图]
```

在 `DESIGN.md` 的“文件网格卡片”“快速预览面板”“状态”中记录按需加载、占位和渐进替换规则。

- [ ] **Step 3: 编译并检查差异**

Run: `.\gradlew.bat compileDebugKotlin --console=plain`

Expected: `BUILD SUCCESSFUL`。

Run: `git diff --check`

Expected: 无输出，退出码 0。

- [ ] **Step 4: 提交**

```powershell
git add app/src/main/java/com/goodtvplorer/ui/browser/BrowserScreen.kt app/src/main/java/com/goodtvplorer/MainActivity.kt DESIGN.md
git commit -m "perf: 仅加载可见 SMB 缩略图"
```

### Task 6: 完整验证与性能审计

**Files:**
- Verify: `app/src/main/java/com/goodtvplorer/**/*.kt`
- Verify: `app/src/test/java/com/goodtvplorer/**/*.kt`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: Tasks 1–5 的全部产物
- Produces: 可编译 Debug APK 与需求逐项证据

- [ ] **Step 1: 运行完整测试和 Debug 打包**

Run: `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`

Expected: `BUILD SUCCESSFUL`，所有测试 0 失败，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 2: 静态审计禁止行为**

Run: `rg -n "take\(60\)|cacheThumbnails|imageFile\(source, handle\)" app/src/main/java`

Expected: 不再存在固定 60 张预加载和缩略图回退整图路径。

- [ ] **Step 3: 审计 Git 和 APK**

Run: `git status --short`

Expected: 无未提交生产代码。

Run: `Get-Item app/build/outputs/apk/debug/app-debug.apk | Select-Object FullName,Length,LastWriteTime`

Expected: APK 存在且时间为本轮构建时间。

- [ ] **Step 4: 真实 NAS 验收建议**

在约 1000 张、每张约 10 MB 的目录中验证：目录列表先出现；请求量只随可见项增长；滚动离屏后请求取消；打开原图时没有继续批量下载其他图片；重复打开命中缓存。真实无线吞吐和 NAS 响应时间只能在目标设备上测量，代码侧以请求数量、读取上限、连接次数和自动化测试作为可复现证据。
