package com.github.gbandszxc.goodtvplorer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiblingCoverTest {
    @Test
    fun `按 cover folder front 的优先级选择封面`() {
        assertEquals("cover.webp", pickSiblingCoverName(listOf("album.jpg", "folder.PNG", "cover.webp")))
        assertEquals("folder.PNG", pickSiblingCoverName(listOf("folder.PNG", "front.png", "albumart.jpg")))
        assertEquals("album.jpg", pickSiblingCoverName(listOf("song.mp3", "artwork.jpeg", "album.jpg")))
    }

    @Test
    fun `文件名比较忽略大小写且只接受图片扩展名`() {
        assertEquals("COVER.JPG", pickSiblingCoverName(listOf("COVER.JPG", "folder.gif", "cover.txt")))
        assertNull(pickSiblingCoverName(listOf("cover", "folder.gif", "album.png.bak")))
    }

    @Test
    fun `没有候选封面时返回 null`() {
        assertNull(pickSiblingCoverName(emptyList()))
        assertNull(pickSiblingCoverName(listOf("song.mp3", "song.lrc", "back.jpg")))
    }
}
