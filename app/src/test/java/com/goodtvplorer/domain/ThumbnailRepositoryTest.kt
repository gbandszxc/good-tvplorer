package com.goodtvplorer.domain

import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.SourceKind
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay

class ThumbnailRepositoryTest {
    private val cacheDir = createTempDirectory("good-tvplorer-thumb-").toFile()

    @AfterTest
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `extracts little and big endian Exif thumbnails`() {
        val thumbnail = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 0xff.toByte(), 0xd9.toByte())

        assertTrue(thumbnail.contentEquals(extractExifThumbnail(exifJpeg(littleEndian = true, thumbnail))))
        assertTrue(thumbnail.contentEquals(extractExifThumbnail(exifJpeg(littleEndian = false, thumbnail))))
    }

    @Test
    fun `rejects Exif thumbnail length outside APP1 bounds`() {
        val jpeg = exifJpeg(littleEndian = true, byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        val offsetField = 2 + 2 + 2 + 6 + 14 + 2 + 12 + 8
        repeat(4) { jpeg[offsetField + it] = 0x7f }

        assertNull(extractExifThumbnail(jpeg))
    }

    @Test
    fun `embedded Exif thumbnail avoids full image copy`() = runBlocking {
        val thumbnail = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 0xff.toByte(), 0xd9.toByte())
        val source = RecordingFileSource(13 * 1024 * 1024, exifJpeg(littleEndian = true, thumbnail))

        val result = repository().thumbnailFile(source, imageItem.copy(size = 13L * 1024 * 1024))

        assertNotNull(result)
        assertEquals("thumbnail", result.readText())
        assertEquals(1, source.readCount)
        assertEquals(0, source.copyCount)
    }

    @Test
    fun `invalid embedded Exif thumbnail falls back to cached original`() = runBlocking {
        val source = RecordingFileSource(
            13 * 1024 * 1024,
            exifJpeg(littleEndian = true, byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1)),
        )
        var decodeCount = 0
        val repository = ThumbnailRepository(cacheDir) { image, thumbnail ->
            decodeCount++
            if (image.length() < 1024) null else thumbnail.apply { parentFile?.mkdirs(); writeText("fallback") }
        }

        val result = repository.thumbnailFile(source, imageItem.copy(size = 13L * 1024 * 1024))

        assertEquals("fallback", assertNotNull(result).readText())
        assertEquals(2, decodeCount)
        assertEquals(1, source.copyCount)
    }

    @Test
    fun `large image is copied once and produces persistent thumbnail`() = runBlocking {
        val source = RecordingFileSource(totalSize = 13 * 1024 * 1024)
        val largeImageItem = imageItem.copy(size = 13L * 1024 * 1024)
        val repository = repository()

        val result = repository.thumbnailFile(source, largeImageItem)

        assertNotNull(result)
        assertTrue(result.exists())
        assertEquals(256L * 1024, source.totalBytesRead)
        assertEquals(1, source.copyCount)
        assertEquals(13L * 1024 * 1024, repository.cachedImageFile(source, largeImageItem).length())
    }

    @Test
    fun `second thumbnail request does not access source again`() = runBlocking {
        val source = RecordingFileSource(totalSize = 13 * 1024 * 1024)
        val largeImageItem = imageItem.copy(size = 13L * 1024 * 1024)
        val repository = repository()

        assertNotNull(repository.thumbnailFile(source, largeImageItem))
        assertNotNull(repository.thumbnailFile(source, largeImageItem))

        assertEquals(256L * 1024, source.totalBytesRead)
        assertEquals(1, source.copyCount)
    }

    @Test
    fun `concurrent requests for same image read and copy source once`() = runBlocking {
        val source = RecordingFileSource(totalSize = 13 * 1024 * 1024, copyDelayMillis = 50)
        val repository = repository()

        List(4) { async { repository.thumbnailFile(source, imageItem.copy(size = 13L * 1024 * 1024)) } }.awaitAll()

        assertEquals(1, source.readCount)
        assertEquals(1, source.copyCount)
    }

    @Test
    fun `concurrent requests for different images do not share lock`() = runBlocking {
        val source = RecordingFileSource(totalSize = 13 * 1024 * 1024, copyDelayMillis = 50)
        val repository = repository()

        listOf(
            imageItem.copy(name = "first.jpg", handle = imageItem.handle.copy(path = "photos/first.jpg")),
            imageItem.copy(name = "second.jpg", handle = imageItem.handle.copy(path = "photos/second.jpg")),
        ).map { item -> async { repository.thumbnailFile(source, item) } }.awaitAll()

        assertEquals(2, source.maxConcurrentCopies)
    }

    private fun repository() = ThumbnailRepository(cacheDir) { cachedImage, thumbnail ->
        assertTrue(cachedImage.exists())
        thumbnail.parentFile?.mkdirs()
        thumbnail.writeText("thumbnail")
        thumbnail
    }

    private val imageItem = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:test", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = 10L * 1024 * 1024,
        modifiedAtMillis = 1234L,
    )

    private class RecordingFileSource(
        private val totalSize: Int,
        private val prefix: ByteArray = ByteArray(0),
        private val copyDelayMillis: Long = 0,
    ) : FileSource {
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"
        private val bytesRead = AtomicLong()
        private val reads = AtomicInteger()
        private val copies = AtomicInteger()
        private val activeCopies = AtomicInteger()
        private val peakCopies = AtomicInteger()
        val totalBytesRead get() = bytesRead.get()
        val readCount get() = reads.get()
        val copyCount get() = copies.get()
        val maxConcurrentCopies get() = peakCopies.get()

        override suspend fun list(path: String) = emptyList<FileItem>()

        override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray {
            reads.incrementAndGet()
            if (prefix.isNotEmpty()) return prefix.copyOfRange(
                offset.toInt().coerceAtMost(prefix.size),
                (offset + maxBytes).coerceAtMost(prefix.size.toLong()).toInt(),
            )
            val size = minOf(maxBytes, (totalSize - offset).coerceAtLeast(0).toInt())
            bytesRead.addAndGet(size.toLong())
            return ByteArray(size)
        }

        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(ByteArray(0))

        override suspend fun copyTo(path: String, target: File) {
            copies.incrementAndGet()
            val active = activeCopies.incrementAndGet()
            peakCopies.updateAndGet { maxOf(it, active) }
            try {
                if (copyDelayMillis > 0) delay(copyDelayMillis)
                target.parentFile?.mkdirs()
                RandomAccessFile(target, "rw").use { it.setLength(totalSize.toLong()) }
            } finally {
                activeCopies.decrementAndGet()
            }
        }
    }

    private fun exifJpeg(littleEndian: Boolean, thumbnail: ByteArray): ByteArray {
        val tiff = ArrayList<Byte>()
        fun u16(value: Int) {
            val bytes = if (littleEndian) intArrayOf(value, value ushr 8) else intArrayOf(value ushr 8, value)
            bytes.forEach { tiff += it.toByte() }
        }
        fun u32(value: Int) {
            val shifts = if (littleEndian) intArrayOf(0, 8, 16, 24) else intArrayOf(24, 16, 8, 0)
            shifts.forEach { tiff += (value ushr it).toByte() }
        }
        tiff += if (littleEndian) listOf(0x49, 0x49).map(Int::toByte) else listOf(0x4d, 0x4d).map(Int::toByte)
        u16(42)
        u32(8)
        u16(0)
        u32(14)
        u16(2)
        u16(0x0201); u16(4); u32(1); u32(44)
        u16(0x0202); u16(4); u32(1); u32(thumbnail.size)
        u32(0)
        tiff += thumbnail.toList()
        val payload = "Exif\u0000\u0000".encodeToByteArray() + tiff.toByteArray()
        val length = payload.size + 2
        return byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe1.toByte(), (length ushr 8).toByte(), length.toByte()) +
            payload + byteArrayOf(0xff.toByte(), 0xd9.toByte())
    }
}
