package com.goodtvplorer.data

import android.content.Context
import android.os.Environment
import com.goodtvplorer.domain.FileTypeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

class LocalFileSource(private val context: Context) : FileSource {
    override val key = "local"
    override val kind = SourceKind.Local
    override val title = "本地文件"

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext roots()
        val dir = File(path)
        val files = dir.listFiles().orEmpty()
        files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .map { file ->
                FileItem(
                    name = file.name,
                    handle = FileHandle(key, kind, file.absolutePath),
                    kind = FileTypeDetector.detect(file.name, file.isDirectory),
                    size = file.takeIf { it.isFile }?.length(),
                    modifiedAtMillis = file.lastModified().takeIf { it > 0 },
                )
            }
    }

    override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0 && maxBytes >= 0)
        if (maxBytes == 0) return@withContext ByteArray(0)
        RandomAccessFile(path, "r").use { input ->
            input.seek(offset)
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        File(path).inputStream()
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0L) return@withContext
        val parent = requireNotNull(target.parentFile) { "缓存文件缺少父目录" }.also { it.mkdirs() }
        val partial = File(parent, ".${target.name}.${UUID.randomUUID()}.part")
        try {
            File(path).inputStream().use { input -> partial.outputStream().use(input::copyTo) }
            check(commitCacheFile(partial, target)) { "无法提交缓存文件：${target.name}" }
        } finally {
            partial.delete()
        }
    }

    private fun roots(): List<FileItem> {
        val dirs = linkedMapOf<String, File>()
        fun add(name: String, file: File?) {
            if (file != null && file.exists()) dirs[name] = file
        }
        add("App files", context.getExternalFilesDir(null))
        add("Download", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        add("Movies", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES))
        add("Pictures", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
        add("Music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
        return dirs.map { (name, file) ->
            FileItem(name, FileHandle(key, kind, file.absolutePath), FileKind.Directory, null, file.lastModified())
        }
    }
}
