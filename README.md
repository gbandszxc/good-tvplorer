# Good TVplorer

Android TV 文件管理器 MVP，使用 Kotlin、Gradle Kotlin DSL、Jetpack Compose、SMBJ、Coil、Media3。

## 构建配置

- JDK：`C:\D\Develop\Java\jdk-17.0.16+8`
- Gradle：Wrapper `9.4.1`
- AGP：`9.2.1`
- `compileSdk` / `targetSdk`：`36`
- `minSdk`：`23`

AGP 9 已内置 Kotlin 支持，因此项目不再应用 `org.jetbrains.kotlin.android`，只保留 Compose Compiler 插件。

## 运行

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

也可以用 Android Studio 打开本目录，在 Android TV 模拟器或真机上运行 `app`。

## MVP 已实现

- 首页：本地文件、添加 SMB、已保存 SMB 连接入口。
- 本地文件：从 App files、Download、Movies、Pictures、Music 开始浏览。
- SMB：手动保存名称、Host、Port、Share、用户名、密码、Domain，并浏览共享目录。
- 统一文件源接口：`FileSource`、`LocalFileSource`、`SmbFileSource`、`FileItem`、`FileHandle`。
- D-pad：列表项和按钮可聚焦，焦点有明显视觉反馈，OK 打开，Back 返回。
- 状态：加载中、空目录、错误提示、刷新。
- 图片：本地/SMB 图片预览；SMB 列表缩略图先缓存到 app cache 再交给 Coil。
- 文本：UTF-8 预览，最多读取前 1MB，并提示截断。
- 音频：Media3/ExoPlayer 播放；SMB 音频先缓存到 app cache。

## 后续 TODO

- SMB 密码从明文 DataStore 改为 EncryptedSharedPreferences 或 Android Keystore。
- 为 Coil 写 SMB Fetcher，避免为了缩略图复制完整大图。
- 做缓存大小上限和 LRU 清理。
- 本地文件改为 SAF/MediaStore 优先，减少对传统外部存储权限的依赖。
- 加收藏服务器、删除连接、复制/移动/删除文件。
- 图片预览支持同目录左右切换。
- 增加最小化单元测试或 instrumentation 冒烟测试。

## 参考

- Android Gradle Plugin 9.2 官方说明：https://developer.android.com/build/releases/agp-9-2-0-release-notes
- AGP 9 内置 Kotlin 迁移说明：https://developer.android.com/build/migrate-to-built-in-kotlin
- Compose BOM：https://developer.android.com/develop/ui/compose/bom
