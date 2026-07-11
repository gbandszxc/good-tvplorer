package com.goodtvplorer.viewmodel

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
import kotlin.test.assertNull
import kotlin.test.assertSame

class MainViewModelTest {
    @Test
    fun entered_path_stays_in_current_source() {
        assertEquals("Movies/2024", resolveBrowserPath("Movies", "2024"))
        assertEquals("Pictures", resolveBrowserPath("Movies/2024", "/Pictures"))
    }

    @Test
    fun `image preview retains selected thumbnail before cancelling other work`() {
        val events = mutableListOf<String>()

        val held = prepareImageThumbnailWork(
            key = "selected",
            requestThumbnailWork = { events += "request"; true },
            cancelAllExcept = { events += "cancel:$it" },
            clearBatch = { events += "clear" },
        )

        assertEquals(true, held)
        assertEquals(listOf("request", "cancel:selected", "clear"), events)
    }

    @Test
    fun `image preview restore cancels preview work before restoring state`() {
        val state = MainUiState(screen = Screen.Browser("smb:nas", "photos"))
        val events = mutableListOf<String>()

        restoreImagePreview(
            state = state,
            cancelPreview = { events += "cancel" },
            restore = {
                assertSame(state, it)
                events += "restore"
            },
        )

        assertEquals(listOf("cancel", "restore"), events)
    }

    @Test
    fun `browser snapshots are reused until refresh invalidates them`() {
        val cache = BrowserMemoryCache()
        val screen = Screen.Browser("smb:nas", "photos")
        val browser = BrowserState(items = listOf(imageItem()))

        assertNull(cache[screen])
        cache[screen] = browser
        assertSame(browser, cache[screen])
        cache.remove(screen)
        assertNull(cache[screen])
    }

    @Test
    fun `image preview model uses the shared cached original`() = runBlocking {
        val source = FakeSource()
        val item = imageItem()
        val cached = File.createTempFile("goodtvplorer-preview", ".jpg").apply { writeText("cached") }
        var cacheCalls = 0

        val model = loadCachedImageModel(source, item) { actualSource, actualItem ->
            assertSame(source, actualSource)
            assertSame(item, actualItem)
            cacheCalls++
            cached
        }

        assertEquals(1, cacheCalls)
        assertSame(cached, model.cachedImageFile)
        cached.delete()
        Unit
    }

    @Test
    fun `image preview back restores existing browser state without reloading`() {
        val item = imageItem()
        val browser = BrowserState(items = listOf(item))
        val state = MainUiState(
            screen = Screen.ImagePreview("smb:nas", item.handle.path, item.name),
            browser = browser,
            preview = PreviewState(loading = true),
        )

        var browserOpens = 0
        var restored: MainUiState? = null

        navigateBack(
            state = state,
            openHome = {},
            openBrowser = { _, _ -> browserOpens++ },
            restore = { restored = it },
        )

        assertEquals(0, browserOpens)
        assertEquals(Screen.Browser("smb:nas", "photos"), restored?.screen)
        assertSame(browser, restored?.browser)
        assertEquals(PreviewState(), restored?.preview)
    }

    private fun imageItem() = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:nas", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = 1L,
        modifiedAtMillis = 2L,
    )

    private class FakeSource : FileSource {
        override val key = "smb:nas"
        override val kind = SourceKind.Smb
        override val title = "NAS"
        override suspend fun list(path: String) = emptyList<FileItem>()
        override suspend fun readRange(path: String, offset: Long, maxBytes: Int) = ByteArray(0)
        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(ByteArray(0))
        override suspend fun copyTo(path: String, target: File) = Unit
    }
}
