package com.goodtvplorer.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.cacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ThumbnailRepository internal constructor(
    private val cacheDir: File,
    private val decoder: (ByteArray) -> Bitmap?,
) {
    constructor(context: Context) : this(context.cacheDir, ::decodeThumbnail)

    suspend fun thumbnailFile(source: FileSource, item: FileItem): File? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "image-thumbs/${hash(item.cacheKey())}.jpg")
        if (file.exists() && file.length() > 0L) return@withContext file

        var bytes = ByteArray(0)
        for (limit in THUMBNAIL_READ_LIMITS) {
            val requested = limit - bytes.size
            val chunk = source.readRange(item.handle.path, bytes.size.toLong(), requested)
            if (chunk.isEmpty()) break
            bytes += chunk
            val bitmap = decoder(bytes)
            if (bitmap != null) return@withContext writeThumbnail(file, bitmap)
            if (chunk.size < requested) break
        }
        null
    }

    suspend fun imageFile(source: FileSource, handle: FileHandle): File = withContext(Dispatchers.IO) {
        val ext = handle.path.substringAfterLast('.', "img")
        val file = File(cacheDir, "images/${hash(handle.sourceKey + "|" + handle.path)}.$ext")
        if (!file.exists() || file.length() == 0L) {
            // TODO: 当前为可工作的 SMB 图片缓存；后续接入 Coil Fetcher 以真正流式解码缩略图。
            source.copyTo(handle.path, file)
        }
        file
    }

    suspend fun previewFile(source: FileSource, handle: FileHandle, kind: FileKind): File? = withContext(Dispatchers.IO) {
        when (kind) {
            FileKind.Image -> imageFile(source, handle)
            FileKind.Audio -> audioCover(source, handle)
            FileKind.Video -> videoFrame(source, handle)
            else -> null
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

    private fun writeThumbnail(file: File, bitmap: Bitmap): File? {
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.part")
        return try {
            val compressed = partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            when {
                !compressed -> null
                file.exists() && file.length() > 0L -> file
                partial.renameTo(file) -> file
                else -> null
            }
        } finally {
            partial.delete()
            bitmap.recycle()
        }
    }

    private companion object {
        val THUMBNAIL_READ_LIMITS = intArrayOf(512 * 1024, 2 * 1024 * 1024, 4 * 1024 * 1024)

        fun decodeThumbnail(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 640) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
