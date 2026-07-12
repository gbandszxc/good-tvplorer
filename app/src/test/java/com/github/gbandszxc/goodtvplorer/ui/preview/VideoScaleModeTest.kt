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
}
