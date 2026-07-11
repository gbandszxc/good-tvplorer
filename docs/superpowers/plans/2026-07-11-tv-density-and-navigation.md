# TV 信息密度与快速导航 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以原 75% 视觉体量为新 100% 基线，收紧 TV 浏览器密度，并加入当前文件源内的手动路径跳转。

**Architecture:** DataStore 保存 0.80–1.20 的相对缩放；`MainActivity` 乘固定 0.75 后提供给 Compose。路径编辑复用 `openBrowser(sourceKey, path)`，只规范化当前文件源内的相对路径和根路径。

**Tech Stack:** Kotlin、Jetpack Compose、Material 3、DataStore Preferences、JUnit 4。

## Global Constraints

- Android TV 遥控器优先；新增控件支持焦点、OK 和 Back。
- 不新增依赖、字体、主题 token 或页面分区。
- 缩放为 80%–120%、5% 步进、默认 100%；实际字体缩放为 `0.75f * preference`。
- 保持 `DESIGN.md` 的石板蓝表面、琥珀焦点、3dp 焦点描边和 8dp 圆角。
- 地址跳转只在当前本地或 SMB 文件源内生效。

---

### Task 1: 新缩放语义与测试

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/data/DisplaySettingsStore.kt`
- Modify: `app/src/main/java/com/goodtvplorer/MainActivity.kt`
- Modify: `app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt`
- Test: `app/src/test/java/com/goodtvplorer/data/DisplaySettingsStoreTest.kt`

**Interfaces:** Produces `internal fun effectiveFontScale(preference: Float): Float` and `internal fun nextFontScale(value: Float, delta: Float): Float`.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun new_100_percent_keeps_old_75_percent_size() =
    assertEquals(0.75f, effectiveFontScale(1f), 0.0001f)

@Test fun scale_step_clamps_at_both_limits() {
    assertEquals(0.8f, nextFontScale(0.8f, -0.05f), 0.0001f)
    assertEquals(1.2f, nextFontScale(1.2f, 0.05f), 0.0001f)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.goodtvplorer.data.DisplaySettingsStoreTest"`

Expected: FAIL because the functions do not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal const val BaseFontScale = 0.75f
internal fun effectiveFontScale(preference: Float) = BaseFontScale * preference.coerceIn(0.8f, 1.2f)
internal fun nextFontScale(value: Float, delta: Float) = (value + delta).coerceIn(0.8f, 1.2f)
```

Set store and `MainUiState` defaults to `1f`; update DataStore bounds to `0.8f..1.2f`; provide `Density(density.density, effectiveFontScale(state.fontScale))` in `MainActivity`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.goodtvplorer.data.DisplaySettingsStoreTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/goodtvplorer/data/DisplaySettingsStore.kt app/src/main/java/com/goodtvplorer/MainActivity.kt app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt app/src/test/java/com/goodtvplorer/data/DisplaySettingsStoreTest.kt; git commit -m "feat: 重设电视端全局缩放基线"`

### Task 2: 显示弹窗与路径规范化

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/ui/components/DisplaySettingsDialog.kt`
- Modify: `app/src/main/java/com/goodtvplorer/ui/components/TvButton.kt`
- Modify: `app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt`
- Test: `app/src/test/java/com/goodtvplorer/viewmodel/MainViewModelTest.kt`

**Interfaces:** Produces `internal fun resolveBrowserPath(currentPath: String, enteredPath: String): String` and `fun openEnteredPath(path: String)`.

- [ ] **Step 1: Write the failing path test**

```kotlin
@Test fun entered_path_stays_in_current_source() {
    assertEquals("Movies/2024", resolveBrowserPath("Movies", "2024"))
    assertEquals("Pictures", resolveBrowserPath("Movies/2024", "/Pictures"))
}
```

- [ ] **Step 2: Run the test to verify the established step contract**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.goodtvplorer.viewmodel.MainViewModelTest.entered_path_stays_in_current_source"`

Expected: PASS because Task 1 establishes `nextFontScale`; this assertion locks the required 5% increment before the dialog consumes it.

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal fun resolveBrowserPath(currentPath: String, enteredPath: String): String {
    val cleaned = enteredPath.trim().replace('\\', '/').trim('/')
    return if (enteredPath.trim().startsWith('/')) cleaned else listOf(currentPath.trim('/'), cleaned)
        .filter(String::isNotBlank).joinToString("/")
}

fun openEnteredPath(path: String) {
    val screen = _state.value.screen as? Screen.Browser ?: return
    openBrowser(screen.sourceKey, resolveBrowserPath(screen.path, path))
}
```

Replace fixed percentage buttons with `−` / value / `＋` / “恢复默认” / “关闭”; the step uses `nextFontScale`, disables at boundaries, and preserves existing `TvButton` colors and 8dp shape. Add `enabled: Boolean = true` to `TvButton`, making disabled buttons non-focusable and non-clickable.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.goodtvplorer.data.DisplaySettingsStoreTest" --tests "com.goodtvplorer.viewmodel.MainViewModelTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/goodtvplorer/ui/components/DisplaySettingsDialog.kt app/src/main/java/com/goodtvplorer/ui/components/TvButton.kt app/src/main/java/com/goodtvplorer/viewmodel/MainViewModel.kt app/src/test/java/com/goodtvplorer/viewmodel/MainViewModelTest.kt; git commit -m "feat: 优化显示设置与路径跳转"`

### Task 3: 固定工具栏与紧凑浏览器

**Files:**
- Modify: `app/src/main/java/com/goodtvplorer/MainActivity.kt`
- Modify: `app/src/main/java/com/goodtvplorer/ui/browser/BrowserScreen.kt`
- Modify: `DESIGN.md`

**Interfaces:** `BrowserScreen` receives `onOpenPath: (String) -> Unit` bound to `MainViewModel::openEnteredPath`.

- [ ] **Step 1: Implement fixed toolbar and editable address**

```kotlin
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("Good TVplorer", modifier = Modifier.width(170.dp), maxLines = 1)
    AddressBar(path, Modifier.weight(1f), onOpenPath)
    Row(Modifier.wrapContentWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TvButton(if (viewMode == BrowserViewMode.Grid) "网格" else "列表", Modifier.width(88.dp), onToggleView)
        TvButton("刷新", Modifier.width(88.dp), onRefresh)
        TvButton("显示", Modifier.width(88.dp), onDisplaySettings)
        TvButton("返回", Modifier.width(88.dp), onBack)
    }
}
```

`AddressBar` default state shows ellipsized text plus a focusable edit button; edit state has a single-line `OutlinedTextField`. IME Done / OK opens the entered path; Back cancels editing and restores the label.

- [ ] **Step 2: Tighten density without changing regions**

```kotlin
Column(Modifier.fillMaxSize().padding(24.dp))
Row(Modifier.fillMaxSize().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp))
LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp))
```

Converge existing relevant padding and gaps to 4dp multiples; preserve 16:10 thumbnails and list-row remote-control target height.

- [ ] **Step 3: Update the design contract**

Update `DESIGN.md` with the new 100% baseline, 80%–120% steps, fixed toolbar action region, and current-source address editing behavior.

- [ ] **Step 4: Run verification**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`.

Manually verify long paths never shrink the four action buttons; edit icon, OK, Back, invalid paths, and 80% / 100% / 120% keep usable focus and readable text.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/goodtvplorer/MainActivity.kt app/src/main/java/com/goodtvplorer/ui/browser/BrowserScreen.kt DESIGN.md; git commit -m "feat: 提升浏览器信息密度与导航"`

## Self-review

- Spec coverage: Task 1 covers the new baseline; Task 2 covers scale controls and source-local path handling; Task 3 covers the fixed action region, editable address, density and design documentation.
- Placeholder scan: no incomplete or deferred implementation items.
- Type consistency: `effectiveFontScale`, `nextFontScale`, `resolveBrowserPath`, and `openEnteredPath` are defined before they are consumed.
