package com.github.gbandszxc.goodtvplorer.domain

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

data class StreamingMedia(val source: FileSource, val item: FileItem) {
    val uri: Uri = Uri.parse("goodtvplorer://media/${Uri.encode(item.name)}")
}

data class TimedTextCue(val startMs: Long, val endMs: Long, val text: String)

class FileSourceDataSource(private val media: StreamingMedia) : BaseDataSource(false) {
    private var position = 0L
    private var remaining = 0L
    private var opened = false
    private var readBuffer = ByteArray(0)
    private var bufferPosition = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val size = media.item.size ?: C.LENGTH_UNSET.toLong()
        position = dataSpec.position
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size != C.LENGTH_UNSET.toLong() -> (size - position).coerceAtLeast(0)
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        readBuffer = ByteArray(0)
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (position !in bufferPosition until bufferPosition + readBuffer.size) {
            val requested = minOf(
                maxOf(length, READ_AHEAD_BYTES).toLong(),
                remaining.takeIf { it != C.LENGTH_UNSET.toLong() } ?: MAX_READ_BYTES.toLong(),
                MAX_READ_BYTES.toLong(),
            ).toInt()
            bufferPosition = position
            readBuffer = runBlocking(Dispatchers.IO) { media.source.readRange(media.item.handle.path, position, requested) }
            if (readBuffer.isEmpty()) return C.RESULT_END_OF_INPUT
        }
        val sourceOffset = (position - bufferPosition).toInt()
        val copied = minOf(length.toLong(), (readBuffer.size - sourceOffset).toLong(), remaining.takeIf { it != C.LENGTH_UNSET.toLong() } ?: length.toLong()).toInt()
        readBuffer.copyInto(destination = buffer, destinationOffset = offset, startIndex = sourceOffset, endIndex = sourceOffset + copied)
        position += copied
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= copied
        bytesTransferred(copied)
        return copied
    }

    override fun getUri(): Uri = media.uri

    override fun close() {
        if (opened) transferEnded()
        opened = false
    }

    class Factory(private val media: StreamingMedia) : DataSource.Factory {
        override fun createDataSource(): DataSource = FileSourceDataSource(media)
    }

    private companion object {
        const val MAX_READ_BYTES = 1024 * 1024
        const val READ_AHEAD_BYTES = 256 * 1024
    }
}

fun parseLrc(text: String): List<TimedTextCue> {
    val entries = text.lineSequence().flatMap { line ->
        val content = line.replace(LRC_TIME, "").trim()
        LRC_TIME.findAll(line).mapNotNull { match -> lrcTime(match.groupValues)?.let { it to content } }
    }.sortedBy(Pair<Long, String>::first).toList()
    return entries.mapIndexed { index, entry ->
        TimedTextCue(entry.first, entries.getOrNull(index + 1)?.first ?: Long.MAX_VALUE, entry.second)
    }
}

fun parseSrt(text: String): List<TimedTextCue> = text.replace("\r\n", "\n").trim().split(Regex("\n{2,}")).mapNotNull { block ->
    val lines = block.lines()
    val timeIndex = lines.indexOfFirst { "-->" in it }
    if (timeIndex < 0) return@mapNotNull null
    val times = lines[timeIndex].split("-->", limit = 2).map(String::trim)
    val start = srtTime(times.getOrNull(0)) ?: return@mapNotNull null
    val end = srtTime(times.getOrNull(1)) ?: return@mapNotNull null
    TimedTextCue(start, end, lines.drop(timeIndex + 1).joinToString("\n").trim())
}

private val LRC_TIME = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

private fun lrcTime(parts: List<String>): Long? {
    val minutes = parts.getOrNull(1)?.toLongOrNull() ?: return null
    val seconds = parts.getOrNull(2)?.toLongOrNull() ?: return null
    val fraction = parts.getOrNull(3).orEmpty()
    val millis = when (fraction.length) { 1 -> fraction.toLongOrNull()?.times(100); 2 -> fraction.toLongOrNull()?.times(10); else -> fraction.take(3).padEnd(3, '0').toLongOrNull() } ?: 0
    return (minutes * 60 + seconds) * 1000 + millis
}

private fun srtTime(value: String?): Long? {
    val parts = value?.split(':', ',') ?: return null
    if (parts.size != 4) return null
    val numbers = parts.map { it.toLongOrNull() ?: return null }
    return ((numbers[0] * 60 + numbers[1]) * 60 + numbers[2]) * 1000 + numbers[3]
}
