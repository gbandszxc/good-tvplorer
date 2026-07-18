package com.github.gbandszxc.goodtvplorer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainActivityTest {
    @Test
    fun backNavigatesUpOnlyFromNestedContent() {
        assertFalse(shouldNavigateUp(path = "", contentFocused = true))
        assertFalse(shouldNavigateUp(path = "Movies", contentFocused = false))
        assertTrue(shouldNavigateUp(path = "Movies", contentFocused = true))
    }

    @Test
    fun secondBackPressWithinTwoSecondsConfirmsExit() {
        assertFalse(isExitConfirmed(previousBackAt = 0L, now = 1_000L))
        assertTrue(isExitConfirmed(previousBackAt = 1_000L, now = 3_000L))
        assertFalse(isExitConfirmed(previousBackAt = 1_000L, now = 3_001L))
    }
}
