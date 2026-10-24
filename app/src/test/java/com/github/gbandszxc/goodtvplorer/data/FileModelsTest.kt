package com.github.gbandszxc.goodtvplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FileModelsTest {
    @Test
    fun `cache key changes with size and modification time`() {
        val base = item(size = 10L, modified = 20L)

        assertNotEquals(base.cacheKey(), base.copy(size = 11L).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(modifiedAtMillis = 21L).cacheKey())
    }

    @Test
    fun `display target keeps share suffix and optional port`() {
        val info = SmbConnectionInfo("id", "NAS", "nas.local", 4450, share = "media", username = "", password = "")

        assertEquals("nas.local:4450/media", info.displayTarget(includePort = true))
        assertEquals("nas.local/media", info.displayTarget())
    }

    @Test
    fun `display target without share omits trailing slash`() {
        val info = SmbConnectionInfo("id", "NAS", "nas.local", 445, share = " ", username = "", password = "")

        assertEquals("nas.local:445", info.displayTarget(includePort = true))
        assertEquals("nas.local", info.displayTarget())
    }

    private fun item(size: Long, modified: Long) = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:test", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = size,
        modifiedAtMillis = modified,
    )
}
