package com.goodtvplorer.domain

import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.SourceKind
import com.goodtvplorer.data.cacheKey
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageModelTest {
    @Test
    fun `image model delegates stable cache identity`() {
        val item = FileItem(
            name = "photo.jpg",
            handle = FileHandle("smb:test", SourceKind.Smb, "photos/photo.jpg"),
            kind = FileKind.Image,
            size = 10L * 1024 * 1024,
            modifiedAtMillis = 1234L,
        )

        assertEquals(item.cacheKey(), ImageModel(source, item).cacheKey)
    }

    private val source = object : FileSource {
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"
        override suspend fun list(path: String) = emptyList<FileItem>()
        override suspend fun readRange(path: String, offset: Long, maxBytes: Int) = ByteArray(0)
        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(ByteArray(0))
        override suspend fun copyTo(path: String, target: File) = Unit
    }
}
