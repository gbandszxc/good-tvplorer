package com.github.gbandszxc.goodtvplorer.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaDataSource
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.cacheKey
import com.github.gbandszxc.goodtvplorer.data.commitCacheFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class ThumbnailRepository internal constructor(
    private val cacheDir: File,
    private val thumbnailer: (File, File) -> File?,
) {
    // ponytail: 浏览百万唯一文件时改用带引用计数的锁池；常规目录规模下永久保留最简单可靠。
    private val imageLocks = ConcurrentHashMap<String, Mutex>()

    constructor(context: Context) : this(context.cacheDir, ::createThumbnail)

    suspend fun thumbnailFile(source: FileSource, item: FileItem): File? = withContext(Dispatchers.IO) {
        val key = item.cacheKey()
        lockFor(key).withLock {
            when (item.kind) {
                FileKind.Image -> thumbnailFileLocked(source, item, key)
                FileKind.Audio -> audioCover(source, item, key)
                FileKind.Video -> videoFrame(source, item, key)
                FileKind.Directory, FileKind.Text, FileKind.Other -> null
            }
        }
    }

    private suspend fun thumbnailFileLocked(source: FileSource, item: FileItem, key: String): File? {
        val file = File(cacheDir, "image-thumbs/${hash(key)}.jpg")
        if (file.exists() && file.length() > 0L) return file
        extractExifThumbnail(source.readPrefix(item.handle.path, EXIF_PREFIX_BYTES))?.let {
            file.parentFile?.mkdirs()
            val partial = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.exif")
            try {
                partial.writeBytes(it)
                thumbnailer(partial, file)?.let { thumbnail -> return thumbnail }
                file.delete()
            } finally {
                partial.delete()
            }
        }
        return streamThumbnail(source, item, file)
    }

    private fun lockFor(key: String): Mutex = imageLocks[key] ?: Mutex().let { imageLocks.putIfAbsent(key, it) ?: it }

    private suspend fun streamThumbnail(source: FileSource, item: FileItem, target: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.openStream(item.handle.path).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 640) sample *= 2
        val bitmap = source.openStream(item.handle.path).use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
        } ?: return null
        target.parentFile?.mkdirs()
        return try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            target.takeIf { it.length() > 0L }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun audioCover(source: FileSource, item: FileItem, key: String): File? {
        val target = File(cacheDir, "covers/${hash(key)}.jpg")
        if (target.exists() && target.length() > 0L) return target
        return runCatching {
            val picture = retriever(source, item).use { it.embeddedPicture }
            if (picture == null) null else target.also {
                it.parentFile?.mkdirs()
                it.writeBytes(picture)
            }
        }.getOrNull()
    }

    private suspend fun videoFrame(source: FileSource, item: FileItem, key: String): File? {
        val target = File(cacheDir, "video-frames/${hash(key)}.jpg")
        if (target.exists() && target.length() > 0L) return target
        return runCatching {
            val bitmap = retriever(source, item).use { it.getFrameAtTime(VIDEO_FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } ?: return null
            target.also {
                it.parentFile?.mkdirs()
                it.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out) }
            }
        }.getOrNull()
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun retriever(source: FileSource, item: FileItem) = MediaMetadataRetriever().apply {
        setDataSource(FileSourceMediaDataSource(source, item.handle.path, item.size ?: -1L))
    }

    private companion object {
        const val EXIF_PREFIX_BYTES = 256 * 1024
        const val VIDEO_FRAME_TIME_US = 3_000_000L

        fun createThumbnail(cachedImage: File, target: File): File? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(cachedImage.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 640) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(cachedImage.absolutePath, options) ?: return null
            target.parentFile?.mkdirs()
            val partial = File(target.parentFile, ".${target.name}.${UUID.randomUUID()}.part")
            return try {
                val compressed = partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }
                when {
                    !compressed -> null
                    target.exists() && target.length() > 0L -> target
                    commitCacheFile(partial, target) -> target
                    else -> null
                }
            } finally {
                partial.delete()
                bitmap.recycle()
            }
        }
    }
}

private class FileSourceMediaDataSource(private val source: FileSource, private val path: String, private val size: Long) : MediaDataSource() {
    override fun getSize(): Long = size
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, requested: Int): Int {
        if (position < 0 || requested <= 0) return -1
        val bytes = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { source.readRange(path, position, requested.coerceAtMost(1024 * 1024)) }
        if (bytes.isEmpty()) return -1
        bytes.copyInto(buffer, offset)
        return bytes.size
    }
    override fun close() = Unit
}

internal fun extractExifThumbnail(jpeg: ByteArray): ByteArray? {
    if (jpeg.size < 4 || jpeg[0] != 0xff.toByte() || jpeg[1] != 0xd8.toByte()) return null
    var position = 2
    while (position < jpeg.size) {
        if (jpeg[position] != 0xff.toByte()) return null
        while (position < jpeg.size && jpeg[position] == 0xff.toByte()) position++
        if (position >= jpeg.size) return null
        val marker = jpeg[position++].toInt() and 0xff
        if (marker == 0xd9 || marker == 0xda) return null
        if (marker == 0x01 || marker in 0xd0..0xd7) continue
        if (position + 2 > jpeg.size) return null
        val length = ((jpeg[position].toInt() and 0xff) shl 8) or (jpeg[position + 1].toInt() and 0xff)
        if (length < 2) return null
        val start = position + 2
        val end = start.toLong() + length - 2L
        if (end > jpeg.size) return null
        if (marker == 0xe1) parseExifThumbnail(jpeg, start, end.toInt())?.let { return it }
        position = end.toInt()
    }
    return null
}

private fun parseExifThumbnail(bytes: ByteArray, start: Int, end: Int): ByteArray? {
    val signature = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
    if (end - start < 14 || !bytes.copyOfRange(start, start + 6).contentEquals(signature)) return null
    val tiff = start + 6
    val littleEndian = when {
        bytes[tiff] == 0x49.toByte() && bytes[tiff + 1] == 0x49.toByte() -> true
        bytes[tiff] == 0x4d.toByte() && bytes[tiff + 1] == 0x4d.toByte() -> false
        else -> return null
    }
    fun u16(index: Int): Int? {
        if (index < tiff || index.toLong() + 2 > end) return null
        val first = bytes[index].toInt() and 0xff
        val second = bytes[index + 1].toInt() and 0xff
        return if (littleEndian) first or (second shl 8) else (first shl 8) or second
    }
    fun u32(index: Int): Long? {
        if (index < tiff || index.toLong() + 4 > end) return null
        var value = 0L
        if (littleEndian) for (shift in 0..24 step 8) value = value or ((bytes[index + shift / 8].toLong() and 0xff) shl shift)
        else for (offset in 0..3) value = (value shl 8) or (bytes[index + offset].toLong() and 0xff)
        return value
    }
    fun absolute(offset: Long, size: Long = 0): Int? {
        val absolute = tiff.toLong() + offset
        if (offset < 0 || size < 0 || absolute < tiff || absolute + size < absolute || absolute + size > end) return null
        return absolute.toInt()
    }
    if (u16(tiff + 2) != 42) return null
    val ifd0 = absolute(u32(tiff + 4) ?: return null, 2) ?: return null
    val ifd0Count = u16(ifd0) ?: return null
    val ifd0End = ifd0.toLong() + 2L + ifd0Count.toLong() * 12
    if (ifd0End + 4 > end) return null
    val ifd1Offset = u32(ifd0End.toInt()) ?: return null
    if (ifd1Offset == 0L) return null
    val ifd1 = absolute(ifd1Offset, 2) ?: return null
    val count = u16(ifd1) ?: return null
    if (ifd1.toLong() + 2L + count.toLong() * 12 + 4 > end) return null
    var thumbnailOffset: Long? = null
    var thumbnailLength: Long? = null
    repeat(count) { index ->
        val entry = ifd1 + 2 + index * 12
        val tag = u16(entry) ?: return null
        val type = u16(entry + 2) ?: return null
        val values = u32(entry + 4) ?: return null
        val value = u32(entry + 8) ?: return null
        if (type == 4 && values >= 1) when (tag) {
            0x0201 -> thumbnailOffset = value
            0x0202 -> thumbnailLength = value
        }
    }
    val offset = thumbnailOffset ?: return null
    val length = thumbnailLength ?: return null
    if (length <= 0 || length > Int.MAX_VALUE) return null
    val thumbnailStart = absolute(offset, length) ?: return null
    val thumbnailEnd = thumbnailStart + length.toInt()
    if (thumbnailEnd < thumbnailStart || bytes[thumbnailStart] != 0xff.toByte() || bytes.getOrNull(thumbnailStart + 1) != 0xd8.toByte()) return null
    return bytes.copyOfRange(thumbnailStart, thumbnailEnd)
}
