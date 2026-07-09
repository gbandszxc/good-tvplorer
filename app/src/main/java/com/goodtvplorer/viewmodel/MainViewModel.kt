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
import com.goodtvplorer.domain.AudioCacheManager
import com.goodtvplorer.domain.ThumbnailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.connections.collectLatest { connections ->
                sources.keys.filter { it.startsWith("smb:") }.forEach { sources.remove(it) }
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
        _state.update { it.copy(screen = Screen.Browser(sourceKey, path), browser = BrowserState(loading = true)) }
        viewModelScope.launch {
            runCatching { source.list(path) }
                .onSuccess { items ->
                    _state.update { it.copy(browser = BrowserState(items = items)) }
                    cacheThumbnails(source, items)
                }
                .onFailure { e -> _state.update { it.copy(browser = BrowserState(error = readable(e))) } }
        }
    }

    fun openItem(item: FileItem) {
        when (item.kind) {
            FileKind.Directory -> openBrowser(item.handle.sourceKey, item.handle.path)
            FileKind.Image -> prepareImage(item.handle, item.name)
            FileKind.Text -> prepareText(item.handle, item.name)
            FileKind.Audio -> prepareAudio(item.handle, item.name)
            FileKind.Video -> prepareVideo(item.handle, item.name)
            FileKind.Other -> showError("暂不支持打开此文件类型")
        }
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

    private fun cacheThumbnails(source: FileSource, items: List<FileItem>) {
        viewModelScope.launch {
            items.filter { it.kind == FileKind.Image }.take(60).forEach { item ->
                runCatching { thumbnails.thumbnailFile(source, item.handle) }
                    .onSuccess { file ->
                        _state.update { state -> state.copy(thumbnails = state.thumbnails + (thumbKey(item.handle) to file)) }
                    }
            }
        }
    }

    private fun prepareImage(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        _state.update { it.copy(screen = Screen.ImagePreview(handle.sourceKey, handle.path, name), preview = PreviewState(loading = true)) }
        viewModelScope.launch {
            runCatching { thumbnails.thumbnailFile(source, handle) }
                .onSuccess { file -> _state.update { it.copy(preview = PreviewState(file = file)) } }
            runCatching { thumbnails.imageFile(source, handle) }
                .onSuccess { file -> _state.update { it.copy(preview = PreviewState(file = file)) } }
                .onFailure { e -> _state.update { it.copy(preview = PreviewState(error = readable(e))) } }
        }
    }

    private fun prepareText(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        _state.update { it.copy(screen = Screen.TextPreview(handle.sourceKey, handle.path, name), preview = PreviewState(loading = true)) }
        viewModelScope.launch {
            runCatching {
                val bytes = source.readPrefix(handle.path, 1024 * 1024 + 1)
                PreviewState(text = bytes.take(1024 * 1024).toByteArray().toString(Charsets.UTF_8), truncated = bytes.size > 1024 * 1024)
            }
                .onSuccess { preview -> _state.update { it.copy(preview = preview) } }
                .onFailure { e -> _state.update { it.copy(preview = PreviewState(error = readable(e))) } }
        }
    }

    private fun prepareAudio(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        _state.update { it.copy(screen = Screen.AudioPreview(handle.sourceKey, handle.path, name), preview = PreviewState(loading = true)) }
        viewModelScope.launch {
            runCatching { audioCache.cachedFile(source, handle) }
                .onSuccess { file -> _state.update { it.copy(preview = PreviewState(file = file)) } }
                .onFailure { e -> _state.update { it.copy(preview = PreviewState(error = readable(e))) } }
        }
    }

    private fun prepareVideo(handle: FileHandle, name: String) {
        val source = sources[handle.sourceKey] ?: return showError("文件源不存在")
        _state.update { it.copy(screen = Screen.VideoPreview(handle.sourceKey, handle.path, name), preview = PreviewState(loading = true)) }
        viewModelScope.launch {
            runCatching { thumbnails.previewFile(source, handle, FileKind.Video) }
                .onSuccess { file -> _state.update { it.copy(preview = PreviewState(file = file)) } }
                .onFailure { e -> _state.update { it.copy(preview = PreviewState(error = readable(e))) } }
        }
    }

    private fun showError(message: String) {
        _state.update { it.copy(browser = it.browser.copy(error = message)) }
    }

    private fun readable(e: Throwable): String = e.message ?: "操作失败"

    companion object {
        fun thumbKey(handle: FileHandle): String = handle.sourceKey + "|" + handle.path
    }
}
