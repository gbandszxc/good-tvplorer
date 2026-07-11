package com.goodtvplorer.domain

import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.SourceKind
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewMetadataRepositoryTest {
    @Test
    fun `audio metadata reads only the configured SMB prefix`() = runBlocking {
        val source = RecordingSource()
        val item = FileItem(
            name = "song.mp3",
            handle = FileHandle("smb:nas", SourceKind.Smb, "Music/song.mp3"),
            kind = FileKind.Audio,
            size = 10L * 1024 * 1024,
            modifiedAtMillis = null,
        )

        val metadata = PreviewMetadataRepository().read(source, item)

        assertEquals(listOf(0L to 512 * 1024), source.requests)
        assertTrue(metadata.entries.any { it.label == "码率" && it.value == "128 kbps" })
    }

    private class RecordingSource : FileSource {
        val requests = mutableListOf<Pair<Long, Int>>()
        override val key = "smb:nas"
        override val kind = SourceKind.Smb
        override val title = "NAS"
        override suspend fun list(path: String) = emptyList<FileItem>()
        override suspend fun readRange(path: String, offset: Long, maxBytes: Int): ByteArray {
            requests += offset to maxBytes
            return ByteArray(maxBytes).also {
                it[0] = 0xff.toByte()
                it[1] = 0xfb.toByte()
                it[2] = 0x90.toByte()
                it[3] = 0
            }
        }
        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(ByteArray(0))
        override suspend fun copyTo(path: String, target: File) = Unit
    }
}
