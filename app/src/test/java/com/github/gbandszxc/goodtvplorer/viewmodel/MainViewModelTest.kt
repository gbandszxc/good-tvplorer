package com.github.gbandszxc.goodtvplorer.viewmodel

import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainViewModelTest {
    @Test
    fun `browser items keep directories first and sort each group by selected field`() {
        val directory = fileItem("Zoo", FileKind.Directory, size = null, modifiedAtMillis = 3L)
        val small = fileItem("alpha.txt", FileKind.Text, size = 10L, modifiedAtMillis = 1L)
        val large = fileItem("Beta.txt", FileKind.Text, size = 20L, modifiedAtMillis = 2L)

        val sorted = filterAndSortBrowserItems(
            listOf(large, small, directory),
            query = "",
            sort = BrowserSort(BrowserSortField.Size, SortDirection.Descending),
        )

        assertEquals(listOf(directory, large, small), sorted)
    }

    @Test
    fun `browser items sort names both ways and treat missing sizes as zero`() {
        val alpha = fileItem("Alpha", FileKind.Text, size = null, modifiedAtMillis = null)
        val beta = fileItem("beta", FileKind.Text, size = 10L, modifiedAtMillis = null)

        assertEquals(listOf(alpha, beta), filterAndSortBrowserItems(listOf(beta, alpha), "", BrowserSort(BrowserSortField.Name, SortDirection.Ascending)))
        assertEquals(listOf(beta, alpha), filterAndSortBrowserItems(listOf(alpha, beta), "", BrowserSort(BrowserSortField.Name, SortDirection.Descending)))
        assertEquals(listOf(alpha, beta), filterAndSortBrowserItems(listOf(beta, alpha), "", BrowserSort(BrowserSortField.Size, SortDirection.Ascending)))
    }

    @Test
    fun `browser items search names without case sensitivity`() {
        val matching = fileItem("Movie.MKV", FileKind.Video, size = 1L, modifiedAtMillis = 1L)
        val chinese = fileItem("电影.jpg", FileKind.Image, size = 2L, modifiedAtMillis = 2L)

        assertEquals(listOf(matching), filterAndSortBrowserItems(listOf(matching, chinese), "movie", BrowserSort()))
        assertEquals(listOf(chinese), filterAndSortBrowserItems(listOf(matching, chinese), "电影", BrowserSort()))
        assertTrue(filterAndSortBrowserItems(listOf(matching, chinese), "missing", BrowserSort()).isEmpty())
    }

    @Test
    fun `browser items sort modification time in ascending and descending order`() {
        val old = fileItem("old", FileKind.Text, size = null, modifiedAtMillis = 1L)
        val new = fileItem("new", FileKind.Text, size = null, modifiedAtMillis = 2L)

        assertEquals(listOf(old, new), filterAndSortBrowserItems(listOf(new, old), "", BrowserSort(BrowserSortField.ModifiedTime, SortDirection.Ascending)))
        assertEquals(listOf(new, old), filterAndSortBrowserItems(listOf(old, new), "", BrowserSort(BrowserSortField.ModifiedTime, SortDirection.Descending)))
    }

    @Test
    fun `recursive search returns matching items below the current directory`() = runBlocking {
        val root = fileItem("Root", FileKind.Directory, size = null, modifiedAtMillis = null)
        val nested = fileItem("Movies", FileKind.Directory, size = null, modifiedAtMillis = null).copy(
            handle = FileHandle("local", SourceKind.Local, "Movies"),
        )
        val result = fileItem("My Movie.mkv", FileKind.Video, size = 1L, modifiedAtMillis = 1L).copy(
            handle = FileHandle("local", SourceKind.Local, "Movies/My Movie.mkv"),
        )
        val source = DirectorySource(mapOf("" to listOf(root, nested), "Movies" to listOf(result)))

        assertEquals(listOf(nested, result), searchDirectoryTree(source, "", listOf(root, nested), "movie"))
    }
    @Test
    fun entered_path_stays_in_current_source() {
        assertEquals("Movies/2024", resolveBrowserPath("Movies", "2024"))
        assertEquals("Pictures", resolveBrowserPath("Movies/2024", "/Pictures"))
        assertEquals("/storage/emulated/0/Movies/2024", resolveBrowserPath("/storage/emulated/0/Movies", "2024"))
        assertEquals("/Pictures", resolveBrowserPath("/storage/emulated/0/Movies", "/Pictures/"))
        assertEquals("/", resolveBrowserPath("/storage/emulated/0/Movies", "/"))
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
    fun `image viewer follows the current directory sort and excludes non-images`() {
        val later = fileItem("z.jpg", FileKind.Image, size = 1L, modifiedAtMillis = 1L)
        val selected = fileItem("a.png", FileKind.Image, size = 1L, modifiedAtMillis = 1L)
        val text = fileItem("notes.txt", FileKind.Text, size = 1L, modifiedAtMillis = 1L)

        assertEquals(
            listOf(selected, later),
            imageViewerItems(listOf(later, text, selected), BrowserSort(), selected),
        )
        assertEquals(listOf(selected), imageViewerItems(listOf(later), BrowserSort(), selected))
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
            screen = Screen.ImageViewer("smb:nas", item.handle.path, item.name),
            browser = browser,
            preview = PreviewState(loading = true),
        )

        var browserOpens = 0
        var restored: MainUiState? = null

        navigateBack(
            state = state,
            openNetwork = {},
            openLocal = {},
            openBrowser = { _, _ -> browserOpens++ },
            restore = { restored = it },
        )

        assertEquals(0, browserOpens)
        assertEquals(Screen.Browser("smb:nas", "photos"), restored?.screen)
        assertSame(browser, restored?.browser)
        assertEquals(PreviewState(), restored?.preview)
    }

    @Test
    fun `back at local root keeps the local browser open`() {
        var networkOpens = 0
        var localOpens = 0
        navigateBack(
            state = MainUiState(screen = Screen.Browser("local", "")),
            openNetwork = { networkOpens++ },
            openLocal = { localOpens++ },
            openBrowser = { _, _ -> },
            restore = {},
        )
        assertEquals(0, networkOpens)
        assertEquals(0, localOpens)
    }

    @Test
    fun `back from network entry returns to local root`() {
        var localOpens = 0
        navigateBack(
            state = MainUiState(screen = Screen.Network),
            openNetwork = {},
            openLocal = { localOpens++ },
            openBrowser = { _, _ -> },
            restore = {},
        )
        assertEquals(1, localOpens)
    }

    private fun imageItem() = FileItem(
        name = "photo.jpg",
        handle = FileHandle("smb:nas", SourceKind.Smb, "photos/photo.jpg"),
        kind = FileKind.Image,
        size = 1L,
        modifiedAtMillis = 2L,
    )

    private fun fileItem(name: String, kind: FileKind, size: Long?, modifiedAtMillis: Long?) = FileItem(
        name = name,
        handle = FileHandle("local", SourceKind.Local, name),
        kind = kind,
        size = size,
        modifiedAtMillis = modifiedAtMillis,
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

    private class DirectorySource(private val directories: Map<String, List<FileItem>>) : FileSource {
        override val key = "local"
        override val kind = SourceKind.Local
        override val title = "本机"
        override suspend fun list(path: String): List<FileItem> = directories[path].orEmpty()
        override suspend fun readRange(path: String, offset: Long, maxBytes: Int) = ByteArray(0)
        override suspend fun openStream(path: String): InputStream = ByteArrayInputStream(ByteArray(0))
        override suspend fun copyTo(path: String, target: File) = Unit
    }
}
