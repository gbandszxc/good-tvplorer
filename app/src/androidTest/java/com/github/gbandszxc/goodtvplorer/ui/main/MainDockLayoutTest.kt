package com.github.gbandszxc.goodtvplorer.ui.main

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import com.github.gbandszxc.goodtvplorer.ui.browser.BrowserScreen
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserPreviewMetadataState
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserState
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserSort
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserViewMode
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
    fun toolbarDownStaysPutWhenDirectoryHasNoFocusableContent() {
        setBrowserContent(BrowserViewMode.List, canNavigateUp = false, itemCount = 0)
        composeRule.onNodeWithContentDescription("编辑路径")
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()

        pressKey(KeyEvent.KEYCODE_DPAD_DOWN)
        composeRule.onNodeWithContentDescription("编辑路径").assertIsFocused()
    }

    private fun setBrowserContent(
        initialViewMode: BrowserViewMode = BrowserViewMode.Grid,
        canNavigateUp: Boolean = true,
        itemCount: Int = 1,
    ) {
        composeRule.setContent {
            var viewMode by remember { mutableStateOf(initialViewMode) }
            var contentGeneration by remember { mutableIntStateOf(0) }
            val items = List(itemCount) { index ->
                FileItem(
                    name = "folder-$index",
                    handle = FileHandle("local", SourceKind.Local, "folder-$contentGeneration-$index"),
                    kind = FileKind.Directory,
                    size = null,
                    modifiedAtMillis = null,
                )
            }

            TvTheme {
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
                            searchQuery = "",
                            searchItems = null,
                            searchLoading = false,
                            previewMetadata = BrowserPreviewMetadataState(),
                            focusAnchorPath = null,
                            onOpen = {},
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
