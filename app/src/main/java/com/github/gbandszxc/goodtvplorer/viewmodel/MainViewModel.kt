package com.github.gbandszxc.goodtvplorer.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.gbandszxc.goodtvplorer.data.FileHandle
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.data.FileSource
import com.github.gbandszxc.goodtvplorer.data.LocalFileSource
import com.github.gbandszxc.goodtvplorer.data.SmbConnectionInfo
import com.github.gbandszxc.goodtvplorer.data.SmbFileSource
import com.github.gbandszxc.goodtvplorer.data.SourceKind
import com.github.gbandszxc.goodtvplorer.data.cacheKey
import com.github.gbandszxc.goodtvplorer.data.persistence.BrowserNavigationRepository
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import com.github.gbandszxc.goodtvplorer.data.persistence.SmbConnectionRepository
import com.github.gbandszxc.goodtvplorer.domain.AudioLyricsRepository
import com.github.gbandszxc.goodtvplorer.domain.StreamingMedia
import com.github.gbandszxc.goodtvplorer.domain.TimedTextCue
import com.github.gbandszxc.goodtvplorer.domain.parseSrt
import com.github.gbandszxc.goodtvplorer.domain.ImageModel
import com.github.gbandszxc.goodtvplorer.domain.PreviewMetadata
import com.github.gbandszxc.goodtvplorer.domain.PreviewMetadataRepository
import com.github.gbandszxc.goodtvplorer.domain.ThumbnailRepository
import com.github.gbandszxc.goodtvplorer.ui.preview.AudioPlaybackController
import com.github.gbandszxc.goodtvplorer.ui.preview.AudioPlaybackState
import com.github.gbandszxc.goodtvplorer.ui.preview.nextQueueIndex
import com.github.gbandszxc.goodtvplorer.ui.preview.previousQueueIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.EmptyCoroutineContext

sealed interface Screen {
    data object Network : Screen
    data class Browser(val sourceKey: String, val path: String) : Screen
    data class ImageViewer(val sourceKey: String, val path: String, val name: String) : Screen
    data class TextPreview(val sourceKey: String, val path: String, val name: String) : Screen
    data class AudioPreview(val sourceKey: String, val path: String, val name: String) : Screen
    data class VideoPreview(val sourceKey: String, val path: String, val name: String) : Screen
}

enum class BrowserViewMode { List, Grid }

enum class BrowserSortField { Name, Size, ModifiedTime }

enum class SortDirection { Ascending, Descending }

data class BrowserSort(
    val field: BrowserSortField = BrowserSortField.Name,
    val direction: SortDirection = SortDirection.Ascending,
)

data class BrowserState(
    val loading: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val error: String? = null,
)

internal class BrowserMemoryCache {
    private val states = mutableMapOf<Screen.Browser, BrowserState>()
    operator fun get(screen: Screen.Browser): BrowserState? = states[screen]
    operator fun set(screen: Screen.Browser, state: BrowserState) { states[screen] = state }
    fun remove(screen: Screen.Browser) { states.remove(screen) }
    fun clear() = states.clear()
}

data class PreviewState(
    val loading: Boolean = false,
    val file: File? = null,
    val image: ImageModel? = null,
    val placeholder: File? = null,
    val text: String = "",
    val truncated: Boolean = false,
    val media: StreamingMedia? = null,
    val timedText: List<TimedTextCue> = emptyList(),
    val cover: File? = null,
    val lyricsLoading: Boolean = false,
    val lyricsSource: String? = null,
    val error: String? = null,
)

data class BrowserPreviewMetadataState(
    val itemKey: String? = null,
    val loading: Boolean = false,
    val metadata: PreviewMetadata = PreviewMetadata(),
    val textSnippet: String? = null,
)

data class MainUiState(
    val screen: Screen = Screen.Browser("local", ""),
    val smbConnections: List<SmbConnectionInfo> = emptyList(),
    val browser: BrowserState = BrowserState(loading = true),
    val focusAnchorPath: String? = null,
    val preview: PreviewState = PreviewState(),
    val previewItems: List<FileItem> = emptyList(),
    val thumbnails: Map<String, File> = emptyMap(),
    val browserViewMode: BrowserViewMode = BrowserViewMode.Grid,
    val browserSort: BrowserSort = BrowserSort(),
    val browserSearchQuery: String = "",
    val browserSearchItems: List<FileItem>? = null,
    val browserSearchLoading: Boolean = false,
    val browserPreviewMetadata: BrowserPreviewMetadataState = BrowserPreviewMetadataState(),
    val fontScale: Float = 1f,
)

internal fun filterAndSortBrowserItems(
    items: List<FileItem>,
    query: String,
    sort: BrowserSort,
): List<FileItem> {
    val normalizedQuery = query.trim()
    val itemComparator = Comparator<FileItem> { left, right ->
        val primary = when (sort.field) {
            BrowserSortField.Name -> compareFileNames(left, right)
            BrowserSortField.Size -> (left.size ?: 0L).compareTo(right.size ?: 0L)
            BrowserSortField.ModifiedTime -> (left.modifiedAtMillis ?: 0L).compareTo(right.modifiedAtMillis ?: 0L)
        }
        val directed = if (sort.direction == SortDirection.Descending) -primary else primary
        if (directed != 0) {
            directed
        } else {
            compareFileNames(left, right)
                .takeIf { it != 0 }
                ?: left.handle.path.compareTo(right.handle.path)
        }
    }
    return items.asSequence()
        .filter { normalizedQuery.isBlank() || it.name.contains(normalizedQuery, ignoreCase = true) }
        .sortedWith(compareBy<FileItem> { it.kind != FileKind.Directory }.then(itemComparator))
        .toList()
}

internal fun previewItems(items: List<FileItem>, sort: BrowserSort, selected: FileItem): List<FileItem> =
    filterAndSortBrowserItems(items, "", sort).filter { it.kind == selected.kind }.let {
        if (it.any { item -> item.handle == selected.handle }) it else listOf(selected)
    }

private fun compareFileNames(left: FileItem, right: FileItem): Int =
    left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))

internal suspend fun searchDirectoryTree(
    source: FileSource,
    rootPath: String,
    rootItems: List<FileItem>,
    query: String,
): List<FileItem> {
    val matches = mutableListOf<FileItem>()
    val visitedDirectories = mutableSetOf(rootPath)

    suspend fun visit(items: List<FileItem>) {
        items.forEach { item ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (item.name.contains(query, ignoreCase = true)) matches += item
            if (item.kind == FileKind.Directory && visitedDirectories.add(item.handle.path)) {
                val children = try {
                    source.list(item.handle.path)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emptyList()
                }
                visit(children)
            }
        }
    }

    visit(rootItems)
    return matches
}

internal fun navigateBack(
    state: MainUiState,
    openNetwork: () -> Unit,
    openLocal: () -> Unit,
    openBrowser: (sourceKey: String, path: String) -> Unit,
    restore: (MainUiState) -> Unit,
) {
    when (val screen = state.screen) {
        Screen.Network -> openLocal()
        is Screen.Browser -> {
            val parent = screen.path.trim('/').substringBeforeLast('/', "")
            when {
                screen.path.isNotBlank() -> openBrowser(screen.sourceKey, parent)
                screen.sourceKey == "local" -> Unit
                else -> openNetwork()
            }
        }
        is Screen.ImageViewer -> restore(
            state.copy(
                screen = Screen.Browser(screen.sourceKey, screen.path.substringBeforeLast('/', "")),
                preview = PreviewState(),
                previewItems = emptyList(),
            ),
        )
        is Screen.TextPreview, is Screen.AudioPreview, is Screen.VideoPreview -> {
            val current = when (screen) {
                is Screen.TextPreview -> screen.path
                is Screen.AudioPreview -> screen.path
                is Screen.VideoPreview -> screen.path
            }
            openBrowser(
                when (screen) {
                    is Screen.TextPreview -> screen.sourceKey
                    is Screen.AudioPreview -> screen.sourceKey
                    is Screen.VideoPreview -> screen.sourceKey
                },
                current.substringBeforeLast('/', ""),
            )
        }
    }
}

internal fun prepareImageThumbnailWork(
    key: String,
    cancelOtherRequests: Boolean,
    requestThumbnailWork: () -> Boolean,
    cancelAllExcept: (String) -> Unit,
    clearBatch: () -> Unit,
): Boolean {
    val held = requestThumbnailWork()
    if (cancelOtherRequests) {
        cancelAllExcept(key)
        clearBatch()
    }
    return held
}

internal fun restoreImagePreview(
    state: MainUiState,
    cancelPreview: () -> Unit,
    restore: (MainUiState) -> Unit,
) {
    cancelPreview()
    restore(state)
}

internal fun loadImageModel(source: FileSource, item: FileItem): ImageModel = ImageModel(source, item)

internal fun resolveBrowserPath(currentPath: String, enteredPath: String): String {
    val entered = enteredPath.trim().replace('\\', '/')
    val cleaned = entered.trimEnd('/').ifEmpty { if (entered.startsWith('/')) "/" else "" }
    if (entered.startsWith('/')) {
        return if (currentPath.trim().startsWith('/')) cleaned else cleaned.trimStart('/')
    }
    return (if (currentPath.trim().startsWith('/')) "/" else "") + listOf(currentPath.trim('/'), cleaned)
        .filter(String::isNotBlank).joinToString("/")
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val local = LocalFileSource()
    private val connectionsStore = SmbConnectionRepository(app)
    private val displaySettings = DisplaySettingsRepository(app)
    private val navigation = BrowserNavigationRepository(app)
    private val thumbnails = ThumbnailRepository(app)
    private val previewMetadata = PreviewMetadataRepository()
    private val audioLyrics = AudioLyricsRepository()
    private val sources = mutableMapOf<String, FileSource>(local.key to local)
    private val thumbnailSources = mutableMapOf<String, FileSource>(local.key to local)
    private val thumbnailRequests = RefCountedRequestRegistry(viewModelScope)
    private val browserCache = BrowserMemoryCache()
    private val thumbnailSemaphore = Semaphore(3)
    private val pendingThumbnails = mutableMapOf<String, File>()
    private var thumbnailBatchJob: Job? = null
    private var browserJob: Job? = null
    private var searchJob: Job? = null
    private var browserMetadataJob: Job? = null
    private var previewJob: Job? = null
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _audioState = MutableStateFlow(AudioPlaybackState())
    val audioState: StateFlow<AudioPlaybackState> = _audioState.asStateFlow()
    private var audioController: AudioPlaybackController? = null
    private var audioStateJob: Job? = null

    init {
        viewModelScope.launch {
            var wasAudio = false
            state.collectLatest { ui ->
                val isAudio = ui.screen is Screen.AudioPreview
                if (wasAudio && !isAudio) releaseAudioPlayback()
                wasAudio = isAudio
            }
        }
        viewModelScope.launch {
            connectionsStore.all.collectLatest { connections ->
                browserJob?.cancel()
                previewJob?.cancel()
                cancelThumbnailRequests()
                browserCache.clear()
                val oldSources = sources.values.filterIsInstance<SmbFileSource>()
                val oldThumbnailSources = thumbnailSources.values.filterIsInstance<SmbFileSource>()
                sources.keys.filter { it.startsWith("smb:") }.forEach { sources.remove(it) }
                thumbnailSources.keys.filter { it.startsWith("smb:") }.forEach { thumbnailSources.remove(it) }
                withContext(Dispatchers.IO) {
                    oldSources.forEach(SmbFileSource::close)
                    oldThumbnailSources.forEach(SmbFileSource::close)
                }
                connections.forEach { info ->
                    sources["smb:${info.id}"] = SmbFileSource(info)
                    thumbnailSources["smb:${info.id}"] = SmbFileSource(info)
                }
                _state.update { it.copy(smbConnections = connections) }
            }
        }
        viewModelScope.launch {
            displaySettings.fontScale.collectLatest { fontScale ->
                _state.update { it.copy(fontScale = fontScale) }
            }
        }
        openLocal()
    }

    fun openNetwork() {
        viewModelScope.launch {
            navigation.lastNetworkLocation()?.takeIf { sources.containsKey(it.sourceKey) }?.let { location ->
                openBrowser(location.sourceKey, location.path)
            } ?: openNetworkHub()
        }
    }

    fun openNetworkHub() {
        browserJob?.cancel()
        previewJob?.cancel()
        cancelThumbnailRequests()
        _state.update { it.copy(screen = Screen.Network, preview = PreviewState()) }
    }

    fun openLocal() {
        viewModelScope.launch {
            openBrowser(local.key, navigation.locationFor(local.key)?.path.orEmpty())
        }
    }

    fun openSmb(id: String) {
        val sourceKey = "smb:$id"
        viewModelScope.launch {
            openBrowser(sourceKey, navigation.locationFor(sourceKey)?.path.orEmpty())
        }
    }

    fun addSmb(info: SmbConnectionInfo) {
        viewModelScope.launch { connectionsStore.save(info) }
    }

    fun refresh() {
        val screen = _state.value.screen as? Screen.Browser ?: return
        viewModelScope.launch { openBrowser(screen.sourceKey, screen.path, forceRefresh = true) }
    }

    fun onMediaPermissionsChanged() {
        browserCache.clear()
        val screen = (_state.value.screen as? Screen.Browser)?.takeIf { it.sourceKey == local.key } ?: return
        viewModelScope.launch { openBrowser(screen.sourceKey, screen.path, forceRefresh = true) }
    }

    fun toggleBrowserViewMode() {
        _state.update {
            it.copy(browserViewMode = if (it.browserViewMode == BrowserViewMode.Grid) BrowserViewMode.List else BrowserViewMode.Grid)
        }
    }

    fun setBrowserSort(sort: BrowserSort) {
        _state.update { it.copy(browserSort = sort) }
    }

    fun setBrowserSearchQuery(query: String) {
        searchJob?.cancel()
        val screen = _state.value.screen as? Screen.Browser ?: return
        if (query.isBlank()) {
            _state.update { it.copy(browserSearchQuery = "", browserSearchItems = null, browserSearchLoading = false) }
            return
        }
        val source = sources[screen.sourceKey] ?: return
        startBrowserSearch(screen, source, _state.value.browser.items, query)
    }

    fun requestBrowserPreviewMetadata(item: FileItem) {
        val key = thumbKey(item)
        if (_state.value.browserPreviewMetadata.itemKey == key) return
        browserMetadataJob?.cancel()
        if (item.kind !in setOf(FileKind.Image, FileKind.Audio, FileKind.Video, FileKind.Text)) {
            _state.update { it.copy(browserPreviewMetadata = BrowserPreviewMetadataState(itemKey = key)) }
            return
        }
        val source = sources[item.handle.sourceKey] ?: return
        _state.update { it.copy(browserPreviewMetadata = BrowserPreviewMetadataState(itemKey = key, loading = true)) }
        browserMetadataJob = viewModelScope.launch {
            try {
                val metadata = previewMetadata.read(source, item)
                val textSnippet = if (item.kind == FileKind.Text) source.readPrefix(item.handle.path, 1024).toString(Charsets.UTF_8).trim().take(240) else null
                if (_state.value.browserPreviewMetadata.itemKey == key) {
                    _state.update { it.copy(browserPreviewMetadata = BrowserPreviewMetadataState(itemKey = key, metadata = metadata, textSnippet = textSnippet)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_state.value.browserPreviewMetadata.itemKey == key) {
                    _state.update { it.copy(browserPreviewMetadata = BrowserPreviewMetadataState(itemKey = key)) }
                }
            }
        }
    }

    private fun startBrowserSearch(
        screen: Screen.Browser,
        source: FileSource,
        rootItems: List<FileItem>,
        query: String,
    ) {
        searchJob?.cancel()
        _state.update { it.copy(browserSearchQuery = query, browserSearchItems = rootItems, browserSearchLoading = true) }
        searchJob = viewModelScope.launch {
            delay(200)
            val results = searchDirectoryTree(source, screen.path, rootItems, query)
            if (_state.value.screen == screen && _state.value.browserSearchQuery == query) {
                _state.update { it.copy(browserSearchItems = results, browserSearchLoading = false) }
            }
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { displaySettings.setFontScale(value) }
    }

    fun openEnteredPath(path: String) {
        val screen = _state.value.screen as? Screen.Browser ?: return
        viewModelScope.launch { openBrowser(screen.sourceKey, resolveBrowserPath(screen.path, path)) }
    }

    private suspend fun openBrowser(sourceKey: String, requestedPath: String, forceRefresh: Boolean = false) {
        val source = sources[sourceKey] ?: return showError("文件源不存在")
        val path = if (sourceKey == local.key) local.normalizePath(requestedPath) else requestedPath
        val screen = Screen.Browser(sourceKey, path)
        val clearSearch = _state.value.screen != screen
        val focusAnchorPath = navigation.focusAnchorFor(sourceKey, path)
        navigation.recordLocation(sourceKey, path, System.currentTimeMillis())
        browserJob?.cancel()
        searchJob?.cancel()
        browserMetadataJob?.cancel()
        previewJob?.cancel()
        cancelThumbnailRequests()
        if (forceRefresh) browserCache.remove(screen)
        browserCache[screen]?.let { cached ->
            _state.update {
                it.copy(
                    screen = screen,
                    browser = cached,
                    focusAnchorPath = focusAnchorPath,
                    browserSearchQuery = if (clearSearch) "" else it.browserSearchQuery,
                    browserSearchItems = if (clearSearch) null else it.browserSearchItems,
                    browserSearchLoading = if (clearSearch) false else it.browserSearchLoading,
                    browserPreviewMetadata = BrowserPreviewMetadataState(),
                )
            }
            return
        }
        _state.update {
            it.copy(
                screen = screen,
                browser = BrowserState(loading = true),
                focusAnchorPath = focusAnchorPath,
                browserSearchQuery = if (clearSearch) "" else it.browserSearchQuery,
                browserSearchItems = if (clearSearch) null else it.browserSearchItems,
                browserSearchLoading = if (clearSearch) false else it.browserSearchLoading,
                browserPreviewMetadata = BrowserPreviewMetadataState(),
            )
        }
        browserJob = viewModelScope.launch {
            try {
                val items = source.list(path)
                if (_state.value.screen == screen) {
                    val thumbnailKeys = items.asSequence().filter { it.kind == FileKind.Image }.map(::thumbKey).toSet()
                    val browser = BrowserState(items = items)
                    browserCache[screen] = browser
                    _state.update {
                        it.copy(
                            browser = browser,
                            focusAnchorPath = focusAnchorPath,
                            thumbnails = it.thumbnails.filterKeys(thumbnailKeys::contains),
                        )
                    }
                    _state.value.browserSearchQuery.takeIf(String::isNotBlank)?.let { query ->
                        startBrowserSearch(screen, source, items, query)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == screen) {
                    _state.update { it.copy(browser = BrowserState(error = readable(error))) }
                }
            }
        }
    }

    fun openItem(item: FileItem) {
        when (item.kind) {
            FileKind.Directory -> viewModelScope.launch {
                navigation.recordFocusAnchor(
                    sourceKey = item.handle.sourceKey,
                    parentPath = item.handle.path.substringBeforeLast('/', ""),
                    childPath = item.handle.path,
                    updatedAtMillis = System.currentTimeMillis(),
                )
                openBrowser(item.handle.sourceKey, item.handle.path)
            }
            FileKind.Image -> prepareImage(item, previewItems(_state.value.browser.items, _state.value.browserSort, item))
            FileKind.Text -> prepareText(item.handle, item.name)
            FileKind.Audio -> prepareMedia(item, isAudio = true, items = previewItems(_state.value.browser.items, _state.value.browserSort, item))
            FileKind.Video -> prepareMedia(item, isAudio = false, items = previewItems(_state.value.browser.items, _state.value.browserSort, item))
            FileKind.Other -> showError("暂不支持打开此文件类型")
        }
    }

    fun requestThumbnail(item: FileItem) {
        requestThumbnailWork(item)
    }

    private fun requestThumbnailWork(item: FileItem): Boolean {
        if (item.kind !in setOf(FileKind.Image, FileKind.Audio, FileKind.Video)) return false
        val key = thumbKey(item)
        if (_state.value.thumbnails.containsKey(key) || pendingThumbnails.containsKey(key)) return false
        val source = thumbnailSources[item.handle.sourceKey] ?: return false
        thumbnailRequests.request(key) {
            thumbnailSemaphore.withPermit { thumbnails.thumbnailFile(source, item) }
                ?.let { queueThumbnail(key, it) }
        }
        return true
    }

    fun releaseThumbnail(item: FileItem) {
        thumbnailRequests.release(thumbKey(item))
    }

    fun selectPreviewItem(item: FileItem) {
        val items = _state.value.previewItems
        if (item !in items) return
        when (item.kind) {
            FileKind.Image -> prepareImage(item, items)
            FileKind.Audio -> prepareMedia(item, isAudio = true, items = items)
            FileKind.Video -> prepareMedia(item, isAudio = false, items = items)
            else -> Unit
        }
    }

    fun movePreviewItem(offset: Int) {
        val state = _state.value
        val path = when (val screen = state.screen) {
            is Screen.ImageViewer -> screen.path
            is Screen.AudioPreview -> screen.path
            is Screen.VideoPreview -> screen.path
            else -> return
        }
        val index = state.previewItems.indexOfFirst { it.handle.path == path }
        state.previewItems.getOrNull(index + offset)?.let(::selectPreviewItem)
    }

    /** 视频预览失败后重试当前条目：复用预览列表重新走媒体准备流程。 */
    fun retryPreview() {
        val state = _state.value
        val path = (state.screen as? Screen.VideoPreview)?.path ?: return
        state.previewItems.firstOrNull { it.handle.path == path }?.let(::selectPreviewItem)
    }

    fun toggleAudioPlayPause() {
        audioController?.togglePlayPause()
    }

    fun seekAudio(positionMs: Long) {
        audioController?.seekTo(positionMs)
    }

    fun seekAudioBy(deltaMs: Long) {
        audioController?.seekBy(deltaMs)
    }

    fun cycleAudioPlayMode() {
        audioController?.cyclePlayMode()
    }

    fun startAudioSleepTimer(minutes: Int) {
        audioController?.startSleepTimer(minutes)
    }

    fun cancelAudioSleepTimer() {
        audioController?.cancelSleepTimer()
    }

    fun playNextAudio() {
        advanceAudioQueue(forward = true)
    }

    fun playPreviousAudio() {
        advanceAudioQueue(forward = false)
    }

    private fun advanceAudioQueue(forward: Boolean) {
        if (audioController == null) return
        val items = _state.value.previewItems
        val index = audioQueueIndex(items, forward) ?: return
        items.getOrNull(index)?.let(::selectPreviewItem)
    }

    private fun audioQueueIndex(items: List<FileItem>, forward: Boolean): Int? {
        val screen = _state.value.screen as? Screen.AudioPreview ?: return null
        val current = items.indexOfFirst { it.handle.path == screen.path }
        if (current < 0) return null
        val mode = audioController?.state?.value?.playMode ?: _audioState.value.playMode
        return if (forward) nextQueueIndex(items.size, current, mode) else previousQueueIndex(items.size, current)
    }

    private fun ensureAudioController(): AudioPlaybackController =
        audioController ?: AudioPlaybackController(
            context = getApplication<Application>(),
            scope = viewModelScope,
            onEnded = ::playNextAudio,
            onSleepTimerExpired = ::onAudioSleepTimerExpired,
        ).also { controller ->
            audioController = controller
            audioStateJob?.cancel()
            audioStateJob = viewModelScope.launch {
                controller.state.collectLatest { _audioState.value = it }
            }
        }

    private fun releaseAudioPlayback() {
        audioStateJob?.cancel()
        audioStateJob = null
        audioController?.release()
        audioController = null
        _audioState.value = AudioPlaybackState()
    }

    private fun onAudioSleepTimerExpired() {
        if (_state.value.screen is Screen.AudioPreview) goBack()
        Toast.makeText(getApplication<Application>(), "定时结束，已停止播放", Toast.LENGTH_SHORT).show()
    }

    fun goBack() = navigateBack(_state.value, ::openNetworkHub, ::openLocal, { sourceKey, path ->
        viewModelScope.launch { openBrowser(sourceKey, path) }
    }) { state ->
        restoreImagePreview(
            state = state,
            cancelPreview = {
                previewJob?.cancel()
                previewJob = null
            },
            restore = { _state.value = it },
        )
    }

    private fun prepareImage(item: FileItem, items: List<FileItem>) {
        val source = sources[item.handle.sourceKey] ?: return showError("文件源不存在")
        val screen = Screen.ImageViewer(item.handle.sourceKey, item.handle.path, item.name)
        previewJob?.cancel()
        previewJob = null
        val key = thumbKey(item)
        val holdsThumbnail = prepareImageThumbnailWork(
            key = key,
            cancelOtherRequests = _state.value.screen !is Screen.ImageViewer,
            requestThumbnailWork = { requestThumbnailWork(item) },
            cancelAllExcept = thumbnailRequests::cancelAllExcept,
            clearBatch = ::clearThumbnailBatch,
        )
        _state.update {
            it.copy(
                screen = screen,
                previewItems = items,
                preview = PreviewState(
                    loading = true,
                    placeholder = it.thumbnails[thumbKey(item)],
                ),
            )
        }
        previewJob = viewModelScope.launch {
            try {
                val image = loadImageModel(source, item)
                if (_state.value.screen == screen) {
                    _state.update { it.copy(preview = PreviewState(image = image, placeholder = it.preview.placeholder)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == screen) {
                    _state.update { it.copy(preview = PreviewState(placeholder = it.preview.placeholder, error = readable(error))) }
                }
            } finally {
                if (holdsThumbnail) thumbnailRequests.release(key)
            }
        }
    }

    private fun prepareText(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        val screen = Screen.TextPreview(handle.sourceKey, handle.path, name)
        previewJob?.cancel()
        _state.update { it.copy(screen = screen, preview = PreviewState(loading = true)) }
        previewJob = viewModelScope.launch {
            try {
                val bytes = source.readPrefix(handle.path, 1024 * 1024 + 1)
                val preview = PreviewState(text = bytes.take(1024 * 1024).toByteArray().toString(Charsets.UTF_8), truncated = bytes.size > 1024 * 1024)
                if (_state.value.screen == screen) _state.update { it.copy(preview = preview) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == screen) _state.update { it.copy(preview = PreviewState(error = readable(error))) }
            }
        }
    }

    private fun prepareMedia(item: FileItem, isAudio: Boolean, items: List<FileItem>) {
        val source = sources[item.handle.sourceKey] ?: return showError("文件源不存在")
        if (isAudio) {
            prepareAudio(source, item, items)
            return
        }
        val screen: Screen = Screen.VideoPreview(item.handle.sourceKey, item.handle.path, item.name)
        previewJob?.cancel()
        _state.update { it.copy(screen = screen, previewItems = items, preview = PreviewState(loading = true)) }
        previewJob = viewModelScope.launch {
            try {
                val companion = companionFile(item, "srt")
                val timedText = companion?.let {
                    val bytes = source.readPrefix(it.handle.path, 1024 * 1024 + 1)
                    val text = bytes.take(1024 * 1024).toByteArray().toString(Charsets.UTF_8)
                    parseSrt(text)
                }.orEmpty()
                if (_state.value.screen == screen) _state.update {
                    it.copy(preview = PreviewState(media = StreamingMedia(source, item), timedText = timedText))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == screen) _state.update { it.copy(preview = PreviewState(error = readable(error))) }
            }
        }
    }

    /**
     * 音频分支：立即展示播放器并开始播放，封面与歌词随后异步补齐。
     */
    private fun prepareAudio(source: FileSource, item: FileItem, items: List<FileItem>) {
        val screen = Screen.AudioPreview(item.handle.sourceKey, item.handle.path, item.name)
        val media = StreamingMedia(source, item)
        previewJob?.cancel()
        _state.update {
            it.copy(
                screen = screen,
                previewItems = items,
                preview = PreviewState(media = media, lyricsLoading = true),
            )
        }
        ensureAudioController().load(media)
        previewJob = viewModelScope.launch {
            try {
                val cover = thumbnails.thumbnailFile(source, item)
                if (cover != null && _state.value.screen == screen) {
                    _state.update { it.copy(preview = it.preview.copy(cover = cover)) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 封面失败不影响播放
            }
            // 歌词异常不写入 preview.error，按无歌词处理。
            val lyrics = try {
                audioLyrics.load(source, item, companionFile(item, "lrc"))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (_state.value.screen == screen) {
                _state.update {
                    it.copy(
                        preview = it.preview.copy(
                            timedText = lyrics?.cues.orEmpty(),
                            lyricsLoading = false,
                            lyricsSource = lyrics?.source?.label?.takeIf { label -> label.isNotBlank() },
                        ),
                    )
                }
            }
        }
    }

    private fun companionFile(item: FileItem, extension: String): FileItem? {
        val base = item.name.substringBeforeLast('.', item.name)
        return _state.value.browser.items.firstOrNull {
            it.name.equals("$base.$extension", ignoreCase = true) && it.handle.path.substringBeforeLast('/', "") == item.handle.path.substringBeforeLast('/', "")
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(browser = it.browser.copy(error = message)) }
    }

    private fun readable(e: Throwable): String = e.message ?: "操作失败"

    private fun queueThumbnail(key: String, file: File) {
        pendingThumbnails[key] = file
        if (thumbnailBatchJob?.isActive == true) return
        thumbnailBatchJob = viewModelScope.launch {
            delay(50)
            val batch = pendingThumbnails.toMap()
            pendingThumbnails.clear()
            _state.update { state -> state.copy(thumbnails = state.thumbnails + batch) }
        }
    }

    private fun cancelThumbnailRequests() {
        thumbnailRequests.cancelAll()
        clearThumbnailBatch()
    }

    private fun clearThumbnailBatch() {
        thumbnailBatchJob?.cancel()
        thumbnailBatchJob = null
        pendingThumbnails.clear()
    }

    override fun onCleared() {
        releaseAudioPlayback()
        browserJob?.cancel()
        previewJob?.cancel()
        cancelThumbnailRequests()
        val oldSources = sources.values.filterIsInstance<SmbFileSource>()
        val oldThumbnailSources = thumbnailSources.values.filterIsInstance<SmbFileSource>()
        Dispatchers.IO.dispatch(EmptyCoroutineContext) {
            oldSources.forEach(SmbFileSource::close)
            oldThumbnailSources.forEach(SmbFileSource::close)
        }
        super.onCleared()
    }

    companion object {
        fun thumbKey(item: FileItem): String = item.cacheKey()
    }

}
