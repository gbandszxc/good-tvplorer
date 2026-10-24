package com.github.gbandszxc.goodtvplorer.domain

import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileSource
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AudioLyricsSource(val label: String) { ExternalLrc("同名 LRC"), Embedded("内嵌歌词"), None("") }

data class AudioLyrics(val cues: List<TimedTextCue> = emptyList(), val source: AudioLyricsSource = AudioLyricsSource.None)

class AudioLyricsRepository {
    suspend fun load(source: FileSource, item: FileItem, externalLrc: FileItem?): AudioLyrics = withContext(Dispatchers.IO) {
        try {
            if (externalLrc != null) {
                val text = decodeLyricsText(source.readPrefix(externalLrc.handle.path, EXTERNAL_LRC_BYTES))
                val cues = parseLrc(text)
                if (cues.isNotEmpty()) return@withContext AudioLyrics(cues, AudioLyricsSource.ExternalLrc)
            }
            val text = extractEmbeddedLyrics(source.readPrefix(item.handle.path, EMBEDDED_HEADER_BYTES), item.name)
            if (text == null) return@withContext AudioLyrics()
            val cues = parseLrc(text)
            when {
                cues.isNotEmpty() -> AudioLyrics(cues, AudioLyricsSource.Embedded)
                text.isNotBlank() -> AudioLyrics(listOf(TimedTextCue(0, Long.MAX_VALUE, text.trim())), AudioLyricsSource.Embedded)
                else -> AudioLyrics()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AudioLyrics()
        }
    }

    private companion object {
        const val EXTERNAL_LRC_BYTES = 1024 * 1024
        const val EMBEDDED_HEADER_BYTES = 512 * 1024
    }
}

/** 歌词文件通常是本地编码，按 BOM → 严格 UTF-8 → GB18030 → UTF-8 替换的顺序解码。 */
internal fun decodeLyricsText(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    if (bytes.size >= 2) {
        if (bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
    }
    decodeStrictUtf8(bytes)?.let { return it }
    return runCatching { String(bytes, Charset.forName("GB18030")) }.getOrDefault(String(bytes, Charsets.UTF_8))
}

private fun decodeStrictUtf8(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

/** 从音频头部提取内嵌歌词；支持 ID3v2.3/2.4 的 USLT 帧与 FLAC 的 Vorbis Comment。 */
internal fun extractEmbeddedLyrics(header: ByteArray, @Suppress("UNUSED_PARAMETER") fileName: String): String? {
    id3Lyrics(header)?.let { return it }
    flacLyrics(header)?.let { return it }
    // TODO: M4A 的 `©lyr` 原子与 Ogg/Opus 的 LYRICS 注释暂不支持，留待后续按需实现。
    return null
}

private fun id3Lyrics(header: ByteArray): String? {
    val texts = AudioBinaryTags.id3Frames(header)
        .filter { it.id == "USLT" }
        .mapNotNull { usltText(header.copyOfRange(it.start, it.end)) }
    return texts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** USLT 内容：encoding(1) + language(3) + 描述符（含终止符）+ 歌词文本。 */
private fun usltText(body: ByteArray): String? {
    if (body.size < 4) return null
    val encoding = body[0].toInt() and 0xff
    val charset = when (encoding) {
        0 -> Charsets.ISO_8859_1
        1 -> Charset.forName("UTF-16")
        2 -> Charset.forName("UTF-16BE")
        3 -> Charsets.UTF_8
        else -> return null
    }
    val wide = encoding == 1 || encoding == 2
    val descriptorStart = 4
    var descriptorEnd = -1
    if (wide) {
        var index = descriptorStart
        while (index + 1 < body.size) {
            if (body[index] == 0.toByte() && body[index + 1] == 0.toByte()) {
                descriptorEnd = index
                break
            }
            index += 2
        }
    } else {
        for (index in descriptorStart until body.size) {
            if (body[index] == 0.toByte()) {
                descriptorEnd = index
                break
            }
        }
    }
    if (descriptorEnd < 0) return null
    val start = descriptorEnd + if (wide) 2 else 1
    if (start >= body.size) return null
    return body.copyOfRange(start, body.size).toString(charset).trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }
}

private fun flacLyrics(header: ByteArray): String? {
    if (!AudioBinaryTags.startsWithAscii(header, "fLaC")) return null
    for (block in AudioBinaryTags.flacBlocks(header)) {
        if (block.type != VORBIS_COMMENT_BLOCK) continue
        val tags = AudioBinaryTags.vorbisComments(header.copyOfRange(block.start, block.end))
        val lyrics = tags["LYRICS"]?.takeIf { it.isNotBlank() } ?: tags["UNSYNCEDLYRICS"]?.takeIf { it.isNotBlank() }
        if (lyrics != null) return lyrics.trim()
    }
    return null
}

private const val VORBIS_COMMENT_BLOCK = 4
