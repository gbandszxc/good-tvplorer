package com.goodtvplorer.data

import com.goodtvplorer.domain.FileTypeDetector
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit

internal inline fun <T> retryConnectionOnce(
    retryable: (Throwable) -> Boolean,
    reset: () -> Unit = {},
    block: () -> T,
): T {
    try {
        return block()
    } catch (first: Throwable) {
        if (first is CancellationException || !retryable(first)) throw first
        reset()
        return block()
    }
}

class SmbFileSource(private val info: SmbConnectionInfo) : FileSource, AutoCloseable {
    override val key = "smb:${info.id}"
    override val kind = SourceKind.Smb
    override val title = info.name

    private val lock = Any()
    private val config = SmbConfig.builder()
        .withNegotiatedBufferSize()
        .withTimeout(30, TimeUnit.SECONDS)
        .withSoTimeout(15, TimeUnit.SECONDS)
        .build()
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        withShare { current ->
            current.list(path.trim('/')).filterNot { it.fileName == "." || it.fileName == ".." }
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

    override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0 && maxBytes >= 0)
        if (maxBytes == 0) return@withContext ByteArray(0)
        withOpenFile(path) { file ->
            val buffer = ByteArray(maxBytes)
            var total = 0
            val chunkSize = readBufferSize()
            while (total < maxBytes) {
                val requested = minOf(maxBytes - total, chunkSize)
                val read = file.read(buffer, offset + total, total, requested)
                if (read <= 0) break
                total += read
                if (read < requested) break
            }
            if (total == 0) ByteArray(0) else buffer.copyOf(total)
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        BufferedInputStream(SmbInputStream(path), readBufferSize())
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0L) return@withContext
        val parent = requireNotNull(target.parentFile) { "缓存文件缺少父目录" }.also { it.mkdirs() }
        val partial = File(parent, ".${target.name}.${UUID.randomUUID()}.part")
        try {
            openStream(path).use { input -> partial.outputStream().use(input::copyTo) }
            if (target.exists() && target.length() > 0L) return@withContext
            check(partial.renameTo(target)) { "无法提交缓存文件：${target.name}" }
        } finally {
            partial.delete()
        }
    }

    override fun close() = synchronized(lock) { closeLocked() }

    private fun openFile(path: String): SmbFile = connectedShare().openFile(
        path.trim('/'),
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null,
    )

    private fun <T> withOpenFile(path: String, block: (SmbFile) -> T): T = withShare {
        openFile(path).use(block)
    }

    private fun <T> withShare(block: (DiskShare) -> T): T = try {
        retryConnectionOnce(::isRetryable, ::invalidate) { block(connectedShare()) }
    } catch (e: SMBApiException) {
        throw IllegalStateException("SMB 访问失败：${e.status}", e)
    } catch (e: Exception) {
        throw IllegalStateException("SMB 连接失败：${e.message ?: e.javaClass.simpleName}", e)
    }

    private fun connectedShare(): DiskShare = synchronized(lock) {
        share?.takeIf { it.isConnected && connection?.isConnected == true } ?: connectLocked()
    }

    private fun connectLocked(): DiskShare {
        closeLocked()
        val nextClient = SMBClient(config)
        var nextConnection: Connection? = null
        var nextSession: Session? = null
        var nextShare: DiskShare? = null
        try {
            nextConnection = nextClient.connect(info.host, info.port)
            val auth = AuthenticationContext(info.username, info.password.toCharArray(), info.domain.orEmpty())
            nextSession = nextConnection.authenticate(auth)
            nextShare = nextSession.connectShare(info.share) as? DiskShare
                ?: error("不是磁盘共享：${info.share}")
            client = nextClient
            connection = nextConnection
            session = nextSession
            share = nextShare
            return nextShare
        } catch (e: Exception) {
            runCatching { nextShare?.close() }
            runCatching { nextSession?.close() }
            runCatching { nextConnection?.close() }
            runCatching { nextClient.close() }
            throw e
        }
    }

    private fun invalidate() = synchronized(lock) { closeLocked() }

    private fun readBufferSize(): Int = synchronized(lock) {
        (connection?.negotiatedProtocol?.maxReadSize ?: 64 * 1024).coerceIn(64 * 1024, 1024 * 1024)
    }

    private fun closeLocked() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        connection = null
        client = null
    }

    private fun isRetryable(error: Throwable): Boolean = error is IOException ||
        error is TransportException ||
        (error is SMBRuntimeException && error !is SMBApiException)

    private fun join(parent: String, child: String): String = listOf(parent.trim('/'), child.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

    private inner class SmbInputStream(private val path: String) : InputStream() {
        private var file = openFile(path)
        private var offset = 0L
        private var mayReconnect = true
        private var closed = false

        override fun read(): Int {
            val byte = ByteArray(1)
            return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, start: Int, length: Int): Int {
            check(!closed) { "流已关闭" }
            if (length == 0) return 0
            val read = retryConnectionOnce(
                retryable = { mayReconnect && isRetryable(it) },
                reset = {
                    mayReconnect = false
                    runCatching { file.close() }
                    invalidate()
                    file = openFile(path)
                },
            ) {
                file.read(buffer, offset, start, length)
            }
            if (read > 0) offset += read
            return read
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { file.close() }
        }
    }
}
