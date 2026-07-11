package com.github.gbandszxc.goodtvplorer.domain

import android.graphics.BitmapFactory
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreviewMetadata(val entries: List<PreviewMetadataEntry> = emptyList())

data class PreviewMetadataEntry(val label: String, val value: String)

/** 仅通过文件头/尾的范围读取生成快速预览信息，避免 SMB 媒体整文件缓存。 */
class PreviewMetadataRepository {
    suspend fun read(source: FileSource, item: FileItem): PreviewMetadata = withContext(Dispatchers.IO) {
        when (item.kind) {
            FileKind.Image -> imageMetadata(readPrefix(source, item, IMAGE_PREFIX_BYTES))
            FileKind.Audio -> audioMetadata(readPrefix(source, item, MEDIA_PROBE_BYTES), item.size, item.name)
            FileKind.Video -> videoMetadata(readPrefix(source, item, MEDIA_PROBE_BYTES), readSuffix(source, item, MEDIA_PROBE_BYTES), item.size, item.name)
            FileKind.Directory, FileKind.Text, FileKind.Other -> PreviewMetadata()
        }
    }

    private suspend fun readPrefix(source: FileSource, item: FileItem, maximum: Int): ByteArray {
        val length = item.size?.coerceAtMost(maximum.toLong())?.toInt() ?: maximum
        return if (length <= 0) ByteArray(0) else source.readRange(item.handle.path, 0, length)
    }

    private suspend fun readSuffix(source: FileSource, item: FileItem, maximum: Int): ByteArray {
        val size = item.size ?: return ByteArray(0)
        val length = size.coerceAtMost(maximum.toLong()).toInt()
        return if (length <= 0) ByteArray(0) else source.readRange(item.handle.path, size - length, length)
    }

    private fun imageMetadata(bytes: ByteArray): PreviewMetadata {
        if (bytes.isEmpty()) return PreviewMetadata()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return PreviewMetadata(buildList {
            if (options.outWidth > 0 && options.outHeight > 0) add(PreviewMetadataEntry("分辨率", "${options.outWidth} × ${options.outHeight}"))
            options.outMimeType?.substringAfterLast('/')?.uppercase(Locale.ROOT)?.let { add(PreviewMetadataEntry("格式", it)) }
        })
    }

    private fun audioMetadata(bytes: ByteArray, size: Long?, name: String): PreviewMetadata {
        if (bytes.startsWithAscii("fLaC")) return flacMetadata(bytes, size)
        val metadata = mp3Metadata(bytes, size)
        return if (metadata.entries.isEmpty()) PreviewMetadata(listOf(PreviewMetadataEntry("格式", extension(name)))) else metadata
    }

    private fun videoMetadata(prefix: ByteArray, suffix: ByteArray, size: Long?, name: String): PreviewMetadata {
        val duration = mp4Duration(prefix) ?: mp4Duration(suffix)
        if (duration == null) return PreviewMetadata(listOf(PreviewMetadataEntry("格式", extension(name))))
        return PreviewMetadata(buildList {
            add(PreviewMetadataEntry("时长", formatDuration(duration)))
            size?.let { bitrate(it, duration)?.let { value -> add(PreviewMetadataEntry("码率", value)) } }
            add(PreviewMetadataEntry("格式", extension(name)))
        })
    }

    private fun mp3Metadata(bytes: ByteArray, size: Long?): PreviewMetadata {
        val tags = parseId3(bytes)
        val firstFrame = if (bytes.startsWithAscii("ID3") && bytes.size >= 10) 10 + synchsafeInt(bytes, 6) else 0
        val bitrate = findMp3Bitrate(bytes, firstFrame)
        return PreviewMetadata(buildList {
            bitrate?.let { rate ->
                size?.let { add(PreviewMetadataEntry("时长", formatDuration(it * 8_000L / rate))) }
                add(PreviewMetadataEntry("码率", "${rate / 1_000L} kbps"))
            }
            tags["TITLE"]?.let { add(PreviewMetadataEntry("标题", it)) }
            tags["ARTIST"]?.let { add(PreviewMetadataEntry("艺术家", it)) }
            tags["ALBUM"]?.let { add(PreviewMetadataEntry("专辑", it)) }
            tags["GENRE"]?.let { add(PreviewMetadataEntry("流派", it)) }
            tags["DATE"]?.let { add(PreviewMetadataEntry("年份", it)) }
            tags["TRACKNUMBER"]?.let { add(PreviewMetadataEntry("音轨", it)) }
            if (isEmpty()) add(PreviewMetadataEntry("格式", "MP3"))
        })
    }

    private fun flacMetadata(bytes: ByteArray, size: Long?): PreviewMetadata {
        if (bytes.size < 42) return PreviewMetadata()
        var offset = 4
        var duration: Long? = null
        val tags = mutableMapOf<String, String>()
        while (offset + 4 <= bytes.size) {
            val header = bytes[offset].toInt() and 0xff
            val last = header and 0x80 != 0
            val type = header and 0x7f
            val length = u24(bytes, offset + 1)
            val start = offset + 4
            val end = start + length
            if (end > bytes.size) break
            if (type == 0 && length >= 34) {
                val packed = u64(bytes, start + 10)
                val rate = packed shr 44
                val samples = packed and 0x0fff_fffffL
                if (rate > 0 && samples > 0) duration = samples * 1_000L / rate
            } else if (type == 4) {
                parseVorbisComments(bytes.copyOfRange(start, end), tags)
            }
            offset = end
            if (last) break
        }
        return PreviewMetadata(buildList {
            duration?.let { value -> add(PreviewMetadataEntry("时长", formatDuration(value))) }
            duration?.let { value -> size?.let { bitrate(it, value)?.let { rate -> add(PreviewMetadataEntry("码率", rate)) } } }
            tags["TITLE"]?.let { add(PreviewMetadataEntry("标题", it)) }
            tags["ARTIST"]?.let { add(PreviewMetadataEntry("艺术家", it)) }
            tags["ALBUM"]?.let { add(PreviewMetadataEntry("专辑", it)) }
            tags["GENRE"]?.let { add(PreviewMetadataEntry("流派", it)) }
            (tags["DATE"] ?: tags["YEAR"])?.let { add(PreviewMetadataEntry("年份", it)) }
            tags["TRACKNUMBER"]?.let { add(PreviewMetadataEntry("音轨", it)) }
            add(PreviewMetadataEntry("格式", "FLAC"))
        })
    }

    private fun parseId3(bytes: ByteArray): Map<String, String> {
        if (!bytes.startsWithAscii("ID3") || bytes.size < 10) return emptyMap()
        val version = bytes[3].toInt() and 0xff
        if (version !in 3..4) return emptyMap()
        val end = (10 + synchsafeInt(bytes, 6)).coerceAtMost(bytes.size)
        val tags = mutableMapOf<String, String>()
        var offset = 10
        while (offset + 10 <= end) {
            val id = bytes.ascii(offset, 4)
            if (id.any { !it.isUpperCase() && !it.isDigit() }) break
            val length = if (version == 4) synchsafeInt(bytes, offset + 4) else u32(bytes, offset + 4).toInt()
            val valueStart = offset + 10
            val valueEnd = valueStart + length
            if (length <= 0 || valueEnd > end) break
            val key = when (id) {
                "TIT2" -> "TITLE"; "TPE1" -> "ARTIST"; "TALB" -> "ALBUM"; "TCON" -> "GENRE"
                "TYER", "TDRC" -> "DATE"; "TRCK" -> "TRACKNUMBER"; else -> null
            }
            if (key != null) decodeId3Text(bytes.copyOfRange(valueStart, valueEnd))?.let { tags[key] = it }
            offset = valueEnd
        }
        return tags
    }

    private fun decodeId3Text(bytes: ByteArray): String? {
        if (bytes.size < 2) return null
        val charset = when (bytes[0].toInt()) {
            0 -> Charsets.ISO_8859_1; 1 -> Charset.forName("UTF-16"); 2 -> Charset.forName("UTF-16BE"); 3 -> Charsets.UTF_8; else -> return null
        }
        return bytes.copyOfRange(1, bytes.size).toString(charset).trimEnd('\u0000').takeIf(String::isNotBlank)
    }

    private fun findMp3Bitrate(bytes: ByteArray, start: Int): Long? {
        for (offset in start until (bytes.size - 4).coerceAtLeast(start)) {
            val header = u32(bytes, offset).toInt()
            if (header and 0xffe0_0000.toInt() != 0xffe0_0000.toInt()) continue
            val version = header ushr 19 and 0x3
            val layer = header ushr 17 and 0x3
            val index = header ushr 12 and 0xf
            if (layer != 1 || index == 0 || index == 15) continue
            return (if (version == 3) MP3_MPEG1_LAYER3_BITRATES else MP3_MPEG2_LAYER3_BITRATES)[index]?.times(1_000L)
        }
        return null
    }

    private fun mp4Duration(bytes: ByteArray): Long? {
        val moov = findBox(bytes, "moov") ?: return null
        var offset = moov.first + 8
        val end = moov.first + moov.second
        while (offset + 8 <= end) {
            val size = readBox(bytes, offset, end) ?: break
            if (bytes.ascii(offset + 4, 4) == "mvhd") {
                val payload = offset + 8
                val version = bytes.getOrNull(payload)?.toInt()?.and(0xff) ?: return null
                val scaleOffset = if (version == 1) payload + 20 else payload + 12
                val durationOffset = if (version == 1) payload + 24 else payload + 16
                val durationBytes = if (version == 1) 8 else 4
                if (durationOffset + durationBytes > offset + size) return null
                val scale = u32(bytes, scaleOffset)
                val duration = if (version == 1) u64(bytes, durationOffset) else u32(bytes, durationOffset)
                return if (scale > 0 && duration > 0) duration * 1_000L / scale else null
            }
            offset += size
        }
        return null
    }

    private fun findBox(bytes: ByteArray, type: String): Pair<Int, Int>? {
        for (index in 4 until bytes.size - 4) {
            if (bytes.ascii(index, 4) != type) continue
            val start = index - 4
            val size = readBox(bytes, start, bytes.size) ?: continue
            return start to size
        }
        return null
    }

    private fun readBox(bytes: ByteArray, start: Int, end: Int): Int? {
        if (start + 8 > end) return null
        val size = u32(bytes, start)
        return if (size in 8..Int.MAX_VALUE.toLong() && start.toLong() + size <= end) size.toInt() else null
    }

    private fun parseVorbisComments(bytes: ByteArray, tags: MutableMap<String, String>) {
        var offset = 0
        fun readInt(): Int? {
            if (offset + 4 > bytes.size) return null
            val value = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or ((bytes[offset + 2].toInt() and 0xff) shl 16) or ((bytes[offset + 3].toInt() and 0xff) shl 24)
            offset += 4
            return value
        }
        val vendorLength = readInt() ?: return
        if (vendorLength < 0 || offset + vendorLength > bytes.size) return
        offset += vendorLength
        val count = readInt() ?: return
        repeat(count.coerceIn(0, 64)) {
            val length = readInt() ?: return
            if (length < 0 || offset + length > bytes.size) return
            val pair = bytes.copyOfRange(offset, offset + length).toString(Charsets.UTF_8)
            offset += length
            val separator = pair.indexOf('=')
            if (separator > 0) tags[pair.substring(0, separator).uppercase(Locale.ROOT)] = pair.substring(separator + 1)
        }
    }

    private fun ByteArray.startsWithAscii(value: String): Boolean = size >= value.length && value.indices.all { this[it].toInt().toChar() == value[it] }
    private fun ByteArray.ascii(offset: Int, length: Int): String = copyOfRange(offset, offset + length).toString(Charsets.ISO_8859_1)
    private fun u24(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xff) shl 16) or ((bytes[offset + 1].toInt() and 0xff) shl 8) or (bytes[offset + 2].toInt() and 0xff)
    private fun u32(bytes: ByteArray, offset: Int): Long = ((bytes[offset].toLong() and 0xff) shl 24) or ((bytes[offset + 1].toLong() and 0xff) shl 16) or ((bytes[offset + 2].toLong() and 0xff) shl 8) or (bytes[offset + 3].toLong() and 0xff)
    private fun u64(bytes: ByteArray, offset: Int): Long = (u32(bytes, offset) shl 32) or u32(bytes, offset + 4)
    private fun synchsafeInt(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0x7f) shl 21) or ((bytes[offset + 1].toInt() and 0x7f) shl 14) or ((bytes[offset + 2].toInt() and 0x7f) shl 7) or (bytes[offset + 3].toInt() and 0x7f)
    private fun formatDuration(milliseconds: Long): String = TimeUnit.MILLISECONDS.toSeconds(milliseconds).let { seconds -> if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60) }
    private fun bitrate(size: Long, durationMillis: Long): String? = durationMillis.takeIf { it > 0 }?.let { "${(size * 8_000L / it / 1_000L).coerceAtLeast(1)} kbps" }
    private fun extension(name: String): String = name.substringAfterLast('.', "文件").uppercase(Locale.ROOT)

    private companion object {
        const val IMAGE_PREFIX_BYTES = 256 * 1024
        const val MEDIA_PROBE_BYTES = 512 * 1024
        val MP3_MPEG1_LAYER3_BITRATES = arrayOf<Long?>(null, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, null)
        val MP3_MPEG2_LAYER3_BITRATES = arrayOf<Long?>(null, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, null)
    }
}
