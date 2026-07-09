package com.goodtvplorer.data

import java.io.File

interface FileSource {
    val key: String
    val kind: SourceKind
    val title: String

    suspend fun list(path: String): List<FileItem>
    suspend fun readPrefix(path: String, maxBytes: Int): ByteArray
    suspend fun copyTo(path: String, target: File)
}
