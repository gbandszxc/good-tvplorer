package com.goodtvplorer.data

import com.goodtvplorer.domain.FileTypeDetector
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import com.hierynomus.smbj.share.Share
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.mssmb2.SMB2CreateDisposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumSet

class SmbFileSource(private val info: SmbConnectionInfo) : FileSource {
    override val key = "smb:${info.id}"
    override val kind = SourceKind.Smb
    override val title = info.name

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        withShare { share ->
            share.list(path.trim('/')).filterNot { it.fileName == "." || it.fileName == ".." }
                .map { entry ->
                    val isDirectory = (entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                    val childPath = join(path, entry.fileName)
                    FileItem(
                        name = entry.fileName,
                        handle = FileHandle(key, kind, childPath),
                        kind = FileTypeDetector.detect(entry.fileName, isDirectory),
                        size = if (isDirectory) null else entry.endOfFile,
                        modifiedAtMillis = entry.lastWriteTime.toEpochMillis(),
                    )
                }
                .sortedWith(compareBy<FileItem> { it.kind != FileKind.Directory }.thenBy { it.name.lowercase() })
        }
    }

    override suspend fun readPrefix(path: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        withOpenFile(path) { file ->
            file.inputStream.use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
        }
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        withOpenFile(path) { file ->
            file.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Unit
    }

    private fun <T> withOpenFile(path: String, block: (SmbFile) -> T): T = withShare { share ->
        val file = share.openFile(
            path.trim('/'),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        file.use(block)
    }

    private fun <T> withShare(block: (DiskShare) -> T): T {
        val client = SMBClient()
        return try {
            client.connect(info.host, info.port).use { connection: Connection ->
                val auth = AuthenticationContext(info.username, info.password.toCharArray(), info.domain.orEmpty())
                val session: Session = connection.authenticate(auth)
                session.use {
                    val share: Share = session.connectShare(info.share)
                    require(share is DiskShare) { "不是磁盘共享：${info.share}" }
                    share.use(block)
                }
            }
        } catch (e: SMBApiException) {
            throw IllegalStateException("SMB 访问失败：${e.status}", e)
        } catch (e: Exception) {
            throw IllegalStateException("SMB 连接失败：${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            client.close()
        }
    }

    private fun join(parent: String, child: String): String = listOf(parent.trim('/'), child.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
}
