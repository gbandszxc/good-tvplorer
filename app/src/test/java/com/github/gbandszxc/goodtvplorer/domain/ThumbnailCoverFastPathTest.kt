package com.github.gbandszxc.goodtvplorer.domain

import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** 内嵌封面快速路径：验证 FLAC 块按块头跳转，不会读取被跳过的 padding 块体。 */
class ThumbnailCoverFastPathTest {
    private val cacheDir = createTempDirectory("good-tvplorer-covers-").toFile()

    @AfterTest
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `超过前缀的 padding 之后仍能取到 PICTURE 封面且不读 padding 块体`() = runBlocking {
        val bytes = flacFile(prefixPadding = true)
        val source = FakeSource(bytes)

        val cover = assertNotNull(repository().embeddedCoverFast(source, audioItem(bytes.size)))

        assertTrue(JPEG.contentEquals(cover), "封面数据应与 PICTURE 块内的图片完全一致")
        val paddingBodyRange = paddingBodyStart until paddingBodyStart + PADDING_BYTES
        val paddingReads = source.requests.filter { (offset, maxBytes) ->
            maxBytes > HEADER_BYTES && offset in paddingBodyRange
        }
        assertTrue(paddingReads.isEmpty(), "不应请求 padding 块体，实际请求：$paddingReads")
        assertTrue(source.totalBytesRead < 64 * 1024, "总读取 ${source.totalBytesRead} 字节，不应包含 1 MB padding")
    }

    @Test
    fun `小块 FLAC 的 PICTURE 块直接从已读前缀切片`() = runBlocking {
        val bytes = flacFile(prefixPadding = false)
        val source = FakeSource(bytes)

        val cover = assertNotNull(repository().embeddedCoverFast(source, audioItem(bytes.size)))

        assertTrue(JPEG.contentEquals(cover))
        // 整个文件落在 16 KiB 前缀内，块头与块体都可从已读前缀切片，无需额外读取。
        assertEquals(listOf(0L to TAG_PREFIX_BYTES), source.requests)
        assertEquals(bytes.size.toLong(), source.totalBytesRead)
    }

    private fun repository() = ThumbnailRepository(cacheDir) { _, _ -> null }

    private fun audioItem(size: Int) = FileItem(
        name = "a.flac",
        handle = FileHandle("smb:test", SourceKind.Smb, "music/a.flac"),
        kind = FileKind.Audio,
        size = size.toLong(),
        modifiedAtMillis = null,
    )

    private class FakeSource(private val bytes: ByteArray) : FileSource {
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"

        /** 按调用顺序记录 (offset, maxBytes)。 */
        val requests = mutableListOf<Pair<Long, Int>>()
        var totalBytesRead = 0L
            private set

        override suspend fun list(path: String): List<FileItem> = emptyList()

        override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray {
            requests += offset to maxBytes
            if (offset < 0 || offset >= bytes.size) return ByteArray(0)
            val start = offset.toInt()
            val end = minOf(bytes.size.toLong(), start.toLong() + maxBytes).toInt()
            return bytes.copyOfRange(start, end).also { totalBytesRead += it.size }
        }

        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(bytes)

        override suspend fun copyTo(path: String, target: File) = Unit
    }

    private fun flacFile(prefixPadding: Boolean): ByteArray {
        val streamInfo = flacBlock(type = 0, last = false, body = ByteArray(STREAMINFO_BYTES))
        val padding = if (prefixPadding) flacBlock(type = 1, last = false, body = ByteArray(PADDING_BYTES)) else ByteArray(0)
        val picture = flacBlock(type = 6, last = true, body = flacPictureBody(JPEG))
        return "fLaC".encodeToByteArray() + streamInfo + padding + picture
    }

    private fun flacBlock(type: Int, last: Boolean, body: ByteArray): ByteArray = byteArrayOf(
        ((if (last) 0x80 else 0) or type).toByte(),
        ((body.size shr 16) and 0xff).toByte(),
        ((body.size shr 8) and 0xff).toByte(),
        (body.size and 0xff).toByte(),
    ) + body

    private fun flacPictureBody(data: ByteArray): ByteArray {
        val mime = "image/jpeg".encodeToByteArray()
        return u32(3) + u32(mime.size) + mime + u32(0) + u32(600) + u32(600) + u32(24) + u32(0) + u32(data.size) + data
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

    private companion object {
        const val HEADER_BYTES = 4
        const val STREAMINFO_BYTES = 34
        const val PADDING_BYTES = 1_000_000
        const val TAG_PREFIX_BYTES = 16 * 1024

        /** "fLaC" + STREAMINFO 块头与块体之后即为 padding 块头，再加 4 字节块头得到块体起点。 */
        val paddingBodyStart = 4L + HEADER_BYTES + STREAMINFO_BYTES + HEADER_BYTES

        val JPEG = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 1, 2, 3, 4, 0xff.toByte(), 0xd9.toByte())
    }
}
