package com.github.gbandszxc.goodtvplorer.data

import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalFileSourceTest {
    private val root = createTempDirectory("good-tvplorer-local-").toFile()
    private val source = LocalFileSource(root)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `root lists direct children with relative handles`() = runBlocking {
        File(root, "Movies").mkdir()
        File(root, "notes.txt").writeText("hello")

        val items = source.list("")

        assertEquals(listOf("Movies", "notes.txt"), items.map { it.name })
        assertEquals(listOf("Movies", "notes.txt"), items.map { it.handle.path })
    }

    @Test
    fun `paths cannot escape the shared storage root`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            source.list("../outside")
        }
        Unit
    }

    @Test
    fun `legacy absolute path becomes a relative path`() {
        val movies = File(root, "Movies")

        assertEquals("Movies", source.normalizePath(movies.absolutePath))
        assertEquals("", source.normalizePath(root.absolutePath))
    }
}
