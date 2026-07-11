package com.github.gbandszxc.goodtvplorer.domain

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.buffer
import okio.source

class ImageModelKeyer : Keyer<ImageModel> {
    override fun key(data: ImageModel, options: Options): String = data.cacheKey
}

class FileSourceImageFetcher(
    private val data: ImageModel,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val opened = data.open()
        return try {
            SourceFetchResult(
                source = ImageSource(opened.stream.source().buffer(), options.fileSystem),
                mimeType = data.item.name.imageMimeType(),
                dataSource = opened.dataSource,
            )
        } catch (error: Throwable) {
            opened.stream.close()
            throw error
        }
    }

    class Factory : Fetcher.Factory<ImageModel> {
        override fun create(data: ImageModel, options: Options, imageLoader: ImageLoader): Fetcher =
            FileSourceImageFetcher(data, options)
    }
}

private fun String.imageMimeType(): String? = when (substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic", "heif" -> "image/heic"
    "bmp" -> "image/bmp"
    else -> null
}
