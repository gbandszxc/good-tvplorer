package com.github.gbandszxc.goodtvplorer.domain

import java.nio.charset.Charset
import java.util.Locale

/** ID3v2 与 Vorbis Comment 标签的底层解析，供预览元数据与歌词仓库共用。 */
internal object AudioBinaryTags {
    /** 帧/块在所属字节数组中的内容区间，不含帧头。 */
    data class Frame(val id: String, val start: Int, val end: Int)

    data class Block(val type: Int, val start: Int, val end: Int)

    fun id3Frames(bytes: ByteArray): List<Frame> {
        if (!startsWithAscii(bytes, "ID3") || bytes.size < 10) return emptyList()
        val version = bytes[3].toInt() and 0xff
        if (version !in 3..4) return emptyList()
        val end = (10 + synchsafeInt(bytes, 6)).coerceAtMost(bytes.size)
        val frames = mutableListOf<Frame>()
        var offset = 10
        while (offset + 10 <= end) {
            val id = ascii(bytes, offset, 4)
            if (id.any { !it.isUpperCase() && !it.isDigit() }) break
            val length = if (version == 4) synchsafeInt(bytes, offset + 4) else u32(bytes, offset + 4).toInt()
            val valueStart = offset + 10
            val valueEnd = valueStart + length
            if (length <= 0 || valueEnd > end) break
            frames += Frame(id, valueStart, valueEnd)
            offset = valueEnd
        }
        return frames
    }

    /** 按帧内容首字节的编码标识解码 ID3 文本帧。 */
    fun decodeId3Text(body: ByteArray): String? {
        if (body.size < 2) return null
        val charset = when (body[0].toInt()) {
            0 -> Charsets.ISO_8859_1; 1 -> Charset.forName("UTF-16"); 2 -> Charset.forName("UTF-16BE"); 3 -> Charsets.UTF_8; else -> return null
        }
        return body.copyOfRange(1, body.size).toString(charset).trimEnd('\u0000').takeIf(String::isNotBlank)
    }

    /** 解析 Vorbis Comment 块（小端长度前缀）；同名键后者覆盖前者。 */
    fun vorbisComments(bytes: ByteArray): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        var offset = 0
        fun readInt(): Int? {
            if (offset + 4 > bytes.size) return null
            val value = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)
            offset += 4
            return value
        }
        val vendorLength = readInt() ?: return tags
        if (vendorLength < 0 || offset + vendorLength > bytes.size) return tags
        offset += vendorLength
        val count = readInt() ?: return tags
        repeat(count.coerceIn(0, 64)) {
            val length = readInt() ?: return tags
            if (length < 0 || offset + length > bytes.size) return tags
            val pair = bytes.copyOfRange(offset, offset + length).toString(Charsets.UTF_8)
            offset += length
            val separator = pair.indexOf('=')
            if (separator > 0) tags[pair.substring(0, separator).uppercase(Locale.ROOT)] = pair.substring(separator + 1)
        }
        return tags
    }

    /** 遍历 FLAC 元数据块；块被截断或遇到末块标记时停止。 */
    fun flacBlocks(bytes: ByteArray): List<Block> {
        if (!startsWithAscii(bytes, "fLaC")) return emptyList()
        val blocks = mutableListOf<Block>()
        var offset = 4
        while (offset + 4 <= bytes.size) {
            val header = bytes[offset].toInt() and 0xff
            val last = header and 0x80 != 0
            val type = header and 0x7f
            val length = u24(bytes, offset + 1)
            val start = offset + 4
            val end = start + length
            if (end > bytes.size) break
            blocks += Block(type, start, end)
            offset = end
            if (last) break
        }
        return blocks
    }

    fun startsWithAscii(bytes: ByteArray, value: String): Boolean = bytes.size >= value.length && value.indices.all { bytes[it].toInt().toChar() == value[it] }

    fun ascii(bytes: ByteArray, offset: Int, length: Int): String = bytes.copyOfRange(offset, offset + length).toString(Charsets.ISO_8859_1)

    fun u24(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xff) shl 16) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or (bytes[offset + 2].toInt() and 0xff)

    fun u32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xff) shl 24) or ((bytes[offset + 1].toLong() and 0xff) shl 16) or ((bytes[offset + 2].toLong() and 0xff) shl 8) or (bytes[offset + 3].toLong() and 0xff)

    fun u64(bytes: ByteArray, offset: Int): Long = (u32(bytes, offset) shl 32) or u32(bytes, offset + 4)

    fun synchsafeInt(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0x7f) shl 21) or ((bytes[offset + 1].toInt() and 0x7f) shl 14) or ((bytes[offset + 2].toInt() and 0x7f) shl 7) or (bytes[offset + 3].toInt() and 0x7f)
}
