package com.github.gbandszxc.goodtvplorer.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheRepository(context: Context) {
    private val cacheDir = context.cacheDir
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { cacheDir.walkTopDown().filter(File::isFile).sumOf(File::length) }
    suspend fun clear() = withContext(Dispatchers.IO) { cacheDir.listFiles().orEmpty().forEach(File::deleteRecursively) }
}
