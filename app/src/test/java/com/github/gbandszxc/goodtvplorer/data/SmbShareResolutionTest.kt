package com.github.gbandszxc.goodtvplorer.data

import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SmbShareResolutionTest {
    @Test
    fun `fixed connection share keeps listing relative to its root`() {
        assertEquals("media" to "", resolveSmbSharePath("media", ""))
        assertEquals("media" to "a/b", resolveSmbSharePath("media", "a/b"))
        assertEquals("media" to "a", resolveSmbSharePath("/media/", "/a"))
    }

    @Test
    fun `blank connection share treats first path segment as share name`() {
        assertEquals("media" to "", resolveSmbSharePath("", "media"))
        assertEquals("media" to "sub/movie.mp4", resolveSmbSharePath(" ", "media/sub/movie.mp4"))
    }

    @Test
    fun `blank connection share rejects empty path`() {
        assertFailsWith<IllegalStateException> { resolveSmbSharePath("", "") }
        assertFailsWith<IllegalStateException> { resolveSmbSharePath("", " / ") }
    }

    @Test
    fun `server share listing keeps only sorted disk shares`() {
        val items = smbServerShareItems(
            shares = listOf(
                NetShareInfo1("printer", 1, null),
                NetShareInfo1("Media", 0, " movies"),
                NetShareInfo1("IPC$", 3, null),
                NetShareInfo1("serial", 2, null),
                NetShareInfo1("C$", Int.MIN_VALUE, null),
                NetShareInfo1("backup", 0x40000000, null),
            ),
            sourceKey = "smb:id",
            sourceKind = SourceKind.Smb,
        )

        assertEquals(listOf("backup", "C$", "Media"), items.map { it.name })
        items.forEach { item ->
            assertEquals(FileKind.Directory, item.kind)
            assertEquals(FileHandle("smb:id", SourceKind.Smb, item.name), item.handle)
            assertEquals(null, item.size)
        }
    }

    @Test
    fun `special disk shares stay visible while special non disk shares are hidden`() {
        assertTrue(smbIsDiskShareType(0x80000000.toInt()))
        assertTrue(smbIsDiskShareType(0))
        assertTrue(!smbIsDiskShareType(0x80000001.toInt()))
        assertTrue(!smbIsDiskShareType(3))
    }
}
