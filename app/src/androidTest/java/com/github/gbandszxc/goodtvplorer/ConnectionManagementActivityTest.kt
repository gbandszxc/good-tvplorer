package com.github.gbandszxc.goodtvplorer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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

        assertEquals(7, composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
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

    private fun field(index: Int) = composeRule.onAllNodes(hasSetTextAction())[index]

    private fun cancelButton() = composeRule.onNode(hasText("取消") and hasClickAction())

    private fun saveButton() = composeRule.onNode(hasText("保存") and hasClickAction())

    private fun sendKey(key: Key) {
        val roots = composeRule.onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().lastIndex].performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }
}
