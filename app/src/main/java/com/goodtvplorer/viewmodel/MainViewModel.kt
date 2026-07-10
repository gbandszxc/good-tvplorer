package com.goodtvplorer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goodtvplorer.data.FileHandle
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.data.FileSource
import com.goodtvplorer.data.DisplaySettingsStore
import com.goodtvplorer.data.LocalFileSource
import com.goodtvplorer.data.SmbConnectionInfo
import com.goodtvplorer.data.SmbConnectionStore
import com.goodtvplorer.data.SmbFileSource
import com.goodtvplorer.data.SourceKind
import com.goodtvplorer.data.cacheKey
import com.goodtvplorer.domain.AudioCacheManager
import com.goodtvplorer.domain.ImageModel
import com.goodtvplorer.domain.ThumbnailRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import kotlin.coroutines.EmptyCoroutineContext

sealed interface Screen {
    data object Home : Screen
    data class Browser(val sourceKey: String, val path: String) : Screen
    data class ImagePreview(val sourceKey: String, val path: String, val name: String) : Screen
    data class TextPreview(val sourceKey: String, val path: String, val name: String) : Screen
    data class AudioPreview(val sourceKey: String, val path: String, val name: String) : Screen
    data class VideoPreview(val sourceKey: String, val path: String, val name: String) : Screen
}

enum class BrowserViewMode { List, Grid }

data class BrowserState(
    val loading: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val error: String? = null,
)

data class PreviewState(
    val loading: Boolean = false,
    val file: File? = null,
    val image: ImageModel? = null,
    val placeholder: File? = null,
    val text: String = "",
    val truncated: Boolean = false,
    val error: String? = null,
)

data class MainUiState(
    val screen: Screen = Screen.Home,
    val smbConnections: List<SmbConnectionInfo> = emptyList(),
    val browser: BrowserState = BrowserState(),
    val preview: PreviewState = PreviewState(),
    val thumbnails: Map<String, File> = emptyMap(),
    val browserViewMode: BrowserViewMode = BrowserViewMode.Grid,
    val fontScale: Float = 0.85f,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val local = LocalFileSource(app)
    private val store = SmbConnectionStore(app)
    private val display = DisplaySettingsStore(app)
    private val thumbnails = ThumbnailRepository(app)
    private val audioCache = AudioCacheManager(app)
    private val sources = mutableMapOf<String, FileSource>(local.key to local)
    private val thumbnailRequests = RefCountedRequestRegistry(viewModelScope)
    private val thumbnailSemaphore = Semaphore(3)
    private val pendingThumbnails = mutableMapOf<String, File>()
    private var thumbnailBatchJob: Job? = null
    private var browserJob: Job? = null
    private var previewJob: Job? = null
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.connections.collectLatest { connections ->
                browserJob?.cancel()
                previewJob?.cancel()
                cancelThumbnailRequests()
                val oldSources = sources.values.filterIsInstance<SmbFileSource>()
                sources.keys.filter { it.startsWith("smb:") }.forEach { sources.remove(it) }
                withContext(Dispatchers.IO) { oldSources.forEach(SmbFileSource::close) }
                connections.forEach { info -> sources["smb:${info.id}"] = SmbFileSource(info) }
                _state.update { it.copy(smbConnections = connections) }
            }
        }
        viewModelScope.launch {
            display.fontScale.collectLatest { fontScale ->
                _state.update { it.copy(fontScale = fontScale) }
            }
        }
    }

    fun openHome() {
        browserJob?.cancel()
        previewJob?.cancel()
        cancelThumbnailRequests()
        _state.update { it.copy(screen = Screen.Home, preview = PreviewState()) }
    }

    fun openLocal() = openBrowser(local.key, "")

    fun openSmb(id: String) = openBrowser("smb:$id", "")

    fun addSmb(info: SmbConnectionInfo) {
        viewModelScope.launch { store.add(info) }
    }

    fun refresh() {
        val screen = _state.value.screen as? Screen.Browser ?: return
        openBrowser(screen.sourceKey, screen.path)
    }

    fun toggleBrowserViewMode() {
        _state.update {
            it.copy(browserViewMode = if (it.browserViewMode == BrowserViewMode.Grid) BrowserViewMode.List else BrowserViewMode.Grid)
        }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { display.setFontScale(value) }
    }

    fun openBrowser(sourceKey: String, path: String) {
        val source = sources[sourceKey] ?: return showError("文件源不存在")
        browserJob?.cancel()
        previewJob?.cancel()
        cancelThumbnailRequests()
        _state.update { it.copy(screen = Screen.Browser(sourceKey, path), browser = BrowserState(loading = true)) }
        browserJob = viewModelScope.launch {
            try {
                val items = source.list(path)
                if (_state.value.screen == Screen.Browser(sourceKey, path)) {
                    val thumbnailKeys = items.asSequence().filter { it.kind == FileKind.Image }.map(::thumbKey).toSet()
                    _state.update {
                        it.copy(
                            browser = BrowserState(items = items),
                            thumbnails = it.thumbnails.filterKeys(thumbnailKeys::contains),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == Screen.Browser(sourceKey, path)) {
                    _state.update { it.copy(browser = BrowserState(error = readable(error))) }
                }
            }
        }
    }

    fun openItem(item: FileItem) {
        when (item.kind) {
            FileKind.Directory -> openBrowser(item.handle.sourceKey, item.handle.path)
            FileKind.Image -> prepareImage(item)
            FileKind.Text -> prepareText(item.handle, item.name)
            FileKind.Audio -> prepareAudio(item.handle, item.name)
            FileKind.Video -> prepareVideo(item.handle, item.name)
            FileKind.Other -> showError("暂不支持打开此文件类型")
        }
    }

    fun requestThumbnail(item: FileItem) {
        if (item.kind != FileKind.Image) return
        val key = thumbKey(item)
        if (_state.value.thumbnails.containsKey(key) || pendingThumbnails.containsKey(key)) return
        val source = sources[item.handle.sourceKey] ?: return
        thumbnailRequests.request(key) {
            thumbnailSemaphore.withPermit { thumbnails.thumbnailFile(source, item) }
                ?.let { queueThumbnail(key, it) }
        }
    }

    fun releaseThumbnail(item: FileItem) {
        thumbnailRequests.release(thumbKey(item))
    }

    fun goBack() {
        when (val screen = _state.value.screen) {
            Screen.Home -> Unit
            is Screen.Browser -> {
                val parent = screen.path.trim('/').substringBeforeLast('/', "")
                if (screen.path.isBlank()) openHome() else openBrowser(screen.sourceKey, parent)
            }
            is Screen.ImagePreview, is Screen.TextPreview, is Screen.AudioPreview, is Screen.VideoPreview -> {
                val current = when (screen) {
                    is Screen.ImagePreview -> screen.path
                    is Screen.TextPreview -> screen.path
                    is Screen.AudioPreview -> screen.path
                    is Screen.VideoPreview -> screen.path
                }
                openBrowser(
                    sourceKey = when (screen) {
                        is Screen.ImagePreview -> screen.sourceKey
                        is Screen.TextPreview -> screen.sourceKey
                        is Screen.AudioPreview -> screen.sourceKey
                        is Screen.VideoPreview -> screen.sourceKey
                    },
                    path = current.substringBeforeLast('/', ""),
                )
            }
        }
    }

    private fun prepareImage(item: FileItem) {
        val source = sources[item.handle.sourceKey] ?: return showError("文件源不存在")
        previewJob?.cancel()
        cancelThumbnailRequests()
        _state.update {
            it.copy(
                screen = Screen.ImagePreview(item.handle.sourceKey, item.handle.path, item.name),
                preview = PreviewState(
                    image = ImageModel(source, item),
                    placeholder = it.thumbnails[thumbKey(item)],
                ),
            )
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

    private fun prepareAudio(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        val screen = Screen.AudioPreview(handle.sourceKey, handle.path, name)
        previewJob?.cancel()
        _state.update { it.copy(screen = screen, preview = PreviewState(loading = true)) }
        previewJob = viewModelScope.launch {
            try {
                val file = audioCache.cachedFile(source, handle)
                if (_state.value.screen == screen) _state.update { it.copy(preview = PreviewState(file = file)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == screen) _state.update { it.copy(preview = PreviewState(error = readable(error))) }
            }
        }
    }

    private fun prepareVideo(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        val screen = Screen.VideoPreview(handle.sourceKey, handle.path, name)
        previewJob?.cancel()
        _state.update { it.copy(screen = screen, preview = PreviewState(loading = true)) }
        previewJob = viewModelScope.launch {
            try {
                val file = thumbnails.previewFile(source, handle, FileKind.Video)
                if (_state.value.screen == screen) _state.update { it.copy(preview = PreviewState(file = file)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.screen == screen) _state.update { it.copy(preview = PreviewState(error = readable(error))) }
            }
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
        thumbnailBatchJob?.cancel()
        thumbnailBatchJob = null
        pendingThumbnails.clear()
    }

    override fun onCleared() {
        browserJob?.cancel()
        previewJob?.cancel()
        cancelThumbnailRequests()
        val oldSources = sources.values.filterIsInstance<SmbFileSource>()
        Dispatchers.IO.dispatch(EmptyCoroutineContext) { oldSources.forEach(SmbFileSource::close) }
        super.onCleared()
    }

    companion object {
        fun thumbKey(item: FileItem): String = item.cacheKey()
    }

}
