package com.goodtvplorer.domain

import coil3.decode.DataSource
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
import kotlin.io.path.createTempFile
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
    fun `open returns cached stream and disk data source`() = runBlocking {
        val cached = createTempFile("good-tvplorer-image-", ".jpg").toFile().apply { writeText("cached") }
        try {
            val model = ImageModel(source, item, cached)
            val opened = model.open()

            assertEquals("cached", opened.stream.bufferedReader().use { it.readText() })
            assertEquals(DataSource.DISK, opened.dataSource)
            assertEquals(0, source.openCount)
        } finally {
            cached.delete()
        }
    }

    @Test
    fun `open returns network stream when cached file is missing`() = runBlocking {
        val missing = createTempFile("good-tvplorer-missing-", ".jpg").toFile().apply { delete() }

        val opened = ImageModel(source, item, missing).open()

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
