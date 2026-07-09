package com.goodtvplorer.data

import android.content.Context
import android.os.Environment
import com.goodtvplorer.domain.FileTypeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    override suspend fun readPrefix(path: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        File(path).inputStream().use { input ->
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        File(path).inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        Unit
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
