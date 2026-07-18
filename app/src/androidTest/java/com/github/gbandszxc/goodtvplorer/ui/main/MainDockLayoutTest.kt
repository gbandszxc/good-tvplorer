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

    private fun setBrowserContent() {
        composeRule.setContent {
            var viewMode by remember { mutableStateOf(BrowserViewMode.Grid) }
            var contentGeneration by remember { mutableIntStateOf(0) }
            val item = FileItem(
                name = "folder",
                handle = FileHandle("local", SourceKind.Local, "folder-$contentGeneration"),
                kind = FileKind.Directory,
                size = null,
                modifiedAtMillis = null,
            )

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
                ) { contentAutoFocusEnabled ->
                    key(contentGeneration) {
                        BrowserScreen(
                            path = "folder",
                            canNavigateUp = true,
                            contentAutoFocusEnabled = contentAutoFocusEnabled,
                            state = BrowserState(items = listOf(item)),
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

    private fun confirmAndAssertFocused(before: String, after: String) {
        composeRule.onNodeWithContentDescription(before)
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            .assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        composeRule.onNodeWithContentDescription(after).assertIsFocused()
    }
}
