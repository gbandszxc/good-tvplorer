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

}
