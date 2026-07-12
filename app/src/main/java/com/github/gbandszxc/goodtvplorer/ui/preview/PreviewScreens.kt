package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.components.tvOkClick
import com.github.gbandszxc.goodtvplorer.domain.FileSourceDataSource
import com.github.gbandszxc.goodtvplorer.domain.StreamingMedia
import com.github.gbandszxc.goodtvplorer.domain.TimedTextCue
import com.github.gbandszxc.goodtvplorer.viewmodel.MainViewModel
import com.github.gbandszxc.goodtvplorer.viewmodel.PreviewState
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun ImageViewer(
    name: String,
    selectedPath: String,
    state: PreviewState,
    images: List<FileItem>,
    thumbnails: Map<String, java.io.File>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (FileItem) -> Unit,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    var loaded by remember(state.image) { mutableStateOf(false) }
    var loadError by remember(state.image) { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    val viewerFocusRequester = remember { FocusRequester() }
    val selectedFilmFocusRequester = remember(selectedPath) { FocusRequester() }
    val filmstripState = rememberLazyListState()
    val selectedFilmIndex = images.indexOfFirst { it.handle.path == selectedPath }
    LaunchedEffect(Unit) { viewerFocusRequester.requestFocus() }
    LaunchedEffect(controlsVisible, selectedFilmIndex) {
        if (controlsVisible && selectedFilmIndex >= 0) filmstripState.scrollToItem(selectedFilmIndex)
    }
    BackHandler(enabled = controlsVisible) { controlsVisible = false }
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(viewerFocusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                        controlsVisible = true
                        true
                    }
                    !controlsVisible && event.key == Key.DirectionLeft -> {
                        onPrevious()
                        true
                    }
                    !controlsVisible && event.key == Key.DirectionRight -> {
                        onNext()
                        true
                    }
                    controlsVisible && event.key == Key.DirectionDown -> {
                        selectedFilmFocusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            },
    ) {
        when {
            state.loading -> {
                state.placeholder?.let {
                    AsyncImage(model = it, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.error != null -> {
                state.placeholder?.let {
                    AsyncImage(model = it, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
            }
            state.image != null -> {
                if (!loaded && state.placeholder != null) {
                    AsyncImage(model = state.placeholder, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (!loaded && state.placeholder == null && loadError == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                AsyncImage(
                    model = state.image,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().alpha(if (loaded) 1f else 0f),
                    contentScale = ContentScale.Fit,
                    onSuccess = {
                        loaded = true
                        loadError = null
                    },
                    onError = { loadError = "图片加载失败，请检查 NAS 连接后重试。" },
                )
                loadError?.let {
                    Text(it, color = Color(0xFFFCA5A5), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        if (controlsVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xE6101A26)).padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TvButton("返回", contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp), fontSize = 20.sp, onClick = onBack)
                }
                Spacer(Modifier.weight(1f))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(132.dp).background(Color(0xE6101A26)).padding(horizontal = 28.dp, vertical = 12.dp),
                    state = filmstripState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(images, key = { it.handle.sourceKey + it.handle.path }) { item ->
                        ImageFilmstripItem(
                            item = item,
                            thumbnail = thumbnails[MainViewModel.thumbKey(item)],
                            selected = item.handle.path == selectedPath,
                            focusRequester = if (item.handle.path == selectedPath) selectedFilmFocusRequester else null,
                            onSelect = { onSelect(item) },
                            onThumbnailVisible = onThumbnailVisible,
                            onThumbnailHidden = onThumbnailHidden,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageFilmstripItem(
    item: FileItem,
    thumbnail: java.io.File?,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(item, thumbnail) {
        val requested = thumbnail == null
        if (requested) onThumbnailVisible(item)
        onDispose { if (requested) onThumbnailHidden(item) }
    }
    LaunchedEffect(focusRequester) { focusRequester?.requestFocus() }
    Box(
        Modifier.width(144.dp).fillMaxSize().clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFFFFC857) else Color(0xFF152232))
            .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color(0xFF26384B)), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable().tvOkClick(onSelect),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            AsyncImage(model = thumbnail, contentDescription = item.name, modifier = Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Crop)
        } else {
            Text("IMG", color = if (focused) Color(0xFF151007) else Color(0xFFC9D8E5), fontSize = 24.sp)
        }
        if (selected) Text("当前", color = Color(0xFFF3F7FA), fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xCC101A26)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun TextPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    var page by remember(state.text) { mutableStateOf(0) }
    val pages = remember(state.text) { textPages(state.text) }
    val pageFocus = remember { FocusRequester() }
    LaunchedEffect(state.loading) { if (!state.loading) pageFocus.requestFocus() }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(horizontal = 36.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("自动换行", color = Color(0xFF7CC7D8), fontSize = 18.sp)
            TvButton("返回", onClick = onBack)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp) }
            else -> Column(
                Modifier.padding(top = 18.dp).fillMaxSize().focusRequester(pageFocus).focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.PageUp, Key.DirectionLeft -> { page = (page - 1).coerceAtLeast(0); true }
                            Key.PageDown, Key.DirectionRight -> { page = (page + 1).coerceAtMost(pages.lastIndex); true }
                            else -> false
                        }
                    },
            ) {
                if (state.truncated) Text("仅显示前 1MB", color = Color(0xFFFFC857), fontSize = 18.sp)
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                    pages[page].forEach { line ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${line.number}", modifier = Modifier.width(72.dp), color = Color(0xFF728397), fontSize = 20.sp, lineHeight = 30.sp)
                            Text(line.text.ifEmpty { " " }, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 20.sp, lineHeight = 30.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text("${page + 1} / ${pages.size}", color = Color(0xFFA8B8C7), fontSize = 18.sp, modifier = Modifier.padding(end = 18.dp))
                    TvButton("上一页", enabled = page > 0, onClick = { page-- })
                    Spacer(Modifier.width(10.dp))
                    TvButton("下一页", enabled = page < pages.lastIndex, onClick = { page++ })
                }
            }
        }
    }
}

internal data class NumberedLine(val number: Int, val text: String)

internal fun textPages(text: String, linesPerPage: Int = 14): List<List<NumberedLine>> =
    text.split('\n').mapIndexed { index, line -> NumberedLine(index + 1, line.trimEnd('\r')) }
        .chunked(linesPerPage).ifEmpty { listOf(listOf(NumberedLine(1, ""))) }

@Composable
fun AudioPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(horizontal = 36.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(name, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TvButton("返回", onClick = onBack)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp) }
            state.media != null -> AudioPlayer(state.media, state.timedText, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AudioPlayer(media: StreamingMedia, lyrics: List<TimedTextCue>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(media) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(FileSourceDataSource.Factory(media)))
            .build().apply { setMediaItem(MediaItem.fromUri(media.uri)); prepare() }
    }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var artwork by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val controlFocus = remember { FocusRequester() }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player) {
        controlFocus.requestFocus()
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            playing = player.isPlaying
            artwork = player.mediaMetadata.artworkData ?: artwork
            error = player.playerError?.message
            delay(250)
        }
    }
    val lyric = lyrics.lastOrNull { position >= it.startMs }?.takeIf { position < it.endMs }
    Column(modifier.fillMaxWidth()) {
    Row(Modifier.padding(top = 18.dp).weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.weight(0.38f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFF101A26)), contentAlignment = Alignment.Center) {
            if (artwork != null) AsyncImage(model = artwork, contentDescription = "音频封面", modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
            else Text("AUD", color = Color(0xFF7CC7D8), fontSize = 40.sp)
        }
        Box(Modifier.weight(0.62f).fillMaxSize().background(Color(0xFF101A26)).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(lyric?.text ?: if (lyrics.isEmpty()) "未找到同名 LRC 歌词" else "♪", color = if (lyric == null) Color(0xFFA8B8C7) else Color(0xFFF3F7FA), fontSize = 28.sp, lineHeight = 40.sp)
        }
    }
    error?.let { Text(it, color = Color(0xFFFFA3A3), fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
    MediaControls(player, playing, position, duration, modifier = Modifier.focusRequester(controlFocus))
    }
}

@Composable
private fun MediaControls(player: ExoPlayer, playing: Boolean, position: Long, duration: Long, modifier: Modifier = Modifier, speed: Float? = null, onSpeed: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TvButton(if (playing) "暂停" else "播放", onClick = { if (player.isPlaying) player.pause() else player.play() })
        TvButton("−10秒", onClick = { player.seekTo((position - 10_000).coerceAtLeast(0)) })
        SeekProgress(position, duration, { player.seekTo(it) }, Modifier.weight(1f))
        TvButton("+10秒", onClick = { player.seekTo((position + 10_000).coerceAtMost(duration)) })
        if (speed != null && onSpeed != null) TvButton("${speed}×", onClick = onSpeed)
        Text("${format(position)} / ${format(duration)}", color = Color(0xFFA8B8C7), fontSize = 18.sp)
    }
}

@Composable
private fun SeekProgress(position: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(8.dp)).background(if (focused) Color(0xFF152232) else Color(0xFF101A26))
            .border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color(0xFF26384B), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().semantics { contentDescription = "播放进度，左右键快进或后退" }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                    Key.DirectionLeft -> { onSeek((position - 10_000).coerceAtLeast(0)); true }
                    Key.DirectionRight -> { onSeek((position + 10_000).coerceAtMost(duration)); true }
                    else -> false
                }
            }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        LinearProgressIndicator(progress = { if (duration <= 0) 0f else position.toFloat() / duration }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFFFC857), trackColor = Color(0xFF26384B))
    }
}

@Composable
fun VideoPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080D))) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null -> Text(state.error, color = Color(0xFFFFA3A3), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
            state.file != null -> AsyncImage(model = state.file, contentDescription = name, modifier = Modifier.fillMaxSize().padding(48.dp), contentScale = ContentScale.Fit)
            else -> Text("没有可用的视频缩略图", color = Color(0xFFC8D5E2), fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.align(Alignment.TopStart).padding(28.dp)) {
            Text(name, color = Color.White, fontSize = 26.sp, maxLines = 1)
            Text("视频预览 · 播放器后续接入", color = Color(0xFFFFC857), fontSize = 18.sp)
        }
        TvButton("返回", modifier = Modifier.align(Alignment.TopEnd).padding(24.dp), onClick = onBack)
    }
}

private fun format(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%02d:%02d".format(total / 60, total % 60)
}
