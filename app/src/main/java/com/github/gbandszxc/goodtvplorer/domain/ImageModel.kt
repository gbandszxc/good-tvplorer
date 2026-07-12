package com.github.gbandszxc.goodtvplorer.domain

import coil3.decode.DataSource
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import com.github.gbandszxc.goodtvplorer.data.cacheKey
import java.io.InputStream

data class ImageModel(
    val source: FileSource,
    val item: FileItem,
) {
    val cacheKey: String = item.cacheKey()

    suspend fun open(): OpenedImage {
        return OpenedImage(
            source.openStream(item.handle.path),
            if (item.handle.sourceKind == SourceKind.Smb) DataSource.NETWORK else DataSource.DISK,
        )
    }
}

data class OpenedImage(val stream: InputStream, val dataSource: DataSource)
