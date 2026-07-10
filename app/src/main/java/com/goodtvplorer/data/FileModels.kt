package com.goodtvplorer.data

enum class FileKind { Directory, Image, Text, Audio, Video, Other }

enum class SourceKind { Local, Smb }

data class FileHandle(
    val sourceKey: String,
    val sourceKind: SourceKind,
    val path: String,
)

data class FileItem(
    val name: String,
    val handle: FileHandle,
    val kind: FileKind,
    val size: Long?,
    val modifiedAtMillis: Long?,
)

fun FileItem.cacheKey(): String = listOf(
    handle.sourceKey,
    handle.path,
    size?.toString().orEmpty(),
    modifiedAtMillis?.toString().orEmpty(),
).joinToString("|")

data class SmbConnectionInfo(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 445,
    val share: String,
    val username: String,
    val password: String,
    val domain: String? = null,
)
