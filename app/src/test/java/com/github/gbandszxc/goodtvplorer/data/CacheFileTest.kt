package com.github.gbandszxc.goodtvplorer.data

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheFileTest {
    private val directory = createTempDirectory("good-tvplorer-cache-").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `commit replaces invalid empty cache atomically`() {
        val target = File(directory, "target.jpg").apply { writeBytes(ByteArray(0)) }
        val partial = File(directory, "partial.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertTrue(commitCacheFile(partial, target))
        assertContentEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertFalse(partial.exists())
    }

    @Test
    fun `commit failure never creates final cache`() {
        val target = File(directory, "target.jpg")
        val missingPartial = File(directory, "missing.part")

        assertFalse(commitCacheFile(missingPartial, target))
        assertFalse(target.exists())
    }
}
