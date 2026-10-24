package com.github.gbandszxc.goodtvplorer.domain

import java.nio.charset.Charset
import java.util.Locale

/** ID3v2 与 Vorbis Comment 标签的底层解析，供预览元数据与歌词仓库共用。 */
internal object AudioBinaryTags {
    /** 帧/块在所属字节数组中的内容区间，不含帧头。 */
    data class Frame(val id: String, val start: Int, val end: Int)

    data class Block(val type: Int, val start: Int, val end: Int)

    /** 内嵌封面：MIME 可能缺失（部分标签写空），此时按数据头嗅探。 */
    data class Picture(val mimeType: String?, val data: ByteArray)

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

    /**
     * ID3v2 标签总长度（含 10 字节头）。
     * 非 ID3 标签、长度字段非法（syncsafe 字节最高位被置位）时返回 null。
     */
    fun id3TagLength(bytes: ByteArray): Int? {
        if (bytes.size < 10 || !startsWithAscii(bytes, "ID3")) return null
        for (index in 6..9) {
            if ((bytes[index].toInt() and 0x80) != 0) return null
        }
        return 10 + synchsafeInt(bytes, 6)
    }

    /**
     * 从完整 ID3 标签（含头）中提取第一张封面：
     * 优先 APIC/PIC 图片类型 3（front cover），否则第一张可用图片。
     * 支持 v2.2（PIC）与 v2.3/v2.4（APIC）；任何越界都返回 null。
     */
    fun id3Picture(bytes: ByteArray): Picture? {
        val tagEnd = id3TagLength(bytes)?.coerceAtMost(bytes.size) ?: return null
        val version = bytes[3].toInt() and 0xff
        if (version !in 2..4) return null
        var offset = 10
        // 扩展头（v2.3 长度不含自身，v2.4 syncsafe 长度含自身）。
        if ((bytes[5].toInt() and 0x40) != 0) {
            if (offset + 4 > tagEnd) return null
            offset = when (version) {
                3 -> offset + 4 + u32(bytes, offset).toInt()
                4 -> offset + synchsafeInt(bytes, offset)
                else -> offset
            }
            if (offset >= tagEnd) return null
        }
        val headerSize = if (version == 2) 6 else 10
        val pictures = mutableListOf<Pair<Int, Picture>>()
        while (offset + headerSize <= tagEnd) {
            val idLength = if (version == 2) 3 else 4
            val id = ascii(bytes, offset, idLength)
            if (id.any { !it.isUpperCase() && !it.isDigit() }) break
            val length = when (version) {
                2 -> u24(bytes, offset + 3)
                4 -> synchsafeInt(bytes, offset + 4)
                else -> u32(bytes, offset + 4).toInt()
            }
            val valueStart = offset + headerSize
            if (length <= 0 || valueStart.toLong() + length > tagEnd) break
            val valueEnd = valueStart + length
            when {
                version == 2 && id == "PIC" -> parseId3v22Picture(bytes, valueStart, valueEnd)?.let { pictures += it }
                version != 2 && id == "APIC" -> parseId3v23Picture(bytes, valueStart, valueEnd)?.let { pictures += it }
            }
            offset = valueEnd
        }
        if (pictures.isEmpty()) return null
        return pictures.firstOrNull { it.first == ID3_PICTURE_TYPE_FRONT_COVER }?.second ?: pictures.first().second
    }

    /**
     * FLAC 元数据块头列表。与 [flacBlocks] 不同，块体允许超出 `bytes` 末尾：
     * 调用方可先拿到块头，再按 [Block.start]/[Block.end] 精确读取完整块体。
     * 最多解析 128 个块以防御异常数据。
     */
    fun flacBlockHeaders(bytes: ByteArray): List<Block> {
        if (!startsWithAscii(bytes, "fLaC")) return emptyList()
        val blocks = mutableListOf<Block>()
        var offset = 4
        while (offset + 4 <= bytes.size && blocks.size < MAX_FLAC_METADATA_BLOCKS) {
            val header = bytes[offset].toInt() and 0xff
            val last = header and 0x80 != 0
            val type = header and 0x7f
            val length = u24(bytes, offset + 1)
            val start = offset + 4
            if (start.toLong() + length > Int.MAX_VALUE) break
            val end = start + length
            blocks += Block(type, start, end)
            if (last || end > bytes.size) break
            offset = end
        }
        return blocks
    }

    /** 解析 FLAC PICTURE 块体（type=6 的 payload，或 Base64 解码后的 METADATA_BLOCK_PICTURE）。 */
    fun flacPicture(blockBody: ByteArray): Picture? {
        var offset = 0
        fun readU32(): Long? {
            if (offset + 4 > blockBody.size) return null
            val value = u32(blockBody, offset)
            offset += 4
            return value
        }
        readU32() ?: return null // 图片类型
        val mimeLength = readU32() ?: return null
        if (mimeLength < 0 || mimeLength > Int.MAX_VALUE || offset + mimeLength > blockBody.size) return null
        val mime = blockBody.copyOfRange(offset, offset + mimeLength.toInt()).toString(Charsets.ISO_8859_1)
        offset += mimeLength.toInt()
        val descriptionLength = readU32() ?: return null
        if (descriptionLength < 0 || descriptionLength > Int.MAX_VALUE || offset + descriptionLength > blockBody.size) return null
        offset += descriptionLength.toInt()
        readU32() ?: return null // 宽
        readU32() ?: return null // 高
        readU32() ?: return null // 色深
        readU32() ?: return null // 颜色数
        val dataLength = readU32() ?: return null
        if (dataLength <= 0 || dataLength > Int.MAX_VALUE || offset + dataLength > blockBody.size) return null
        val data = blockBody.copyOfRange(offset, offset + dataLength.toInt())
        return Picture(mime.takeIf { it.isNotBlank() } ?: sniffPictureMime(data), data)
    }

    /** APIC 内容：encoding(1) + MIME(0x00 截止) + 图片类型(1) + 描述(按 encoding 截止) + 数据。 */
    private fun parseId3v23Picture(bytes: ByteArray, start: Int, end: Int): Pair<Int, Picture>? {
        if (end - start < 4) return null
        val encoding = bytes[start].toInt() and 0xff
        val mimeEnd = zeroTerminator(bytes, start + 1, end)
        if (mimeEnd < 0) return null
        val mime = bytes.copyOfRange(start + 1, mimeEnd).toString(Charsets.ISO_8859_1)
        val typeIndex = mimeEnd + 1
        if (typeIndex >= end) return null
        val pictureType = bytes[typeIndex].toInt() and 0xff
        val data = picturePayload(bytes, typeIndex + 1, end, isWideEncoding(encoding)) ?: return null
        return pictureType to Picture(mime.takeIf { it.isNotBlank() } ?: sniffPictureMime(data), data)
    }

    /** PIC 内容：encoding(1) + 格式(3, "JPG"/"PNG") + 图片类型(1) + 描述 + 数据。 */
    private fun parseId3v22Picture(bytes: ByteArray, start: Int, end: Int): Pair<Int, Picture>? {
        if (end - start < 6) return null
        val encoding = bytes[start].toInt() and 0xff
        val format = ascii(bytes, start + 1, 3).uppercase(Locale.ROOT)
        val pictureType = bytes[start + 4].toInt() and 0xff
        val data = picturePayload(bytes, start + 5, end, isWideEncoding(encoding)) ?: return null
        val mime = when (format) {
            "JPG" -> "image/jpeg"
            "PNG" -> "image/png"
            else -> null
        }
        return pictureType to Picture(mime ?: sniffPictureMime(data), data)
    }

    /** 跳过图片描述，返回其后的图片数据；越界或空数据返回 null。 */
    private fun picturePayload(bytes: ByteArray, descriptionStart: Int, end: Int, wide: Boolean): ByteArray? {
        if (descriptionStart >= end) return null
        val descriptionEnd = if (wide) utf16Terminator(bytes, descriptionStart, end) else zeroTerminator(bytes, descriptionStart, end)
        if (descriptionEnd < 0) return null
        val dataStart = descriptionEnd + if (wide) 2 else 1
        if (dataStart >= end) return null
        return bytes.copyOfRange(dataStart, end)
    }

    private fun isWideEncoding(encoding: Int): Boolean = encoding == 1 || encoding == 2

    private fun zeroTerminator(bytes: ByteArray, start: Int, end: Int): Int {
        for (index in start until minOf(end, bytes.size)) {
            if (bytes[index] == 0.toByte()) return index
        }
        return -1
    }

    /** UTF-16 描述以双字节 0x0000 结束；按双字节对齐查找，避免误命中字符低字节。 */
    private fun utf16Terminator(bytes: ByteArray, start: Int, end: Int): Int {
        var index = start
        while (index + 1 < end) {
            if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) return index
            index += 2
        }
        return -1
    }

    private fun sniffPictureMime(data: ByteArray): String? = when {
        data.size >= 3 && data[0] == 0xff.toByte() && data[1] == 0xd8.toByte() && data[2] == 0xff.toByte() -> "image/jpeg"
        data.size >= 8 && PNG_SIGNATURE.indices.all { data[it] == PNG_SIGNATURE[it] } -> "image/png"
        else -> null
    }

    fun startsWithAscii(bytes: ByteArray, value: String): Boolean = bytes.size >= value.length && value.indices.all { bytes[it].toInt().toChar() == value[it] }

    fun ascii(bytes: ByteArray, offset: Int, length: Int): String = bytes.copyOfRange(offset, offset + length).toString(Charsets.ISO_8859_1)

    fun u24(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xff) shl 16) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or (bytes[offset + 2].toInt() and 0xff)

    fun u32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xff) shl 24) or ((bytes[offset + 1].toLong() and 0xff) shl 16) or ((bytes[offset + 2].toLong() and 0xff) shl 8) or (bytes[offset + 3].toLong() and 0xff)

    fun u64(bytes: ByteArray, offset: Int): Long = (u32(bytes, offset) shl 32) or u32(bytes, offset + 4)

    fun synchsafeInt(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0x7f) shl 21) or ((bytes[offset + 1].toInt() and 0x7f) shl 14) or ((bytes[offset + 2].toInt() and 0x7f) shl 7) or (bytes[offset + 3].toInt() and 0x7f)

    private const val ID3_PICTURE_TYPE_FRONT_COVER = 3
    private const val MAX_FLAC_METADATA_BLOCKS = 128
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
}
