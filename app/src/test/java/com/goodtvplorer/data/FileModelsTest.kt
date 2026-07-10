package com.goodtvplorer.data

import kotlin.test.Test
import kotlin.test.assertNotEquals

class FileModelsTest {
    @Test
    fun `cache key changes with size and modification time`() {
        val base = item(size = 10L, modified = 20L)

        assertNotEquals(base.cacheKey(), base.copy(size = 11L).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(modifiedAtMillis = 21L).cacheKey())
    }

    private fun item(size: Long, modified: Long) = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:test", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = size,
        modifiedAtMillis = modified,
    )
}
