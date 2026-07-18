package com.github.gbandszxc.goodtvplorer.ui.main

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.domain.Formatters
import com.github.gbandszxc.goodtvplorer.ui.browser.BrowserScreen
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserPreviewMetadataState
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserState
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserSort
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainDockLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun viewToggleKeepsFocusAfterConfirmation() {
        setBrowserContent()
        confirmAndAssertFocused("切换为列表视图", "切换为网格视图")
    }

    @Test
    fun refreshKeepsFocusAfterConfirmation() {
        setBrowserContent()
        confirmAndAssertFocused("刷新", "刷新")
    }

    @Test
    fun backKeepsFocusAfterConfirmation() {
        setBrowserContent()
        confirmAndAssertFocused("返回上级", "返回上级")
    }

    @Test
    fun listItemMovesUpThroughToolbarToSelectedSource() {
        setBrowserContent(BrowserViewMode.List, canNavigateUp = false)

        composeRule.onNode(hasText("folder-0") and hasClickAction()).assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("本机").assertIsFocused()
    }

    @Test
    fun gridFirstRowAlwaysMovesUpToToolbar() {
        setBrowserContent(BrowserViewMode.Grid, canNavigateUp = false, itemCount = 4)

        repeat(4) { index ->
            composeRule.onNode(hasText("folder-$index") and hasClickAction())
                .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            pressKey(KeyEvent.KEYCODE_DPAD_UP)
            composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        }
    }

    @Test
    fun dockMovesVerticallyAndReturnsToLastContentItem() {
        setBrowserContent(BrowserViewMode.Grid, canNavigateUp = false)
        composeRule.onNode(hasText("folder-0") and hasClickAction()).assertIsFocused()

        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithContentDescription("切换为列表视图").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("刷新").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("返回上级").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("连接管理").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("设置").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNode(hasText("folder-0") and hasClickAction()).assertIsFocused()
    }

    @Test
    fun listRemainsReachableAfterSwitchingFromAnOffscreenGridItem() {
        setBrowserContent(BrowserViewMode.Grid, canNavigateUp = false, itemCount = 40)
        composeRule.onNodeWithTag("browser-items")
            .performScrollToNode(hasText("folder-24"))
        composeRule.onNode(hasText("folder-24") and hasClickAction())
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()

        composeRule.onNodeWithContentDescription("切换为列表视图")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.onNodeWithContentDescription("切换为网格视图").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNode(hasText("folder-0") and hasClickAction()).assertIsFocused()

        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNode(hasText("folder-0") and hasClickAction()).assertIsFocused()
    }

    @Test
    fun networkSourceMovesDownToConfigurationAndLeftToDock() {
        setNetworkHub()

        composeRule.onNodeWithContentDescription("网络")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNode(hasText("去配置") and hasClickAction()).assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("网络").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithContentDescription("连接管理").assertIsFocused()
    }

    @Test
    fun toolbarMovesInFixedHorizontalOrderAndUpToSource() {
        setBrowserContent(BrowserViewMode.List, canNavigateUp = false)
        composeRule.onNodeWithContentDescription("编辑路径")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }

        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithContentDescription("排序").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNodeWithContentDescription("搜索当前目录").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("本机").assertIsFocused()
    }

    @Test
    fun sourceAndContentAlwaysEnterToolbarThroughPath() {
        setBrowserContent(BrowserViewMode.List, canNavigateUp = false, searchQuery = "folder")
        composeRule.onNodeWithContentDescription("编辑路径")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNodeWithContentDescription("搜索当前目录").assertIsFocused()

        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("本机").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNodeWithContentDescription("搜索当前目录").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNode(hasText("folder-0") and hasClickAction()).assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_UP)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        composeRule.onNodeWithContentDescription("搜索当前目录").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_LEFT)
        composeRule.onNodeWithContentDescription("排序").assertIsFocused()
    }

    @Test
    fun toolbarDownStaysPutWhenDirectoryHasNoFocusableContent() {
        setBrowserContent(BrowserViewMode.List, canNavigateUp = false, itemCount = 0)
        composeRule.onNodeWithContentDescription("编辑路径")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
    }

    @Test
    fun listDisplaysNameSizeAndDateInSeparateColumns() {
        val name = "这是一个用于验证固定宽度和走马灯效果的超长文件名称.txt"
        val modifiedAt = 1_700_000_000_000L
        var displayScale by mutableStateOf(0.8f)
        setBrowserContent(
            initialViewMode = BrowserViewMode.List,
            canNavigateUp = false,
            displayScaleProvider = { displayScale },
            itemsOverride = listOf(
                FileItem(
                    name = name,
                    handle = FileHandle("local", SourceKind.Local, name),
                    kind = FileKind.Text,
                    size = 123L,
                    modifiedAtMillis = modifiedAt,
                ),
            ),
        )
        composeRule.onNodeWithContentDescription("编辑路径")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }

        listOf(0.8f, 1f, 1.2f).forEach { scale ->
            composeRule.runOnIdle { displayScale = scale }
            val nameBounds = composeRule.onAllNodesWithText(name, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
            val sizeBounds = composeRule.onNodeWithText("123 B", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val dateBounds = composeRule.onNodeWithText(Formatters.time(modifiedAt), useUnmergedTree = true).getUnclippedBoundsInRoot()

            assertEquals(360f, (nameBounds.right - nameBounds.left).value, 0.5f)
            assertEquals(120f, (sizeBounds.right - sizeBounds.left).value, 0.5f)
            assertTrue(nameBounds.right <= sizeBounds.left)
            assertTrue(sizeBounds.right <= dateBounds.left)
        }
    }

    @Test
    fun dockSourceAndSourceTextWidthsFollowDisplayScale() {
        var displayScale by mutableStateOf(0.8f)
        composeRule.setContent {
            TvTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density * displayScale,
                        fontScale = effectiveFontScale(displayScale) / displayScale,
                    ),
                ) {
                    MainDockLayout(
                        networkSelected = true,
                        showNetworkHub = true,
                        connections = emptyList(),
                        onLocal = {},
                        onNetwork = {},
                        onOpenSmb = {},
                        onConnections = {},
                        onSettings = {},
                        browserViewMode = null,
                        onToggleView = {},
                        onRefresh = {},
                        onBack = {},
                    ) {}
                }
            }
        }

        val compactDock = composeRule.onNodeWithTag("main-dock").fetchSemanticsNode().boundsInRoot
        val compactSource = composeRule.onNodeWithContentDescription("本机").fetchSemanticsNode().boundsInRoot
        val compactText = composeRule.onNodeWithText("本机", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle { displayScale = 1.2f }

        composeRule.onNodeWithTag("main-dock").fetchSemanticsNode().boundsInRoot.let {
            assertEquals(1.5f, it.width / compactDock.width, 0.02f)
        }
        composeRule.onNodeWithContentDescription("本机").fetchSemanticsNode().boundsInRoot.let {
            assertEquals(1.5f, it.width / compactSource.width, 0.02f)
        }
        composeRule.onNodeWithText("本机", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.let {
            assertEquals(1.5f, it.width / compactText.width, 0.05f)
        }
    }

    @Test
    fun mainRegionsShareOneScaledSpacing() {
        val displayScale = 1.2f
        val spacing = 16f
        setBrowserContent(displayScale = displayScale)

        val root = composeRule.onNodeWithTag("main-layout").getUnclippedBoundsInRoot()
        val dock = composeRule.onNodeWithTag("main-dock").getUnclippedBoundsInRoot()
        val source = composeRule.onNodeWithTag("source-bar").getUnclippedBoundsInRoot()
        val toolbar = composeRule.onNodeWithTag("browser-toolbar").getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag("browser-content").getUnclippedBoundsInRoot()

        assertEquals(0f, (dock.left - root.left).value, 0.5f)
        assertEquals(0f, (dock.top - root.top).value, 0.5f)
        assertEquals(0f, (root.bottom - dock.bottom).value, 0.5f)
        assertEquals(spacing, (source.left - dock.right).value, 0.5f)
        assertEquals(spacing, (source.top - root.top).value, 0.5f)
        assertEquals(spacing, (root.right - source.right).value, 0.5f)
        assertEquals(source.left.value, toolbar.left.value, 0.5f)
        assertEquals(source.right.value, toolbar.right.value, 0.5f)
        assertEquals(spacing, (toolbar.top - source.bottom).value, 0.5f)
        assertEquals(toolbar.left.value, content.left.value, 0.5f)
        assertEquals(toolbar.right.value, content.right.value, 0.5f)
        assertEquals(spacing, (content.top - toolbar.bottom).value, 0.5f)
        assertEquals(spacing, (root.bottom - content.bottom).value, 0.5f)
    }

    @Test
    fun previewPanelFollowsDisplayScale() {
        var displayScale by mutableStateOf(0.8f)
        setBrowserContent(displayScaleProvider = { displayScale })

        val compactPanel = composeRule.onNodeWithTag("preview-panel").fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle { displayScale = 1.2f }

        val expandedPanel = composeRule.onNodeWithTag("preview-panel").fetchSemanticsNode().boundsInRoot
        assertEquals(1.5f, expandedPanel.width / compactPanel.width, 0.02f)
    }

    @Test
    fun quickPreviewContentIsScrollableOnShortScreens() {
        setBrowserContent()

        composeRule.onNodeWithTag("preview-content")
            .assert(hasScrollAction())
    }

    @Test
    fun touchClickFocusesFileBeforeOpeningIt() {
        var opened = 0
        setBrowserContent(
            initialViewMode = BrowserViewMode.List,
            canNavigateUp = false,
            itemCount = 2,
            onOpen = { opened++ },
        )
        val secondItem = composeRule.onNode(hasText("folder-1") and hasClickAction())

        secondItem.performTouchInput { click() }
        secondItem.assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, opened) }

        secondItem.performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun remoteConfirmationStillOpensFocusedFileImmediately() {
        var opened = 0
        setBrowserContent(
            initialViewMode = BrowserViewMode.List,
            canNavigateUp = false,
            onOpen = { opened++ },
        )

        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)

        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    private fun setBrowserContent(
        initialViewMode: BrowserViewMode = BrowserViewMode.Grid,
        canNavigateUp: Boolean = true,
        itemCount: Int = 1,
        searchQuery: String = "",
        displayScale: Float = 1f,
        itemsOverride: List<FileItem>? = null,
        displayScaleProvider: () -> Float = { displayScale },
        onOpen: (FileItem) -> Unit = {},
    ) {
        composeRule.setContent {
            var viewMode by remember { mutableStateOf(initialViewMode) }
            var contentGeneration by remember { mutableIntStateOf(0) }
            val items = itemsOverride ?: List(itemCount) { index ->
                FileItem(
                    name = "folder-$index",
                    handle = FileHandle("local", SourceKind.Local, "folder-$contentGeneration-$index"),
                    kind = FileKind.Directory,
                    size = null,
                    modifiedAtMillis = null,
                )
            }

            TvTheme {
                val displayScale = displayScaleProvider().coerceIn(0.8f, 1.2f)
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density * displayScale,
                        fontScale = effectiveFontScale(displayScale) / displayScale,
                    ),
                ) {
                    MainDockLayout(
                        networkSelected = false,
                        showNetworkHub = false,
                        connections = emptyList(),
                        onLocal = {},
                        onNetwork = {},
                        onOpenSmb = {},
                        onConnections = {},
                        onSettings = {},
                        browserViewMode = viewMode,
                        onToggleView = {
                            viewMode = if (viewMode == BrowserViewMode.Grid) BrowserViewMode.List else BrowserViewMode.Grid
                        },
                        onRefresh = { contentGeneration++ },
                        onBack = { contentGeneration++ },
                    ) { focusNavigation ->
                        key(contentGeneration) {
                            BrowserScreen(
                                path = "folder",
                                canNavigateUp = canNavigateUp,
                                navigation = focusNavigation,
                                state = BrowserState(items = items),
                                thumbnails = emptyMap(),
                                viewMode = viewMode,
                                sort = BrowserSort(),
                                searchQuery = searchQuery,
                                searchItems = null,
                                searchLoading = false,
                                previewMetadata = BrowserPreviewMetadataState(),
                                focusAnchorPath = null,
                                onOpen = onOpen,
                                onNavigateUp = {},
                                onOpenPath = {},
                                onSortChange = {},
                                onSearchQueryChange = {},
                                onPreviewMetadataRequest = {},
                                onThumbnailVisible = {},
                                onThumbnailHidden = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setNetworkHub() {
        composeRule.setContent {
            TvTheme {
                MainDockLayout(
                    networkSelected = true,
                    showNetworkHub = true,
                    connections = emptyList(),
                    onLocal = {},
                    onNetwork = {},
                    onOpenSmb = {},
                    onConnections = {},
                    onSettings = {},
                    browserViewMode = null,
                    onToggleView = {},
                    onRefresh = {},
                    onBack = {},
                ) {}
            }
        }
    }

    private fun confirmAndAssertFocused(before: String, after: String) {
        val stepsFromViewToggle = when (before) {
            "刷新" -> 1
            "返回上级" -> 2
            else -> 0
        }
        composeRule.onNodeWithContentDescription(if (stepsFromViewToggle == 0) before else "切换为列表视图")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        repeat(stepsFromViewToggle) { pressKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        composeRule.onNodeWithContentDescription(before).assertIsFocused()
        pressKey(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.onNodeWithContentDescription(after).assertIsFocused()
    }

    private fun pressKey(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        composeRule.waitForIdle()
    }
}
