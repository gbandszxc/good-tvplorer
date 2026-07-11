package com.github.gbandszxc.goodtvplorer.data

import java.io.File
import java.io.InputStream

interface FileSource {
    val key: String
    val kind: SourceKind
    val title: String

    suspend fun list(path: String): List<FileItem>
    suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray
    suspend fun readPrefix(path: String, maxBytes: Int): ByteArray = readRange(path, 0, maxBytes)
    suspend fun openStream(path: String): InputStream
    suspend fun copyTo(path: String, target: File)
}

internal fun commitCacheFile(partial: File, target: File): Boolean {
    if (target.exists() && target.length() > 0L) {
        partial.delete()
        return true
    }
    if (target.exists() && !target.delete()) return false
    return partial.renameTo(target)
}
