package com.github.gbandszxc.goodtvplorer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionManagementActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ConnectionManagementActivity>()

    @Test
    fun smbFormSupportsDpadTraversalWithoutStealingCaretKeys() {
        composeRule.onNodeWithText("添加网络地址").performClick()
        sendKey(Key.DirectionDown)
        composeRule
            .onNode(hasText("SMB") and hasClickAction())
            .assertIsFocused()
        sendKey(Key.DirectionCenter)

        val fields = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals(7, fields.size)
        assertEquals(fields[1].boundsInRoot.top, fields[2].boundsInRoot.top, 1f)
        assertEquals(fields[4].boundsInRoot.top, fields[5].boundsInRoot.top, 1f)
        field(0).assertIsFocused()

        sendKey(Key.DirectionRight)
        field(0).assertIsFocused()
        sendKey(Key.DirectionLeft)
        field(0).assertIsFocused()

        sendKey(Key.DirectionDown)
        field(1).assertIsFocused().performTextReplacement("nas.local")
        sendKey(Key.DirectionUp)
        field(0).assertIsFocused()

        sendKey(Key.DirectionDown)
        field(1).assertIsFocused()
        sendKey(Key.DirectionDown)
        field(2).assertIsFocused()
        sendKey(Key.DirectionDown)
        field(3).assertIsFocused().performTextReplacement("media")
        sendKey(Key.DirectionDown)
        field(4).assertIsFocused()
        sendKey(Key.DirectionDown)
        field(5).assertIsFocused()
        sendKey(Key.DirectionDown)
        field(6).assertIsFocused()

        sendKey(Key.DirectionDown)
        cancelButton().assertIsFocused()
        sendKey(Key.DirectionRight)
        saveButton().assertIsEnabled().assertIsFocused()
        sendKey(Key.DirectionLeft)
        cancelButton().assertIsFocused()
    }

    @Test
    fun smbDialogDimensionsAndTextFollowGlobalDisplayScale() {
        val displaySettings = DisplaySettingsRepository(composeRule.activity)
        val density = composeRule.activity.resources.displayMetrics.density
        try {
            runBlocking { displaySettings.setFontScale(0.8f) }
            composeRule.waitUntil(5_000) {
                val titleBounds = composeRule.onNodeWithText("连接管理").fetchSemanticsNode().boundsInRoot
                kotlin.math.abs(titleBounds.left - 36f * density * 0.8f) < 2f
            }
            composeRule.onNodeWithText("添加网络地址").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("SMB") and hasClickAction()).fetchSemanticsNodes().size == 1
            }
            composeRule.onNode(hasText("SMB") and hasClickAction()).performClick()

            val compactPort = field(2).fetchSemanticsNode().boundsInRoot
            val compactTitle = composeRule.onNodeWithText("新增 SMB").fetchSemanticsNode().boundsInRoot
            val compactDialog = composeRule.onNodeWithTag("connection-dialog").fetchSemanticsNode().boundsInRoot
            assertEquals(128f * density * 0.8f, compactPort.width, 2f)
            assertEquals(560f * density * 0.8f, compactDialog.width, 2f)

            runBlocking { displaySettings.setFontScale(1.2f) }
            composeRule.waitUntil(5_000) {
                val titleBounds = composeRule.onNodeWithText("新增 SMB").fetchSemanticsNode().boundsInRoot
                titleBounds.height > compactTitle.height
            }
            val expandedPort = field(2).fetchSemanticsNode().boundsInRoot
            val expandedTitle = composeRule.onNodeWithText("新增 SMB").fetchSemanticsNode().boundsInRoot
            val expandedDialog = composeRule.onNodeWithTag("connection-dialog").fetchSemanticsNode().boundsInRoot

            assertEquals(128f * density * 1.2f, expandedPort.width, 2f)
            assertEquals(560f * density * 1.2f, expandedDialog.width, 2f)
            assertEquals(
                compactPort.width * 1.5f,
                expandedPort.width,
                2f,
            )
            assertTrue(expandedTitle.height > compactTitle.height)
        } finally {
            runBlocking { displaySettings.setFontScale(1f) }
        }
    }

    private fun field(index: Int) = composeRule.onAllNodes(hasSetTextAction())[index]

    private fun cancelButton() = composeRule.onNode(hasText("取消") and hasClickAction())

    private fun saveButton() = composeRule.onNode(hasText("保存") and hasClickAction())

    private fun sendKey(key: Key) {
        val roots = composeRule.onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().lastIndex].performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }
}
