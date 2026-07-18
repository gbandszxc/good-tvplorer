package com.github.gbandszxc.goodtvplorer.data

import android.os.Environment
import com.github.gbandszxc.goodtvplorer.domain.FileTypeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

class LocalFileSource internal constructor(rootDirectory: File) : FileSource {
    @Suppress("DEPRECATION")
    constructor() : this(Environment.getExternalStorageDirectory())

    override val key = "local"
    override val kind = SourceKind.Local
    override val title = "本地文件"
    private val root = rootDirectory.canonicalFile
    private val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = resolve(path)
        val files = dir.listFiles().orEmpty()
        files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                FileItem(
                    name = file.name,
                    handle = FileHandle(key, kind, relativePath(file)),
                    kind = FileTypeDetector.detect(file.name, file.isDirectory),
                    size = file.takeIf { it.isFile }?.length(),
                    modifiedAtMillis = file.lastModified().takeIf { it > 0 },
                )
            }
    }

    override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0 && maxBytes >= 0)
        if (maxBytes == 0) return@withContext ByteArray(0)
        RandomAccessFile(resolve(path), "r").use { input ->
            input.seek(offset)
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        resolve(path).inputStream()
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0L) return@withContext
        val parent = requireNotNull(target.parentFile) { "缓存文件缺少父目录" }.also { it.mkdirs() }
        val partial = File(parent, ".${target.name}.${UUID.randomUUID()}.part")
        try {
            resolve(path).inputStream().use { input -> partial.outputStream().use(input::copyTo) }
            check(commitCacheFile(partial, target)) { "无法提交缓存文件：${target.name}" }
        } finally {
            partial.delete()
        }
    }

    internal fun normalizePath(path: String): String = runCatching {
        val cleaned = systemPath(path)
        val rootPath = root.path
        val legacyRootPath = rootPath.trimStart(File.separatorChar)
        val relative = when {
            cleaned == rootPath || cleaned == legacyRootPath -> ""
            cleaned.startsWith("$rootPath${File.separator}") -> cleaned.removePrefix(rootPath).trimStart(File.separatorChar)
            cleaned.startsWith("$legacyRootPath${File.separator}") -> cleaned.removePrefix(legacyRootPath).trimStart(File.separatorChar)
            else -> cleaned
        }
        relativePath(resolve(relative))
    }.getOrDefault("")

    private fun resolve(path: String): File {
        val requested = File(systemPath(path))
        val resolved = (if (requested.isAbsolute) requested else File(root, requested.path)).canonicalFile
        require(resolved == root || resolved.path.startsWith(rootPrefix)) {
            "本地路径超出共享存储根目录"
        }
        return resolved
    }

    private fun relativePath(file: File): String =
        if (file == root) "" else file.absolutePath.removePrefix(rootPrefix).replace(File.separatorChar, '/')

    private fun systemPath(path: String): String = path.trim()
        .replace('\\', File.separatorChar)
        .replace('/', File.separatorChar)
}
