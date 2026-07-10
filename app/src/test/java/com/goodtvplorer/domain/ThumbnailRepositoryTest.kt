package com.goodtvplorer.domain

import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.SourceKind
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking

class ThumbnailRepositoryTest {
    private val cacheDir = createTempDirectory("good-tvplorer-thumb-").toFile()

    @AfterTest
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `thumbnail failure reads at most four MiB and never copies original`() = runBlocking {
        val source = RecordingFileSource(totalSize = 10 * 1024 * 1024)
        val repository = ThumbnailRepository(cacheDir, decoder = { null })

        val result = repository.thumbnailFile(source, imageItem)

        assertNull(result)
        assertEquals(4 * 1024 * 1024L, source.totalBytesRead)
        assertEquals(0, source.copyCount)
    }

    private val imageItem = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:test", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = 10L * 1024 * 1024,
        modifiedAtMillis = 1234L,
    )

    private class RecordingFileSource(private val totalSize: Int) : FileSource {
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"
        var totalBytesRead = 0L
        var copyCount = 0

        override suspend fun list(path: String) = emptyList<FileItem>()

        override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray {
            val size = minOf(maxBytes, (totalSize - offset).coerceAtLeast(0).toInt())
            totalBytesRead += size
            return ByteArray(size)
        }

        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(ByteArray(0))

        override suspend fun copyTo(path: String, target: File) {
            copyCount++
        }
    }
}
