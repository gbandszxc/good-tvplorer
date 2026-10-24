package com.github.gbandszxc.goodtvplorer.data

import android.util.Log
import com.github.gbandszxc.goodtvplorer.domain.FileTypeDetector
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
import com.hierynomus.smbj.share.AsyncFileReader
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo1
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
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
import java.util.ArrayDeque
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

internal fun smbReadBufferSize(negotiatedMaxReadSize: Int): Int =
    negotiatedMaxReadSize.coerceIn(64 * 1024, 1024 * 1024)

internal fun smbAuthenticationContext(info: SmbConnectionInfo): AuthenticationContext =
    if (info.username.isBlank() && info.password.isEmpty()) {
        AuthenticationContext.anonymous()
    } else {
        AuthenticationContext(info.username, info.password.toCharArray(), info.domain.orEmpty())
    }

/** 将来源路径拆分为目标共享名与该共享内的相对路径；连接未绑定共享时以路径首段为目标共享。 */
internal fun resolveSmbSharePath(connectionShare: String, path: String): Pair<String, String> {
    val share = connectionShare.trim().trim('/')
    if (share.isNotEmpty()) return share to path.trim().trim('/')
    val trimmed = path.trim().trim('/')
    check(trimmed.isNotEmpty()) { "SMB 根目录需要先选择共享目录" }
    return if ('/' in trimmed) trimmed.substringBefore('/') to trimmed.substringAfter('/') else trimmed to ""
}

internal fun smbIsDiskShareType(type: Int): Boolean = type and SMB_SHARE_TYPE_MASK == 0

/** 将服务器枚举出的共享过滤为磁盘共享并按名称排序，作为来源根目录展示。 */
internal fun smbServerShareItems(
    shares: List<NetShareInfo1>,
    sourceKey: String,
    sourceKind: SourceKind,
): List<FileItem> = shares.asSequence()
    .filter { smbIsDiskShareType(it.type) }
    .map { share ->
        FileItem(
            name = share.netName,
            handle = FileHandle(sourceKey, sourceKind, share.netName),
            kind = FileKind.Directory,
            size = null,
            modifiedAtMillis = null,
        )
    }
    .sortedBy { it.name.lowercase() }
    .toList()

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
            if (info.share.isBlank() && path.trim('/').isEmpty()) {
                listServerShares(started).also { count = it.size }
            } else {
                val (shareName, relativePath) = resolveSmbSharePath(info.share, path)
                withShare(shareName) { lease ->
                    generation = lease.generation
                    lease.share.list(relativePath)
                        .filterNot { it.fileName == "." || it.fileName == ".." }
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
            val (shareName, relativePath) = resolveSmbSharePath(info.share, path)
            withOpenFile(shareName, relativePath) { opened ->
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
        val started = System.nanoTime()
        var copiedBytes = 0L
        try {
            val (shareName, relativePath) = resolveSmbSharePath(info.share, path)
            val (fileSize, chunkSize) = withShare(shareName) { lease ->
                lease.share.getFileInformation(relativePath).standardInformation.endOfFile to lease.readBufferSize
            }
            if (fileSize >= PIPELINED_COPY_MIN_BYTES) {
                copyToPipelined(shareName, relativePath, partial, fileSize, chunkSize)
            } else {
                val coroutine = currentCoroutineContext()
                openStream(path).use { input ->
                    partial.outputStream().use { output ->
                        copyStreamCancellable(input, output, ensureActive = coroutine::ensureActive)
                    }
                }
            }
            check(commitCacheFile(partial, target)) { "无法提交缓存文件：${target.name}" }
            copiedBytes = fileSize
        } finally {
            partial.delete()
            logPerformance("copyTo", started, 0L, path, bytes = copiedBytes, count = 1)
        }
    }

    private suspend fun copyToPipelined(
        shareName: String,
        relativePath: String,
        target: File,
        fileSize: Long,
        chunkSize: Int,
    ) {
        val coroutine = currentCoroutineContext()
        withShare(shareName) { lease ->
            openFile(lease.share, relativePath).use { file ->
                target.outputStream().buffered(chunkSize).use { output ->
                    val pending = ArrayDeque<PendingRead>()
                    var nextOffset = 0L
                    try {
                        while (nextOffset < fileSize || pending.isNotEmpty()) {
                            coroutine.ensureActive()
                            while (nextOffset < fileSize && pending.size < READ_AHEAD_REQUESTS) {
                                val length = minOf(chunkSize.toLong(), fileSize - nextOffset).toInt()
                                pending.addLast(PendingRead(length, AsyncFileReader.read(file, nextOffset, length)))
                                nextOffset += length
                            }
                            val request = pending.removeFirst()
                            val response = request.future.get()
                            check(response.dataLength == request.length) { "SMB 文件读取提前结束：$relativePath" }
                            output.write(response.data, 0, response.dataLength)
                        }
                    } finally {
                        pending.forEach { it.future.cancel(true) }
                    }
                }
            }
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

    private fun openRetainedFile(path: String): OpenedFile {
        val (shareName, relativePath) = resolveSmbSharePath(info.share, path)
        return withShare(shareName) { lease ->
            OpenedFile(openFile(lease.share, relativePath), lease.generation, lease.readBufferSize)
        }
    }

    private fun <T> withOpenFile(shareName: String, relativePath: String, block: (OpenedFile) -> T): T =
        withShare(shareName) { lease ->
            val opened = OpenedFile(openFile(lease.share, relativePath), lease.generation, lease.readBufferSize)
            opened.file.use { block(opened) }
        }

    private fun <T> withShare(shareName: String, block: (ShareLease) -> T): T = withSession { lease ->
        block(
            ShareLease(
                share = connectDiskShare(lease.session, shareName),
                generation = lease.generation,
                readBufferSize = lease.readBufferSize,
            ),
        )
    }

    private fun <T> withSession(block: (SessionLease) -> T): T = try {
        val started = System.nanoTime()
        useResourceWithRetryOnce(
            resources = resources,
            retryable = ::isRetryable,
        ) { current ->
            logPerformance("resourceAcquire", started, current.generation, null, count = 1)
            block(
                SessionLease(
                    session = current.value.session,
                    generation = current.generation,
                    readBufferSize = current.value.readBufferSize,
                ),
            )
        }
    } catch (e: Exception) {
        throwSmbFailure(e)
    }

    /** 复用会话内缓存的树连接；缓存连接失效时重新树连接，保持与旧连接级复用一致的自愈行为。 */
    private fun connectDiskShare(session: Session, shareName: String): DiskShare {
        val share = session.connectShare(shareName) as? DiskShare ?: error("不是磁盘共享：$shareName")
        if (share.isConnected) return share
        runCatching { share.close() }
        return session.connectShare(shareName) as? DiskShare ?: error("不是磁盘共享：$shareName")
    }

    /** 共享目录未配置时，浏览根目录通过 MS-SRVS 枚举服务器暴露的共享。 */
    private suspend fun listServerShares(started: Long): List<FileItem> = withContext(Dispatchers.IO) {
        var count = 0
        try {
            SMBClient(config).use { client ->
                client.connect(info.host, info.port).use { connection ->
                    val session = connection.authenticate(smbAuthenticationContext(info))
                    val transport = SMBTransportFactories.SRVSVC.getTransport(session)
                    val shares = ServerService(transport).getShares1()
                    smbServerShareItems(shares, key, kind).also { count = it.size }
                }
            }
        } catch (e: Exception) {
            throwSmbFailure(e)
        } finally {
            logPerformance("listShares", started, 0L, null, count = count)
        }
    }

    private fun connectResources(): SmbResources {
        val started = System.nanoTime()
        val nextClient = SMBClient(config)
        var nextConnection: Connection? = null
        var nextSession: Session? = null
        var connected = false
        try {
            nextConnection = nextClient.connect(info.host, info.port)
            nextSession = nextConnection.authenticate(smbAuthenticationContext(info))
            connected = true
            return SmbResources(nextClient, nextConnection, nextSession)
        } catch (e: Exception) {
            runCatching { nextSession?.close() }
            runCatching { nextConnection?.close(true) }
            runCatching { nextClient.close() }
            throw e
        } finally {
            logPerformance("connect", started, 0L, null, count = if (connected) 1 else 0)
        }
    }

    private fun isRetryable(error: Throwable): Boolean = error is IOException ||
        error is TransportException ||
        (error is SMBRuntimeException && error !is SMBApiException)

    private fun join(parent: String, child: String): String = listOf(parent.trim('/'), child.trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

    private inner class SmbInputStream(private val path: String) : InputStream() {
        private var opened = openRetainedFile(path)
        val readBufferSize = opened.readBufferSize
        private var offset = 0L
        private var mayReconnect = true
        private var closed = false
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
                        resources.invalidate(opened.generation)
                    }
                    opened = openRetainedFile(path)
                },
            ) {
                opened.file.read(buffer, offset, start, length)
            }
            if (read > 0) {
                offset += read
                totalBytes += read
            }
            return read
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { opened.file.close() }
            resources.invalidate(opened.generation)
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

    private data class SessionLease(
        val session: Session,
        val generation: Long,
        val readBufferSize: Int,
    )
    private data class ShareLease(
        val share: DiskShare,
        val generation: Long,
        val readBufferSize: Int,
    )
    private data class OpenedFile(
        val file: SmbFile,
        val generation: Long,
        val readBufferSize: Int,
    )

    private data class SmbResources(
        val client: SMBClient,
        val connection: Connection,
        val session: Session,
    ) {
        val readBufferSize = smbReadBufferSize(connection.negotiatedProtocol.maxReadSize)

        fun isUsable() = connection.isConnected

        fun close() {
            runCatching { connection.close(true) }
            runCatching { client.close() }
        }
    }

    private companion object {
        const val TAG = "SmbFileSource"
        const val READ_AHEAD_REQUESTS = 4
        const val PIPELINED_COPY_MIN_BYTES = 4L * 1024 * 1024
    }

    private data class PendingRead(
        val length: Int,
        val future: java.util.concurrent.Future<com.hierynomus.mssmb2.messages.SMB2ReadResponse>,
    )
}

private const val SMB_SHARE_TYPE_MASK = 0x3
