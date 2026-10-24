package com.github.gbandszxc.goodtvplorer.domain

import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioLyricsRepositoryTest {
    @Test
    fun `无 BOM 的 UTF-8 歌词直接解码`() {
        assertEquals("[00:01.00]第一句", decodeLyricsText("[00:01.00]第一句".encodeToByteArray()))
    }

    @Test
    fun `非法 UTF-8 字节回退 GB18030`() {
        assertEquals("第一句", decodeLyricsText("第一句".toByteArray(Charset.forName("GB18030"))))
    }

    @Test
    fun `剥离 UTF-16LE BOM 解码歌词`() {
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + "[00:01.00]第一句".toByteArray(Charsets.UTF_16LE)
        assertEquals("[00:01.00]第一句", decodeLyricsText(bytes))
    }

    @Test
    fun `提取 ID3v2_3 USLT 帧中的歌词并跳过描述符`() {
        val header = id3v23Uslt("第一句\n第二句", descriptor = "comment")

        assertEquals("第一句\n第二句", extractEmbeddedLyrics(header, "song.mp3"))
    }

    @Test
    fun `提取 FLAC Vorbis Comment 中的 LYRICS`() {
        assertEquals("第一句", extractEmbeddedLyrics(flacWithLyrics("第一句"), "song.flac"))
    }

    @Test
    fun `同名 LRC 优先于内嵌歌词`() = runBlocking {
        val source = FakeSource(
            mapOf(
                "Music/song.lrc" to "[00:02.00]外部".encodeToByteArray(),
                "Music/song.mp3" to id3v23Uslt("内嵌"),
            ),
        )

        val lyrics = AudioLyricsRepository().load(source, audioItem, lrcItem)

        assertEquals(AudioLyricsSource.ExternalLrc, lyrics.source)
        assertEquals(listOf(TimedTextCue(2000, Long.MAX_VALUE, "外部")), lyrics.cues)
    }

    @Test
    fun `非 LRC 内嵌文本降级为单行歌词`() = runBlocking {
        val source = FakeSource(mapOf("Music/song.mp3" to id3v23Uslt("只是歌词")))

        val lyrics = AudioLyricsRepository().load(source, audioItem, null)

        assertEquals(AudioLyricsSource.Embedded, lyrics.source)
        assertEquals(listOf(TimedTextCue(0, Long.MAX_VALUE, "只是歌词")), lyrics.cues)
    }

    @Test
    fun `读取失败时返回空歌词而不抛异常`() = runBlocking {
        assertEquals(AudioLyrics(), AudioLyricsRepository().load(ThrowingSource(), audioItem, null))
    }

    private val audioItem = FileItem(
        name = "song.mp3",
        handle = FileHandle("smb:test", SourceKind.Smb, "Music/song.mp3"),
        kind = FileKind.Audio,
        size = 1024L,
        modifiedAtMillis = null,
    )

    private val lrcItem = FileItem(
        name = "song.lrc",
        handle = FileHandle("smb:test", SourceKind.Smb, "Music/song.lrc"),
        kind = FileKind.Text,
        size = 64L,
        modifiedAtMillis = null,
    )

    /** encoding=3(UTF-8) + language + 描述符 + 0x00 + 歌词。 */
    private fun id3v23Uslt(text: String, descriptor: String = ""): ByteArray {
        val body = byteArrayOf(3) + "eng".encodeToByteArray() + descriptor.encodeToByteArray() + byteArrayOf(0) + text.encodeToByteArray()
        val frame = "USLT".encodeToByteArray() + u32(body.size) + byteArrayOf(0, 0) + body
        val header = byteArrayOf(
            (frame.size shr 21 and 0x7f).toByte(),
            (frame.size shr 14 and 0x7f).toByte(),
            (frame.size shr 7 and 0x7f).toByte(),
            (frame.size and 0x7f).toByte(),
        )
        return "ID3".encodeToByteArray() + byteArrayOf(3, 0, 0) + header + frame
    }

    private fun u32(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun u32le(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
        (value ushr 16).toByte(),
        (value ushr 24).toByte(),
    )

    /** 只含一个末位 VORBIS_COMMENT 块的 FLAC 头。 */
    private fun flacWithLyrics(text: String): ByteArray {
        val comment = "LYRICS=$text".encodeToByteArray()
        val payload = u32le(0) + u32le(1) + u32le(comment.size) + comment
        val header = byteArrayOf(
            (0x80 or 4).toByte(),
            (payload.size ushr 16).toByte(),
            (payload.size ushr 8).toByte(),
            payload.size.toByte(),
        )
        return "fLaC".encodeToByteArray() + header + payload
    }

    private class FakeSource(private val files: Map<String, ByteArray>) : FileSource {
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"

        override suspend fun list(path: String) = emptyList<FileItem>()

        override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray {
            val bytes = files[path] ?: return ByteArray(0)
            val start = offset.toInt().coerceAtMost(bytes.size)
            return bytes.copyOfRange(start, (start + maxBytes).coerceAtMost(bytes.size))
        }

        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(files[path] ?: ByteArray(0))

        override suspend fun copyTo(path: String, target: File) = Unit
    }

    private class ThrowingSource : FileSource {
        override val key = "smb:test"
        override val kind = SourceKind.Smb
        override val title = "test"

        override suspend fun list(path: String): List<FileItem> = throw IllegalStateException("离线")
        override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray = throw IllegalStateException("离线")
        override suspend fun openStream(path: String): InputStream = throw IllegalStateException("离线")
        override suspend fun copyTo(path: String, target: File) = throw IllegalStateException("离线")
    }
}
