package com.github.gbandszxc.goodtvplorer

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun initialDisplaySectionIsSelectedAndFocused() {
        composeRule
            .onNode(hasText("显示设置") and hasClickAction())
            .assertIsSelected()
            .assertIsFocused()
    }

    @Test
    fun aboutRowsExposeStaticAndActionSemantics() {
        sendKey(Key.DirectionDown)
        sendKey(Key.DirectionDown)
        sendKey(Key.DirectionCenter)

        composeRule
            .onNode(hasText("关于") and hasClickAction())
            .assertIsSelected()

        sendKey(Key.DirectionRight)

        composeRule.onNodeWithText("项目信息").assertHasNoClickAction()
        composeRule
            .onNode(hasText("检查更新") and hasClickAction())
            .assertHasClickAction()
            .assertIsFocused()
        composeRule
            .onNode(hasText("GitHub") and hasClickAction())
            .assertHasClickAction()
    }

    @Test
    fun fontScaleStepperSupportsDpadBoundariesAndReset() {
        composeRule
            .onNode(hasText("恢复默认") and hasClickAction())
            .performClick()
        waitForScale("100%")

        sendKey(Key.DirectionRight)
        composeRule
            .onNodeWithContentDescription("减小字体")
            .assertHasClickAction()
            .assertIsFocused()

        repeat(4) { sendKey(Key.DirectionCenter) }
        waitForScale("80%")
        composeRule
            .onNodeWithContentDescription("减小字体")
            .assertIsNotEnabled()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule
            .onNode(hasText("显示设置") and hasClickAction())
            .assertIsFocused()
        sendKey(Key.DirectionRight)
        composeRule
            .onNodeWithContentDescription("增大字体")
            .assertIsFocused()

        composeRule
            .onNode(hasText("恢复默认") and hasClickAction())
            .performClick()
        waitForScale("100%")

        listOf("105%", "110%", "115%", "120%").forEach { expected ->
            composeRule
                .onNodeWithContentDescription("增大字体")
                .assertHasClickAction()
                .assertIsEnabled()
                .performClick()
            waitForScale(expected)
        }
        composeRule
            .onNodeWithContentDescription("增大字体")
            .assertIsNotEnabled()

        composeRule
            .onNode(hasText("恢复默认") and hasClickAction())
            .performClick()
        waitForScale("100%")
    }

    private fun sendKey(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun waitForScale(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

}
