# Android 开发核心概念——前端开发者视角

> 本文档用前端术语类比 Android 开发概念，方便非 Android 开发者快速理解项目代码和与 AI 高效沟通。

---

## 一、架构层面

### Activity — 页面/路由

`MainActivity` ≈ 首页路由 `/`，`SettingsActivity` ≈ `/settings`。
每个 Activity 是独立窗口，切换时有过渡动画（类似 SPA 路由跳转，但在 Android 中是真正的窗口切换）。

### Fragment — 页面内的子视图

一个 Activity 里可以嵌套多个 Fragment，类似 Vue 的 `<router-view>` 里嵌套子组件。本项目未使用 Fragment。

### ViewModel — 状态管理

`MainViewModel` 持有文件列表、当前路径、焦点项等所有状态，UI 只读它。
类比 Pinia / Redux：

```
// 前端：Pinia store
const useMainStore = defineStore('main', () => {
  const files = ref([])
  const currentPath = ref('/')
  return { files, currentPath }
})

// Android：ViewModel
class MainViewModel : ViewModel() {
  val files = mutableStateOf<List<FileItem>>(emptyList())
  val currentPath = mutableStateOf("/")
}
```

### Composable — 函数式组件

`@Composable fun BrowserScreen()` ≈ `function BrowserScreen() { return <div>... }`。
每次状态变化重新执行，框架 diff 后更新视图。

### State — 响应式状态

```kotlin
// Compose
val files by remember { mutableStateOf(emptyList<FileItem>()) }

// 前端等价
const files = ref([])
```

Compose 读到 State 变化会自动重绘该组件，和 Vue 的 `ref()` / React 的 `useState()` 一个机制。

---

## 二、UI 框架层面

| Android 概念 | 前端类比 |
|---|---|
| **Jetpack Compose** | React + JSX（但用 Kotlin DSL 而非 JSX） |
| **XML Layout** | HTML 模板（本项目已弃用） |
| **Material 3** | Ant Design / Element Plus，组件库 |
| **TvTheme** | 全局主题 / CSS Variables |
| `@Preview` 注解 | Storybook 独立预览组件 |

---

## 三、TV 端特有概念

TV 端最大的不同：**没有触摸屏，全靠遥控器 D-pad 导航**。

| 概念 | 前端类比 | 说明 |
|---|---|---|
| **D-pad 焦点** | `:focus` + 键盘导航 | 上下左右键移动焦点，类似 Tab 导航，但更严格：每个可交互元素必须有明确的焦点路径 |
| **FocusRequester** | `ref()` + `.focus()` | 手动让某个元素获焦，类似 `input.focus()` |
| **Leanback** | TV 版响应式设计规范 | Android TV 官方 UI 规范，大屏 + 远距离观看 + 遥控器操作 |
| **焦点态样式** | `button:focus-visible` | 本项目用琥珀色高亮，是 TV 端唯一表示"选中"的方式 |

---

## 四、数据与存储

### Room (SQLite ORM) — IndexedDB 封装

| Room 概念 | 前端类比 |
|---|---|
| Entity | 数据表结构定义（TypeScript interface + ORM model） |
| DAO | 查询方法（`db.users.findMany()`） |
| Repository | 数据访问层（API service） |
| AppDatabase | 数据库实例（`new Database(...)`） |

### Coroutines — async/await 增强版

```kotlin
viewModelScope.launch {
    val files = repo.list()  // 挂起，不阻塞 UI
    _files.value = files
}
```

```typescript
// 前端等价
async function loadFiles() {
  const files = await repo.list()
  files.value = files
}
```

`viewModelScope` 保证 Activity 销毁时自动取消协程，类似 React 的 `useEffect` cleanup。

### 存储优先级

本项目强制使用 Room，**禁止** DataStore / SharedPreferences / localStorage 等。
所有跨页面、跨重启的业务状态必须走 Room Repository。

---

## 五、构建与打包

| Android 概念 | 前端类比 |
|---|---|
| **Gradle** | Webpack / Vite（管依赖、编译、签名、打包） |
| **build.gradle.kts** | `package.json` + `vite.config.ts` |
| **APK** | `.zip` 发布包，直接装设备 |
| **AAB** | Google Play 分发格式 |
| **R8 / ProGuard** | Terser 代码压缩 + 混淆 |
| **minSdk / targetSdk** | `browserslist` 最低兼容版本 |
| **applicationId** | `name` 字段，包名标识 |

---

## 六、项目代码结构速查

```
app/src/main/java/.../
├── MainActivity.kt              // 首页路由入口
├── SettingsActivity.kt          // /settings 页面
├── ConnectionManagementActivity.kt  // /connections 页面
│
├── ui/
│   ├── theme/TvTheme.kt         // theme.css + CSS Variables
│   ├── components/TvButton.kt   // <TvButton /> 组件
│   ├── main/MainDockLayout.kt   // <Layout> 左侧 Dock + 顶栏
│   ├── browser/BrowserScreen.kt // <FileBrowser /> 主浏览页
│   └── preview/PreviewScreens.kt// <Preview /> 右侧预览面板
│
├── viewmodel/MainViewModel.ts   // useMainStore() 状态管理
│
├── data/
│   ├── LocalFileSource.kt       // 本地文件 API
│   ├── SmbFileSource.kt         // SMB 网络文件 API
│   ├── FileModels.kt            // TypeScript 类型定义
│   └── persistence/
│       ├── AppDatabase.kt       // db.ts 数据库定义
│       ├── Entity classes       // 数据表模型
│       ├── DAO interfaces       // 查询方法
│       └── Repository           // 数据访问层
│
└── domain/                      // 工具函数层
    ├── ThumbnailRepository.kt   // 缩略图生成
    ├── Formatters.kt            // 格式化工具
    └── FileTypeDetector.kt      // 文件类型判断
```

---

## 七、沟通高频缩写

| 缩写 | 全称 | 前端等价 |
|---|---|---|
| `VM` | ViewModel | store / composables |
| `DS` | DataStore | localStorage（本项目禁止使用） |
| `SA` | State hoisting | state lifting |
| `FB` | Focus behavior | :focus 处理 |
| `LC` | LazyColumn | 虚拟滚动列表（react-virtualized） |
| `SMB` | Server Message Block | NAS 协议，和 WebDAV 同级 |
| `D-pad` | Directional pad | 遥控器方向键 |
| `Leanback` | — | Android TV UI 框架 |

---

## 八、心智模型

把 Good TVplorer 想成 **React + TypeScript 的 TV 端文件管理器 SPA**：

```
┌─────────────────────────────────────────────┐
│  路由：三个 Activity = 三个页面路由         │
│  ├─ /         MainActivity（主浏览）         │
│  ├─ /settings SettingsActivity              │
│  └─ /connections ConnectionManagement       │
├─────────────────────────────────────────────┤
│  状态：MainViewModel = Pinia store          │
│  ├─ files: 文件列表                          │
│  ├─ currentPath: 当前路径                    │
│  ├─ focusedItem: 焦点项                     │
│  └─ viewMode: 列表/网格                      │
├─────────────────────────────────────────────┤
│  UI：Compose 组件 = React 组件              │
│  ├─ MainDockLayout   → 左侧导航 + 顶栏     │
│  ├─ BrowserScreen    → 文件列表/网格        │
│  └─ PreviewScreens   → 右侧预览             │
├─────────────────────────────────────────────┤
│  数据：Room = IndexedDB                      │
│  ├─ 连接配置（SMB 地址、密码）               │
│  ├─ 显示设置（字体缩放）                     │
│  └─ 浏览恢复状态                             │
├─────────────────────────────────────────────┤
│  特殊：没有鼠标，全靠 D-pad 键盘导航        │
│  焦点管理 = 本项目核心交互逻辑               │
└─────────────────────────────────────────────┘
```

---

## 九、后续可扩充方向

- [ ] Jetpack Compose 重组机制详解
- [ ] D-pad 焦点管理实战
- [ ] Room 数据库迁移与版本管理
- [ ] Coroutines 与 Flow 详解
- [ ] TV 端适配注意事项
- [ ] 项目依赖库说明（Coil3、Media3、SMBj 等）
