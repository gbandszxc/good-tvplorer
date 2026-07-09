package com.goodtvplorer.domain

import android.content.Context
import com.goodtvplorer.data.FileHandle
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

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
