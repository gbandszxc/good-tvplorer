package com.github.gbandszxc.goodtvplorer.data

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

/** 用户名和密码均留空时按 SMB 匿名认证；填写用户名但密码留空仍按无密码账户认证，不视为匿名。 */
val SmbConnectionInfo.isAnonymousCredentials: Boolean
    get() = username.isBlank() && password.isEmpty()

/** 连接列表展示用目标地址；共享目录为空表示浏览服务器全部共享，不追加尾斜杠。 */
fun SmbConnectionInfo.displayTarget(includePort: Boolean = false): String = buildString {
    append(host)
    if (includePort) append(':').append(port)
    if (!share.isBlank()) append('/').append(share.trim())
}
