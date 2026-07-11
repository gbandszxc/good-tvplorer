package com.github.gbandszxc.goodtvplorer.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
        lockFor(key).withLock { thumbnailFileLocked(source, item, key) }
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
        return thumbnailer(cachedImageFileLocked(source, item, key), file)
    }

    suspend fun cachedImageFile(source: FileSource, item: FileItem): File = withContext(Dispatchers.IO) {
        val key = item.cacheKey()
        lockFor(key).withLock { cachedImageFileLocked(source, item, key) }
    }

    private fun lockFor(key: String): Mutex = imageLocks[key] ?: Mutex().let { imageLocks.putIfAbsent(key, it) ?: it }

    private suspend fun cachedImageFileLocked(source: FileSource, item: FileItem, key: String): File {
        val directory = File(cacheDir, "image-cache")
        val file = File(directory, hash(key))
        if (file.exists() && file.length() > 0L) return file
        source.copyTo(item.handle.path, file)
        check(file.exists() && file.length() > 0L) { "原图缓存写入失败：${item.name}" }
        return file
    }

    suspend fun previewFile(source: FileSource, handle: FileHandle, kind: FileKind): File? = withContext(Dispatchers.IO) {
        when (kind) {
            FileKind.Audio -> audioCover(source, handle)
            FileKind.Video -> videoFrame(source, handle)
            FileKind.Directory, FileKind.Image, FileKind.Text, FileKind.Other -> null
        }
    }

    private suspend fun mediaCache(source: FileSource, handle: FileHandle): File {
        val ext = handle.path.substringAfterLast('.', "bin")
        val file = File(cacheDir, "media-preview/${hash(handle.sourceKey + "|" + handle.path)}.$ext")
        if (!file.exists() || file.length() == 0L) {
            // TODO: SMB 视频取缩略图会先缓存完整文件；后续改为范围读取或服务端缩略图。
            source.copyTo(handle.path, file)
        }
        return file
    }

    private suspend fun audioCover(source: FileSource, handle: FileHandle): File? {
        val target = File(cacheDir, "covers/${hash(handle.sourceKey + "|" + handle.path)}.jpg")
        if (target.exists() && target.length() > 0L) return target
        val media = mediaCache(source, handle)
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(media.absolutePath)
            val picture = retriever.embeddedPicture
            retriever.release()
            if (picture == null) null else target.also {
                it.parentFile?.mkdirs()
                it.writeBytes(picture)
            }
        }.getOrNull()
    }

    private suspend fun videoFrame(source: FileSource, handle: FileHandle): File? {
        val target = File(cacheDir, "video-frames/${hash(handle.sourceKey + "|" + handle.path)}.jpg")
        if (target.exists() && target.length() > 0L) return target
        if (Build.VERSION.SDK_INT < 29) return null
        val media = mediaCache(source, handle)
        return runCatching {
            val bitmap = ThumbnailUtils.createVideoThumbnail(media, Size(640, 360), null)
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

    private companion object {
        const val EXIF_PREFIX_BYTES = 256 * 1024

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
