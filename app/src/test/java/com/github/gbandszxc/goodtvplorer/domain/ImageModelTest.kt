package com.github.gbandszxc.goodtvplorer.domain

import coil3.decode.DataSource
import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import com.github.gbandszxc.goodtvplorer.data.cacheKey
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

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

    @Test
    fun `open always returns the SMB source stream`() = runBlocking {
        val opened = ImageModel(source, item).open()

        assertEquals("source", opened.stream.bufferedReader().use { it.readText() })
        assertEquals(DataSource.NETWORK, opened.dataSource)
        assertEquals(1, source.openCount)
    }

    private val item = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:test", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = 10L * 1024 * 1024,
        modifiedAtMillis = 1234L,
    )

    private val source = object : FileSource {
        var openCount = 0
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"
        override suspend fun list(path: String) = emptyList<FileItem>()
        override suspend fun readRange(path: String, offset: Long, maxBytes: Int) = ByteArray(0)
        override suspend fun openStream(path: String): InputStream {
            openCount++
            return ByteArrayInputStream("source".encodeToByteArray())
        }
        override suspend fun copyTo(path: String, target: File) = Unit
    }
}
