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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

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

internal fun throwSmbFailure(error: Exception): Nothing = when (error) {
    is CancellationException -> throw error
    is SMBApiException -> throw IllegalStateException("SMB 访问失败：${error.status}", error)
    else -> throw IllegalStateException("SMB 连接失败：${error.message ?: error.javaClass.simpleName}", error)
}

internal inline fun readRangeFully(
    maxBytes: Int,
    chunkSize: Int,
    ensureActive: () -> Unit = {},
    read: (buffer: ByteArray, start: Int, requested: Int) -> Int,
): ByteArray {
    val buffer = ByteArray(maxBytes)
    var total = 0
    while (total < maxBytes) {
        ensureActive()
        val count = read(buffer, total, minOf(maxBytes - total, chunkSize))
        if (count <= 0) break
        total += count
    }
    return if (total == maxBytes) buffer else buffer.copyOf(total)
}

class SmbFileSource(private val info: SmbConnectionInfo) : FileSource, AutoCloseable {
    override val key = "smb:${info.id}"
    override val kind = SourceKind.Smb
    override val title = info.name

    private val config = SmbConfig.builder()
        .withNegotiatedBufferSize()
        .withTimeout(30, TimeUnit.SECONDS)
        .withSoTimeout(15, TimeUnit.SECONDS)
        .build()
    private val resources = ReusableResource(
        factory = ::connectResources,
        usable = { it.share.isConnected && it.connection.isConnected },
        close = SmbResources::close,
    )

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        withShare { current ->
            current.share.list(path.trim('/')).filterNot { it.fileName == "." || it.fileName == ".." }
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
        val coroutine = currentCoroutineContext()
        withOpenFile(path) { opened ->
            readRangeFully(
                maxBytes = maxBytes,
                chunkSize = opened.readBufferSize,
                ensureActive = coroutine::ensureActive,
            ) { buffer, start, requested ->
                opened.file.read(buffer, offset + start, start, requested)
            }
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        val stream = SmbInputStream(path, currentCoroutineContext())
        BufferedInputStream(stream, stream.readBufferSize)
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0L) return@withContext
        val parent = requireNotNull(target.parentFile) { "缓存文件缺少父目录" }.also { it.mkdirs() }
        val partial = File(parent, ".${target.name}.${UUID.randomUUID()}.part")
        try {
            openStream(path).use { input -> partial.outputStream().use(input::copyTo) }
            check(commitCacheFile(partial, target)) { "无法提交缓存文件：${target.name}" }
        } finally {
            partial.delete()
        }
    }

    override fun close() = resources.close()

    private fun openFile(share: DiskShare, path: String): SmbFile = share.openFile(
        path.trim('/'),
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null,
    )

    private fun openFile(path: String): OpenedFile = withShare {
        OpenedFile(openFile(it.share, path), it.generation, it.readBufferSize)
    }

    private fun <T> withOpenFile(path: String, block: (OpenedFile) -> T): T = withShare { current ->
        val opened = OpenedFile(openFile(current.share, path), current.generation, current.readBufferSize)
        opened.file.use { block(opened) }
    }

    private fun <T> withShare(block: (ShareLease) -> T): T = try {
        var lease: ResourceLease<SmbResources>? = null
        retryConnectionOnce(
            retryable = ::isRetryable,
            reset = {
                lease?.let { resources.invalidate(it.generation) }
                lease = null
            },
        ) {
            resources.acquire().let { current ->
                lease = current
                block(
                    ShareLease(
                        share = current.value.share,
                        generation = current.generation,
                        readBufferSize = current.value.readBufferSize,
                    ),
                )
            }
        }
    } catch (e: Exception) {
        throwSmbFailure(e)
    }

    private fun connectResources(): SmbResources {
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
            return SmbResources(nextClient, nextConnection, nextSession, nextShare)
        } catch (e: Exception) {
            runCatching { nextShare?.close() }
            runCatching { nextSession?.close() }
            runCatching { nextConnection?.close() }
            runCatching { nextClient.close() }
            throw e
        }
    }

    private fun isRetryable(error: Throwable): Boolean = error is IOException ||
        error is TransportException ||
        (error is SMBRuntimeException && error !is SMBApiException)

    private fun join(parent: String, child: String): String = listOf(parent.trim('/'), child.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

    private inner class SmbInputStream(
        private val path: String,
        private val coroutine: CoroutineContext,
    ) : InputStream() {
        private var opened = openFile(path)
        val readBufferSize = opened.readBufferSize
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
            coroutine.ensureActive()
            val read = retryConnectionOnce(
                retryable = { mayReconnect && isRetryable(it) },
                reset = {
                    mayReconnect = false
                    runCatching { opened.file.close() }
                    resources.invalidate(opened.generation)
                    opened = openFile(path)
                },
            ) {
                opened.file.read(buffer, offset, start, length)
            }
            if (read > 0) offset += read
            return read
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { opened.file.close() }
        }
    }

    private data class ShareLease(val share: DiskShare, val generation: Long, val readBufferSize: Int)
    private data class OpenedFile(val file: SmbFile, val generation: Long, val readBufferSize: Int)

    private data class SmbResources(
        val client: SMBClient,
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
    ) {
        val readBufferSize = connection.negotiatedProtocol.maxReadSize.coerceIn(64 * 1024, 1024 * 1024)

        fun close() {
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }
}
