package com.github.gbandszxc.goodtvplorer.domain

import android.content.Context
import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AudioCacheManager(private val context: Context) {
    suspend fun cachedFile(source: FileSource, handle: FileHandle): File = withContext(Dispatchers.IO) {
        val ext = handle.path.substringAfterLast('.', "bin")
        val file = File(context.cacheDir, "audio/${hash(handle.sourceKey + "|" + handle.path)}.$ext")
        if (!file.exists() || file.length() == 0L) {
            // TODO: MVP 不做缓存上限管理；后续按 LRU 清理 app cache/audio。
            source.copyTo(handle.path, file)
        }
        file
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
