package com.goodtvplorer.data

import android.util.Log
import com.goodtvplorer.domain.FileTypeDetector
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.mssmb2.messages.SMB2Echo
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
import java.io.OutputStream
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

internal inline fun <R, T> useResourceWithRetryOnce(
    resources: ReusableResource<R>,
    retryable: (Throwable) -> Boolean,
    retain: (R) -> Unit = {},
    release: (R) -> Unit = {},
    block: (ResourceLease<R>) -> T,
): T {
    var lease: ResourceLease<R>? = null
    var retained = false
    try {
        return retryConnectionOnce(
            retryable = retryable,
            reset = {
                lease?.let {
                    synchronized(resources) {
                        if (retained) release(it.value)
                        retained = false
                        resources.invalidate(it.generation)
                    }
                }
                lease = null
            },
        ) {
            synchronized(resources) {
                resources.acquire().also {
                    retain(it.value)
                    retained = true
                    lease = it
                }
            }.let(block)
        }
    } finally {
        lease?.takeIf { retained }?.let { release(it.value) }
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

internal inline fun copyStreamCancellable(
    input: InputStream,
    output: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ensureActive: () -> Unit,
): Long {
    require(bufferSize > 0)
    val buffer = ByteArray(bufferSize)
    var total = 0L
    while (true) {
        ensureActive()
        val count = input.read(buffer)
        if (count < 0) return total
        output.write(buffer, 0, count)
        total += count
    }
}

internal class IdleResourceVerifier(
    private val clockNanos: () -> Long = System::nanoTime,
    private val idleNanos: Long,
    private val probe: () -> Boolean,
) {
    private var lastVerifiedNanos = clockNanos()
    private var activeCount = 0

    @Synchronized
    fun isUsable(connected: Boolean): Boolean {
        if (!connected) return false
        if (activeCount > 0) return true
        val now = clockNanos()
        if (now - lastVerifiedNanos <= idleNanos) return true
        if (!probe()) return false
        lastVerifiedNanos = clockNanos()
        return true
    }

    @Synchronized
    fun retain() {
        activeCount++
    }

    @Synchronized
    fun release() {
        check(activeCount > 0) { "资源 lease 计数不匹配" }
        activeCount--
    }

    @Synchronized
    fun markActive() {
        lastVerifiedNanos = clockNanos()
    }
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
        usable = SmbResources::isUsable,
        close = SmbResources::close,
    )

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        var generation = 0L
        var count = 0
        try {
            withShare { current ->
                generation = current.generation
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
                    .also { count = it.size }
            }
        } finally {
            logPerformance("list", started, generation, path, count = count)
        }
    }

    override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0 && maxBytes >= 0)
        if (maxBytes == 0) return@withContext ByteArray(0)
        val coroutine = currentCoroutineContext()
        var generation = 0L
        var byteCount = 0L
        val started = System.nanoTime()
        try {
            withOpenFile(path) { opened ->
                generation = opened.generation
                readRangeFully(
                    maxBytes = maxBytes,
                    chunkSize = opened.readBufferSize,
                    ensureActive = coroutine::ensureActive,
                ) { buffer, start, requested ->
                    opened.file.read(buffer, offset + start, start, requested)
                }.also { byteCount = it.size.toLong() }
            }
        } finally {
            logPerformance("readRange", started, generation, path, bytes = byteCount, count = 1)
        }
    }

    override suspend fun openStream(path: String): InputStream = withContext(Dispatchers.IO) {
        val stream = SmbInputStream(path)
        BufferedInputStream(stream, stream.readBufferSize)
    }

    override suspend fun copyTo(path: String, target: File) = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0L) return@withContext
        val parent = requireNotNull(target.parentFile) { "缓存文件缺少父目录" }.also { it.mkdirs() }
        val partial = File(parent, ".${target.name}.${UUID.randomUUID()}.part")
        val coroutine = currentCoroutineContext()
        try {
            openStream(path).use { input ->
                partial.outputStream().use { output ->
                    copyStreamCancellable(input, output, ensureActive = coroutine::ensureActive)
                }
            }
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

    private fun openRetainedFile(path: String): OpenedFile = withShare {
        val file = openFile(it.share, path)
        it.resource.retain()
        OpenedFile(file, it.generation, it.readBufferSize, it.resource)
    }

    private fun <T> withOpenFile(path: String, block: (OpenedFile) -> T): T = withShare { current ->
        val opened = OpenedFile(openFile(current.share, path), current.generation, current.readBufferSize, current.resource)
        opened.file.use { block(opened) }
    }

    private fun <T> withShare(block: (ShareLease) -> T): T = try {
        val started = System.nanoTime()
        useResourceWithRetryOnce(
            resources = resources,
            retryable = ::isRetryable,
            retain = SmbResources::retain,
            release = SmbResources::release,
        ) { current ->
            logPerformance("resourceAcquire", started, current.generation, null, count = 1)
            block(
                ShareLease(
                    share = current.value.share,
                    generation = current.generation,
                    readBufferSize = current.value.readBufferSize,
                    resource = current.value,
                ),
            ).also { current.value.markActive() }
        }
    } catch (e: Exception) {
        throwSmbFailure(e)
    }

    private fun connectResources(): SmbResources {
        val started = System.nanoTime()
        val nextClient = SMBClient(config)
        var nextConnection: Connection? = null
        var nextSession: Session? = null
        var nextShare: DiskShare? = null
        var connected = false
        try {
            nextConnection = nextClient.connect(info.host, info.port)
            val auth = AuthenticationContext(info.username, info.password.toCharArray(), info.domain.orEmpty())
            nextSession = nextConnection.authenticate(auth)
            nextShare = nextSession.connectShare(info.share) as? DiskShare
                ?: error("不是磁盘共享：${info.share}")
            return SmbResources(
                nextClient,
                nextConnection,
                nextSession,
                nextShare,
                IdleResourceVerifier(idleNanos = IDLE_PROBE_NANOS) { probeConnection(nextConnection) },
            ).also { connected = true }
        } catch (e: Exception) {
            runCatching { nextShare?.close() }
            runCatching { nextSession?.close() }
            runCatching { nextConnection?.close() }
            runCatching { nextClient.close() }
            throw e
        } finally {
            logPerformance("connect", started, 0L, null, count = if (connected) 1 else 0)
        }
    }

    private fun isRetryable(error: Throwable): Boolean = error is IOException ||
        error is TransportException ||
        (error is SMBRuntimeException && error !is SMBApiException)

    private fun probeConnection(connection: Connection): Boolean {
        val started = System.nanoTime()
        var future: java.util.concurrent.Future<SMB2Echo>? = null
        var success = false
        try {
            future = connection.send(SMB2Echo(connection.negotiatedProtocol.dialect))
            future.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            success = true
            return true
        } catch (_: Exception) {
            future?.cancel(true)
            return false
        } finally {
            logPerformance("probe", started, 0L, null, count = if (success) 1 else 0)
        }
    }

    private fun join(parent: String, child: String): String = listOf(parent.trim('/'), child.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

    private inner class SmbInputStream(private val path: String) : InputStream() {
        private var opened = openRetainedFile(path)
        val readBufferSize = opened.readBufferSize
        private var offset = 0L
        private var mayReconnect = true
        private var closed = false
        private var ownsLease = true
        private val started = System.nanoTime()
        private var totalBytes = 0L

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
                    runCatching { opened.file.close() }
                    synchronized(resources) {
                        opened.resource.release()
                        ownsLease = false
                        resources.invalidate(opened.generation)
                    }
                    opened = openRetainedFile(path).also { ownsLease = true }
                },
            ) {
                opened.file.read(buffer, offset, start, length)
            }
            if (read > 0) {
                offset += read
                totalBytes += read
                opened.resource.markActive()
            }
            return read
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { opened.file.close() }
            if (ownsLease) {
                opened.resource.release()
                ownsLease = false
            }
            logPerformance("stream", started, opened.generation, path, bytes = totalBytes, count = 1)
        }
    }

    private fun logPerformance(
        operation: String,
        startedNanos: Long,
        generation: Long,
        path: String?,
        bytes: Long = 0L,
        count: Int = 0,
    ) {
        val durationMs = (System.nanoTime() - startedNanos) / 1_000_000
        val pathId = path?.hashCode()?.toUInt()?.toString(16) ?: "none"
        runCatching {
            Log.d(TAG, "operation=$operation durationMs=$durationMs bytes=$bytes count=$count generation=$generation pathId=$pathId")
        }
    }

    private data class ShareLease(
        val share: DiskShare,
        val generation: Long,
        val readBufferSize: Int,
        val resource: SmbResources,
    )
    private data class OpenedFile(
        val file: SmbFile,
        val generation: Long,
        val readBufferSize: Int,
        val resource: SmbResources,
    )

    private data class SmbResources(
        val client: SMBClient,
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
        val verifier: IdleResourceVerifier,
    ) {
        val readBufferSize = connection.negotiatedProtocol.maxReadSize.coerceIn(64 * 1024, 1024 * 1024)

        fun isUsable() = verifier.isUsable(share.isConnected && connection.isConnected)

        fun retain() = verifier.retain()

        fun release() = verifier.release()

        fun markActive() = verifier.markActive()

        fun close() {
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }

    private companion object {
        const val TAG = "SmbFileSource"
        const val PROBE_TIMEOUT_SECONDS = 1L
        val IDLE_PROBE_NANOS = TimeUnit.SECONDS.toNanos(5)
    }
}
