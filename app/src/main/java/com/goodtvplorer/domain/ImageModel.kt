package com.goodtvplorer.domain

import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.cacheKey

data class ImageModel(
    val source: FileSource,
    val item: FileItem,
) {
    val cacheKey: String = item.cacheKey()
}
