package com.goodtvplorer.domain

import coil3.decode.DataSource
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.SourceKind
import com.goodtvplorer.data.cacheKey
import java.io.File
import java.io.InputStream

data class ImageModel(
    val source: FileSource,
    val item: FileItem,
    val cachedImageFile: File? = null,
) {
    val cacheKey: String = item.cacheKey()

    suspend fun open(): OpenedImage {
        cachedImageFile?.takeIf { it.exists() && it.length() > 0L }?.let {
            return OpenedImage(it.inputStream(), DataSource.DISK)
        }
        return OpenedImage(
            source.openStream(item.handle.path),
            if (item.handle.sourceKind == SourceKind.Smb) DataSource.NETWORK else DataSource.DISK,
        )
    }
}

data class OpenedImage(val stream: InputStream, val dataSource: DataSource)
