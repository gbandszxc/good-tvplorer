# SMB 大图浏览性能设计

## 目标

让 `pictures/s5m2` 中约 12–13 MB、6000×4000 的 JPEG 在 Android TV 上稳定浏览：不因取消缩略图闪退，返回预览不重载目录，大图缩略图只跨网络读取一次，SMB 超时可被识别并快速恢复。

## 方案

### 稳定性与导航

- `RefCountedRequestRegistry.cancelAll()` 先从注册表移除请求，再取消 Job，避免 Job 的 `finally` 在迭代期间修改同一个 Map。
- 进入预览前保留当前 `BrowserState`；从预览返回时只切换 `Screen`，不再次调用 SMB `list()`。
- 已访问目录的 `BrowserState` 在当前 ViewModel 会话中按来源和路径缓存；返回上一级直接恢复，用户点击“刷新”时才强制重新 `list()`。

### 图片缓存

- 缩略图缓存键继续使用来源、路径、大小和修改时间，不新增数据库。
- 先读取最多 256 KiB JPEG 前缀并解析 APP1/EXIF IFD1；存在内嵌 JPEG 缩略图时直接缓存它。实测目标相机文件的内嵌缩略图位于前 38 KiB 内。
- 没有有效内嵌缩略图时，首次请求才流式复制到 App cache 的原图缓存文件，采用临时文件加原子提交，避免大 `ByteArray`。
- 从本地缓存文件使用采样解码生成 640px JPEG 缩略图。
- 原图预览优先读取同一原图缓存文件；未缓存时才走现有 SMB 流。
- 使用已有文件缓存和 Coil，不引入第三方缓存框架；EXIF 解析器只处理 JPEG APP1 中 IFD1 的 `JPEGInterchangeFormat`/`JPEGInterchangeFormatLength`，严格验证大小端、偏移和长度边界。
- 原图放在 Android `cacheDir`，由平台在存储压力下清理；不实现会与正在读取文件竞争的自定义 LRU，也不做数据库索引。
- 同一图片缓存键使用轻量 `Mutex` 合并并发请求，避免同一原图重复下载；不同图片仍可并发。

### SMB 生命周期

- 继续复用现有 SMB 资源，不为每张图片重新认证。
- 元数据/原图与缩略图可使用独立连接，避免大文件读取阻塞目录操作。
- 超时或传输异常使对应连接代际失效并只重试一次。
- 添加不含凭据的结构化耗时日志：连接、目录读取、范围读取、流读取、缓存命中与字节数。

## 验收

- 并发取消回归测试不再抛出 `ConcurrentModificationException`。
- 大于 12 MB 的 JPEG 能生成并命中磁盘缩略图，不再每次读取 12 MB 后丢弃。
- 从图片预览返回不执行新的目录列表请求，并恢复原列表内容。
- 单元测试全部通过，`assembleDebug` 成功。
- 在 MuMu 的 `pictures/s5m2` 路径验证列表、打开大图、返回和上一级，日志中无 FATAL、OOM。
