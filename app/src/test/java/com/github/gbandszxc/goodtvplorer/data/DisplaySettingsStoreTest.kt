package com.github.gbandszxc.goodtvplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplaySettingsTest {
    @Test
    fun new_100_percent_keeps_old_75_percent_size() =
        assertEquals(0.75f, effectiveFontScale(1f), 0.0001f)

    @Test
    fun scale_step_clamps_at_both_limits() {
        assertEquals(0.8f, nextFontScale(0.8f, -0.05f), 0.0001f)
        assertEquals(1.2f, nextFontScale(1.2f, 0.05f), 0.0001f)
    }

    @Test
    fun font_scale_percent_rounds_105_percent_without_truncating() =
        assertEquals(105, fontScalePercent(1.05f))
}
