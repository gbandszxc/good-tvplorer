package com.github.gbandszxc.goodtvplorer.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamingMediaTest {
    @Test
    fun `解析 lrc 时间并以前后歌词划分区间`() {
        val cues = parseLrc("[00:01.20]第一句\n[00:03.50]第二句")
        assertEquals(TimedTextCue(1200, 3500, "第一句"), cues[0])
    }

    @Test
    fun `解析 srt 多行字幕`() {
        val cues = parseSrt("1\n00:00:01,000 --> 00:00:03,500\n第一行\n第二行")
        assertEquals(TimedTextCue(1000, 3500, "第一行\n第二行"), cues.single())
    }

    @Test
    fun `lrc offset 按正负值整体平移歌词`() {
        val delayed = parseLrc("[OFFSET:+500]\n[00:01.00]第一句\n[00:02.00]第二句")
        assertEquals(
            listOf(TimedTextCue(1500, 2500, "第一句"), TimedTextCue(2500, Long.MAX_VALUE, "第二句")),
            delayed,
        )

        val advanced = parseLrc("[offset:-500]\n[00:01.00]第一句")
        assertEquals(TimedTextCue(500, Long.MAX_VALUE, "第一句"), advanced.single())
    }

    @Test
    fun `currentCueIndex 返回最后一个起始时间不晚于当前位置的歌词`() {
        val cues = parseLrc("[00:01.00]第一句\n[00:02.00]第二句\n[00:03.00]第三句")

        assertEquals(-1, currentCueIndex(emptyList(), 1000))
        assertEquals(-1, currentCueIndex(cues, 999))
        assertEquals(0, currentCueIndex(cues, 1000))
        assertEquals(1, currentCueIndex(cues, 2500))
        assertEquals(2, currentCueIndex(cues, 99_999))
    }
}
