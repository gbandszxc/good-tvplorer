package com.github.gbandszxc.goodtvplorer

import android.content.pm.PackageManager
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun appRunsWithoutStatusBar() {
        val insets = requireNotNull(ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView))

        assertFalse(insets.isVisible(WindowInsetsCompat.Type.statusBars()))
    }

    @Test
    fun everyActivityUsesFullscreenAppTheme() {
        val activity = composeRule.activity
        val activities = requireNotNull(
            activity.packageManager
                .getPackageInfo(activity.packageName, PackageManager.GET_ACTIVITIES)
                .activities,
        ).filter { it.name.startsWith("${MainActivity::class.java.packageName}.") }

        assertTrue(activities.isNotEmpty())
        assertTrue(activities.all { it.themeResource == R.style.AppTheme })
    }

    @Test
    fun initialDisplaySectionIsSelectedAndFocused() {
        composeRule
            .onNode(hasText("显示设置") and hasClickAction())
            .assertIsSelected()
            .assertIsFocused()
    }

    @Test
    fun touchSelectionMovesFocusToTheSelectedSection() {
        composeRule
            .onNode(hasText("缓存设置") and hasClickAction())
            .performClick()
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
            .onNodeWithContentDescription("缩小界面")
            .assertHasClickAction()
            .assertIsFocused()

        repeat(4) { sendKey(Key.DirectionCenter) }
        waitForScale("80%")
        composeRule
            .onNodeWithContentDescription("缩小界面")
            .assertIsNotEnabled()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule
            .onNode(hasText("显示设置") and hasClickAction())
            .assertIsFocused()
        sendKey(Key.DirectionRight)
        composeRule
            .onNodeWithContentDescription("放大界面")
            .assertIsFocused()

        composeRule
            .onNode(hasText("恢复默认") and hasClickAction())
            .performClick()
        waitForScale("100%")

        listOf("105%", "110%", "115%", "120%").forEach { expected ->
            composeRule
                .onNodeWithContentDescription("放大界面")
                .assertHasClickAction()
                .assertIsEnabled()
                .performClick()
            waitForScale(expected)
        }
        composeRule
            .onNodeWithContentDescription("放大界面")
            .assertIsNotEnabled()

        composeRule
            .onNode(hasText("恢复默认") and hasClickAction())
            .performClick()
        waitForScale("100%")
    }

    @Test
    fun interfaceScaleChangesSettingsDimensionsAndText() {
        composeRule
            .onNode(hasText("恢复默认") and hasClickAction())
            .performClick()
        waitForScale("100%")

        val baseRow = composeRule
            .onNode(hasText("显示设置") and hasClickAction())
            .fetchSemanticsNode()
            .boundsInRoot
        val baseTitle = composeRule
            .onNodeWithText("设置", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        repeat(4) {
            composeRule
                .onNodeWithContentDescription("放大界面")
                .performClick()
        }
        waitForScale("120%")

        val scaledRow = composeRule
            .onNode(hasText("显示设置") and hasClickAction())
            .fetchSemanticsNode()
            .boundsInRoot
        val scaledTitle = composeRule
            .onNodeWithText("设置", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertEquals((baseRow.bottom - baseRow.top) * 1.2f, scaledRow.bottom - scaledRow.top, 1f)
        assertTrue(scaledTitle.bottom - scaledTitle.top > baseTitle.bottom - baseTitle.top)

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
