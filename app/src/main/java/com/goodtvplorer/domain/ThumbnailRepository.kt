package com.goodtvplorer.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ThumbnailRepository(private val context: Context) {
    suspend fun imageFile(source: FileSource, handle: FileHandle): File = withContext(Dispatchers.IO) {
        val ext = handle.path.substringAfterLast('.', "img")
        val file = File(context.cacheDir, "images/${hash(handle.sourceKey + "|" + handle.path)}.$ext")
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
        val file = File(context.cacheDir, "media-preview/${hash(handle.sourceKey + "|" + handle.path)}.$ext")
        if (!file.exists() || file.length() == 0L) {
            // TODO: SMB 视频取缩略图会先缓存完整文件；后续改为范围读取或服务端缩略图。
            source.copyTo(handle.path, file)
        }
        return file
    }

    private suspend fun audioCover(source: FileSource, handle: FileHandle): File? {
        val target = File(context.cacheDir, "covers/${hash(handle.sourceKey + "|" + handle.path)}.jpg")
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
        val target = File(context.cacheDir, "video-frames/${hash(handle.sourceKey + "|" + handle.path)}.jpg")
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
}
