package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.media3.common.VideoSize
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoScaleModeTest {
    @Test
    fun `显示模式按固定顺序循环`() {
        assertEquals(VideoScaleMode.Fill, VideoScaleMode.Fit.next())
        assertEquals(VideoScaleMode.Stretch, VideoScaleMode.Fill.next())
        assertEquals(VideoScaleMode.Original, VideoScaleMode.Stretch.next())
        assertEquals(VideoScaleMode.Ratio16x9, VideoScaleMode.Original.next())
        assertEquals(VideoScaleMode.Ratio4x3, VideoScaleMode.Ratio16x9.next())
        assertEquals(VideoScaleMode.Fit, VideoScaleMode.Ratio4x3.next())
    }

    @Test
    fun `未知原始尺寸回退适应屏幕`() {
        assertEquals(VideoScaleMode.Fit, effectiveVideoScaleMode(VideoScaleMode.Original, VideoSize.UNKNOWN))
        assertEquals(VideoScaleMode.Original, effectiveVideoScaleMode(VideoScaleMode.Original, VideoSize(1920, 1080)))
    }

    @Test
    fun `触屏滑动超过阈值才切换预览项`() {
        assertEquals(-1, swipeStep(73f, 72f))
        assertEquals(0, swipeStep(72f, 72f))
        assertEquals(1, swipeStep(-73f, 72f))
    }

    @Test
    fun `触屏拖动进度按可用宽度换算并限制范围`() {
        assertEquals(30_000L, seekPosition(120_000L, 25f, 100))
        assertEquals(0L, seekPosition(120_000L, -10f, 100))
        assertEquals(120_000L, seekPosition(120_000L, 110f, 100))
    }
}
