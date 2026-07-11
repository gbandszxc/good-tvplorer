package com.goodtvplorer.domain

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class PreviewMetadata(
    val entries: List<PreviewMetadataEntry> = emptyList(),
)

data class PreviewMetadataEntry(
    val label: String,
    val value: String,
)

class PreviewMetadataRepository(
    private val thumbnails: ThumbnailRepository,
    private val audioCache: AudioCacheManager,
) {
    suspend fun read(source: FileSource, item: FileItem): PreviewMetadata = withContext(Dispatchers.IO) {
        when (item.kind) {
            FileKind.Image -> imageMetadata(thumbnails.cachedImageFile(source, item))
            FileKind.Audio -> mediaMetadata(audioCache.cachedFile(source, item.handle), isAudio = true)
            FileKind.Video -> mediaMetadata(thumbnails.cachedMediaFile(source, item.handle), isAudio = false)
            FileKind.Directory, FileKind.Text, FileKind.Other -> PreviewMetadata()
        }
    }

    private fun imageMetadata(file: File): PreviewMetadata {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return PreviewMetadata(
            buildList {
                if (options.outWidth > 0 && options.outHeight > 0) add(PreviewMetadataEntry("分辨率", "${options.outWidth} × ${options.outHeight}"))
                options.outMimeType?.substringAfterLast('/')?.uppercase()?.let { add(PreviewMetadataEntry("格式", it)) }
            },
        )
    }

    private fun mediaMetadata(file: File, isAudio: Boolean): PreviewMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val metadata = { key: Int -> retriever.extractMetadata(key)?.takeIf(String::isNotBlank) }
            PreviewMetadata(
                buildList {
                    metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { add(PreviewMetadataEntry("时长", formatDuration(it))) }
                    metadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { add(PreviewMetadataEntry("码率", formatBitrate(it))) }
                    if (isAudio) {
                        metadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { add(PreviewMetadataEntry("标题", it)) }
                        metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { add(PreviewMetadataEntry("艺术家", it)) }
                        metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { add(PreviewMetadataEntry("专辑", it)) }
                        metadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { add(PreviewMetadataEntry("流派", it)) }
                        metadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { add(PreviewMetadataEntry("年份", it)) }
                        metadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { add(PreviewMetadataEntry("音轨", it)) }
                    } else {
                        val width = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val height = metadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        if (width != null && height != null) add(PreviewMetadataEntry("分辨率", "$width × $height"))
                        metadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { add(PreviewMetadataEntry("格式", it.substringAfterLast('/').uppercase())) }
                    }
                },
            )
        } finally {
            retriever.release()
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    private fun formatBitrate(bitsPerSecond: Long): String = "${(bitsPerSecond / 1000f).toInt()} kbps"
}
