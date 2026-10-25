package com.github.gbandszxc.goodtvplorer

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        waitForNameFieldFocus()

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
        composeRule.onNodeWithTag("smb-anonymous-toggle").assertIsFocused()
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
    fun anonymousToggleHidesCredentialsAndKeepsSaveAvailableWithoutShare() {
        composeRule.onNodeWithText("添加网络地址").performClick()
        composeRule.onNode(hasText("SMB") and hasClickAction()).performClick()
        waitForNameFieldFocus()

        val toggle = composeRule.onNodeWithTag("smb-anonymous-toggle")
        toggle.performSemanticsAction(SemanticsActions.RequestFocus)
        toggle.performClick()
        composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().let { fields ->
            assertEquals(5, fields.size)
        }
        toggle.assertIsFocused()
        sendKey(Key.DirectionDown)
        field(4).assertIsFocused()

        toggle.performClick()
        assertEquals(7, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("smb-anonymous-toggle").performClick()
        field(1).performTextReplacement("nas.local")
        saveButton().assertIsEnabled()
    }

    @Test
    fun saveButtonStaysDisabledUntilHostIsFilled() {
        composeRule.onNodeWithText("添加网络地址").performClick()
        composeRule.onNode(hasText("SMB") and hasClickAction()).performClick()
        waitForNameFieldFocus()

        saveButton().assertIsNotEnabled()
        field(1).performTextReplacement("nas.local")
        saveButton().assertIsEnabled()
    }

    @Test
    fun smbDialogStaysInsideViewportWithActionsVisible() {
        composeRule.onNodeWithText("添加网络地址").performClick()
        composeRule.onNode(hasText("SMB") and hasClickAction()).performClick()
        waitForNameFieldFocus()

        val rootBounds = composeRule.onAllNodes(isRoot())
            .fetchSemanticsNodes()
            .last()
            .boundsInRoot
        val dialogBounds = composeRule.onNodeWithTag("connection-dialog")
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(dialogBounds.top >= rootBounds.top)
        assertTrue(dialogBounds.bottom <= rootBounds.bottom)
        cancelButton().assertIsDisplayed()
        saveButton().assertIsDisplayed()
    }

    @Test
    fun smbDialogDimensionsAndTextFollowGlobalDisplayScale() {
        val displaySettings = DisplaySettingsRepository(composeRule.activity)
        val density = composeRule.activity.resources.displayMetrics.density
        try {
            runBlocking { displaySettings.setFontScale(0.8f) }
            composeRule.waitUntil(5_000) {
                val backBounds = composeRule
                    .onNode(hasText("返回") and hasClickAction())
                    .fetchSemanticsNode()
                    .boundsInRoot
                val titleBounds = composeRule.onNodeWithText("连接管理").fetchSemanticsNode().boundsInRoot
                kotlin.math.abs(backBounds.left - 36f * density * 0.8f) < 2f &&
                    kotlin.math.abs(titleBounds.left - (backBounds.right + 12f * density * 0.8f)) < 2f
            }
            composeRule.onNodeWithText("添加网络地址").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("SMB") and hasClickAction()).fetchSemanticsNodes().size == 1
            }
            composeRule.onNode(hasText("SMB") and hasClickAction()).performClick()
            waitForNameFieldFocus()

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

    /** SMB 表单在独立 Dialog 窗口中异步取得初始焦点，等待名称字段真正 Focused 后再断言焦点链。 */
    private fun waitForNameFieldFocus() {
        composeRule.waitUntil(5_000) {
            runCatching {
                field(0).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
            }.getOrDefault(false)
        }
    }

    private fun cancelButton() = composeRule.onNode(hasText("取消") and hasClickAction())

    private fun saveButton() = composeRule.onNode(hasText("保存") and hasClickAction())

    private fun sendKey(key: Key) {
        val keyCode = when (key) {
            Key.DirectionUp -> KeyEvent.KEYCODE_DPAD_UP
            Key.DirectionDown -> KeyEvent.KEYCODE_DPAD_DOWN
            Key.DirectionLeft -> KeyEvent.KEYCODE_DPAD_LEFT
            Key.DirectionRight -> KeyEvent.KEYCODE_DPAD_RIGHT
            Key.DirectionCenter -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> error("unsupported key: $key")
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        composeRule.waitForIdle()
    }
}
