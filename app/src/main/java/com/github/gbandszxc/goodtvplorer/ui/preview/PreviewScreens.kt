package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.github.gbandszxc.goodtvplorer.R
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvSeekBar
import com.github.gbandszxc.goodtvplorer.ui.components.tvOkClick
import com.github.gbandszxc.goodtvplorer.domain.FileSourceDataSource
import com.github.gbandszxc.goodtvplorer.domain.StreamingMedia
import com.github.gbandszxc.goodtvplorer.domain.TimedTextCue
import com.github.gbandszxc.goodtvplorer.viewmodel.MainViewModel
import com.github.gbandszxc.goodtvplorer.viewmodel.PreviewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit

internal fun swipeStep(distance: Float, threshold: Float): Int = when {
    distance > threshold -> -1
    distance < -threshold -> 1
    else -> 0
}

internal fun Modifier.horizontalSwipe(onPrevious: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(Unit) {
    var distance = 0f
    val threshold = 72.dp.toPx()
    detectHorizontalDragGestures(
        onDragStart = { distance = 0f },
        onDragCancel = { distance = 0f },
        onDragEnd = {
            when (swipeStep(distance, threshold)) {
                -1 -> onPrevious()
                1 -> onNext()
            }
            distance = 0f
        },
    ) { change, dragAmount ->
        change.consume()
        distance += dragAmount
    }
}

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
    val context = LocalContext.current
    val imageRequest = remember(state.image) {
        state.image?.let {
            ImageRequest.Builder(context)
                .data(it)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .allowHardware(false)
                .build()
        }
    }
    val viewerFocusRequester = remember { FocusRequester() }
    val selectedFilmFocusRequester = remember(selectedPath) { FocusRequester() }
    val filmstripState = rememberLazyListState()
    val selectedFilmIndex = images.indexOfFirst { it.handle.path == selectedPath }
    // 关闭控制层后焦点确定回全屏画面；进入时首次组合（controlsVisible=false）同样生效
    LaunchedEffect(controlsVisible) { if (!controlsVisible) viewerFocusRequester.requestFocus() }
    LaunchedEffect(controlsVisible, selectedFilmIndex) {
        if (controlsVisible && selectedFilmIndex >= 0) filmstripState.scrollToItem(selectedFilmIndex)
    }
    LaunchedEffect(controlsVisible, filmstripState, images) {
        if (!controlsVisible) return@LaunchedEffect
        val heldItems = mutableMapOf<String, FileItem>()
        try {
            snapshotFlow { filmstripState.layoutInfo.visibleItemsInfo.map { it.index }.toSet() }
                .distinctUntilChanged()
                .collect { visibleIndices ->
                    val visibleItems = visibleIndices.mapNotNull(images::getOrNull)
                    val visibleKeys = visibleItems.mapTo(mutableSetOf()) { MainViewModel.thumbKey(it) }
                    (heldItems.keys - visibleKeys).forEach { key ->
                        heldItems.remove(key)?.let(onThumbnailHidden)
                    }
                    visibleItems.forEach { item ->
                        val key = MainViewModel.thumbKey(item)
                        heldItems.getOrPut(key) {
                            onThumbnailVisible(item)
                            item
                        }
                    }
                }
        } finally {
            heldItems.values.forEach(onThumbnailHidden)
        }
    }
    BackHandler(enabled = controlsVisible) { controlsVisible = false }
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(viewerFocusRequester).focusable()
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                when {
                    // 收起时 OK 在 KeyUp 消费并呼出：呼出引发的移焦发生在下一帧重组，本次 KeyUp 不会再落入胶卷项
                    event.type == KeyEventType.KeyUp && isOk && !controlsVisible -> { controlsVisible = true; true }
                    // 展开期间吞掉 KeyDown，KeyUp 交给胶卷项（现状行为不变）
                    event.type == KeyEventType.KeyDown && isOk -> true
                    event.type == KeyEventType.KeyDown && !controlsVisible && event.key == Key.DirectionLeft -> {
                        onPrevious()
                        true
                    }
                    event.type == KeyEventType.KeyDown && !controlsVisible && event.key == Key.DirectionRight -> {
                        onNext()
                        true
                    }
                    // Back 在键序层直接收起控制层：焦点无论在控制层按钮还是画面上都单击确定性关闭，
                    // 不经 OnBackPressedDispatcher（实测其注册时序竞态会导致首次 Back 仅移焦、二次才收起）；
                    // 收起后 BackHandler 已禁用，收起的 Back 因 KeyDown 被消费未建立跟踪，不会连带退出预览
                    event.type == KeyEventType.KeyDown && controlsVisible && event.key == Key.Back -> {
                        controlsVisible = false
                        true
                    }
                    event.type == KeyEventType.KeyDown && controlsVisible && event.key == Key.DirectionDown -> {
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
            imageRequest != null -> {
                if (!loaded && state.placeholder != null) {
                    AsyncImage(model = state.placeholder, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (!loaded && state.placeholder == null && loadError == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                AsyncImage(
                    model = imageRequest,
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
        Box(
            Modifier.matchParentSize()
                .horizontalSwipe(onPrevious, onNext)
                .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } }
                .semantics {
                    contentDescription = "图片预览，点击显示控制栏，左右滑动切换图片"
                    onClick {
                        controlsVisible = !controlsVisible
                        true
                    }
                },
        )
        if (controlsVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xE6101A26)).padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // 显式 Cancel 固定原位，不依赖几何搜索结果；进出胶卷仅经上/下键
                    TvButton(
                        "返回",
                        modifier = Modifier.focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        fontSize = 20.sp,
                        onClick = onBack,
                    )
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
) {
    var focused by remember { mutableStateOf(false) }
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
            Icon(
                painterResource(R.drawable.ic_image),
                contentDescription = "图片",
                tint = if (focused) Color(0xFF151007) else Color(0xFFC9D8E5),
                modifier = Modifier.requiredSize(36.dp),
            )
        }
        if (selected) Text("当前", color = Color(0xFFF3F7FA), fontSize = 16.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xCC101A26)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun TextPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    var page by remember(state.text) { mutableIntStateOf(0) }
    val pages = remember(state.text) { textPages(state.text) }
    val pageFocus = remember { FocusRequester() }
    val textBackFocus = remember { FocusRequester() }
    val prevPageFocus = remember { FocusRequester() }
    val nextPageFocus = remember { FocusRequester() }
    // 正文容器自身是否持焦；按钮持焦时翻页键让位给焦点移动
    var bodyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.error) {
        if (state.loading) return@LaunchedEffect
        // 错误态正文不组合，显式聚焦返回按钮，避免平台根级救援的弱可发现性
        if (state.error != null) textBackFocus.requestFocus() else pageFocus.requestFocus()
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(horizontal = 36.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("自动换行", color = Color(0xFF7CC7D8), fontSize = 18.sp)
            TvButton("返回", modifier = Modifier.focusRequester(textBackFocus), onClick = onBack)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp) }
            else -> Column(
                Modifier.padding(top = 18.dp).fillMaxSize().focusRequester(pageFocus)
                    .onFocusChanged { bodyFocused = it.isFocused }
                    .focusProperties { up = textBackFocus; down = prevPageFocus }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // 按钮持焦时左右键让位给焦点移动，正文持焦时翻页语义不变
                        if (!bodyFocused) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.PageUp, Key.DirectionLeft -> { page = (page - 1).coerceAtLeast(0); true }
                            Key.PageDown, Key.DirectionRight -> { page = (page + 1).coerceAtMost(pages.lastIndex); true }
                            else -> false
                        }
                    }
                    .horizontalSwipe(
                        onPrevious = { page = (page - 1).coerceAtLeast(0) },
                        onNext = { page = (page + 1).coerceAtMost(pages.lastIndex) },
                    ),
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
                    TvButton(
                        "上一页",
                        enabled = page > 0,
                        modifier = Modifier.focusRequester(prevPageFocus).focusProperties {
                            up = textBackFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = nextPageFocus
                        },
                        onClick = { page-- },
                    )
                    Spacer(Modifier.width(10.dp))
                    TvButton(
                        "下一页",
                        enabled = page < pages.lastIndex,
                        modifier = Modifier.focusRequester(nextPageFocus).focusProperties {
                            up = textBackFocus
                            down = FocusRequester.Cancel
                            left = prevPageFocus
                            right = FocusRequester.Cancel
                        },
                        onClick = { page++ },
                    )
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
fun VideoPreview(
    name: String,
    state: PreviewState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) = when {
    state.loading -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    state.error != null -> VideoErrorPanel(message = state.error, onBack = onBack, onRetry = onRetry)
    state.media != null -> VideoPlayer(name, state.media, state.timedText, onPrevious, onNext, onBack)
    else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Text("没有可用的视频", color = Color(0xFFA8B8C7), fontSize = 26.sp) }
}

/** 视频预览失败面板：显式聚焦「重试」，与「返回」并列，确认键语义一目了然。 */
@Composable
private fun VideoErrorPanel(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(message, color = Color(0xFFFFA3A3), fontSize = 26.sp, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("重试", modifier = Modifier.focusRequester(retryFocus), onClick = onRetry)
                TvButton("返回", onClick = onBack)
            }
        }
    }
}

@OptIn(UnstableApi::class)
internal enum class VideoScaleMode(
    val label: String,
    val resizeMode: Int,
    val targetRatio: Float? = null,
) {
    Fit("适应屏幕", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("铺满屏幕", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Stretch("拉伸全屏", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Original("原始比例", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Ratio16x9("16:9", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 16f / 9f),
    Ratio4x3("4:3", AspectRatioFrameLayout.RESIZE_MODE_ZOOM, 4f / 3f),
    ;

    fun next(): VideoScaleMode = entries[(ordinal + 1) % entries.size]
}

internal fun effectiveVideoScaleMode(mode: VideoScaleMode, size: VideoSize): VideoScaleMode =
    if (mode == VideoScaleMode.Original && (size.width <= 0 || size.height <= 0)) VideoScaleMode.Fit else mode

@Composable
private fun VideoPlayer(
    name: String,
    media: StreamingMedia,
    subtitles: List<TimedTextCue>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val player = remember(media) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(FileSourceDataSource.Factory(media)))
            .build().apply { setMediaItem(MediaItem.fromUri(media.uri)); prepare() }
    }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var error by remember { mutableStateOf<String?>(null) }
    var embeddedSubtitle by remember { mutableStateOf("") }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var controlsVisible by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.Fit) }
    val playerFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val rewind10Focus = remember { FocusRequester() }
    val forward10Focus = remember { FocusRequester() }
    val speedFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    // 进度条 ↑ 回到的记忆按钮（默认播放/暂停）；各按钮 onFocusChanged 时更新
    val lastControlsButton = remember { mutableStateOf(playFocus) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                embeddedSubtitle = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n")
            }

            override fun onVideoSizeChanged(newVideoSize: VideoSize) {
                videoSize = newVideoSize
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player, controlsVisible) {
        // 呼出时初始焦点显式落在播放/暂停，不依赖 FocusRequester 对后代 BFS 的隐式取值
        if (controlsVisible) playFocus.requestFocus() else playerFocus.requestFocus()
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            playing = player.isPlaying
            error = player.playerError?.let(::playbackErrorMessage)
            delay(250)
        }
    }
    val subtitle = subtitles.lastOrNull { position >= it.startMs }?.takeIf { position < it.endMs }?.text ?: embeddedSubtitle.takeIf(String::isNotBlank)
    val effectiveScaleMode = effectiveVideoScaleMode(scaleMode, videoSize)
    BackHandler(enabled = controlsVisible) { controlsVisible = false }
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(playerFocus).focusable()
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                when {
                    // 收起时 OK 在 KeyUp 消费并呼出：KeyUp 时本 handler 即焦点属主，呼出引发的移焦发生在下一帧重组，
                    // 本次 KeyUp 不会再落入任何按钮，从根上排除「呼出附带触发一次播放/暂停」的可能
                    event.type == KeyEventType.KeyUp && isOk && !controlsVisible -> { controlsVisible = true; true }
                    // 展开期间吞掉 KeyDown，KeyUp 交给焦点按钮（现状行为不变）
                    event.type == KeyEventType.KeyDown && isOk -> true
                    event.type == KeyEventType.KeyDown && !controlsVisible && event.key == Key.DirectionLeft -> {
                        onPrevious()
                        true
                    }
                    event.type == KeyEventType.KeyDown && !controlsVisible && event.key == Key.DirectionRight -> {
                        onNext()
                        true
                    }
                    // Back 在键序层直接收起控制层（与图片页同构）：不依赖 OnBackPressedDispatcher 的注册时序，
                    // 焦点在控制层任意按钮或画面上都单击确定性关闭；收起后 BackHandler 已禁用且 KeyDown
                    // 被消费未建立跟踪，本次 Back 不会连带返回文件列表
                    event.type == KeyEventType.KeyDown && controlsVisible && event.key == Key.Back -> {
                        controlsVisible = false
                        true
                    }
                    else -> false
                }
            },
    ) {
        VideoSurface(player, effectiveScaleMode, videoSize, density.density)
        Box(
            Modifier.matchParentSize()
                .horizontalSwipe(onPrevious, onNext)
                .pointerInput(Unit) { detectTapGestures { controlsVisible = !controlsVisible } }
                .semantics {
                    contentDescription = "视频预览，点击显示控制栏，左右滑动切换视频"
                    onClick {
                        controlsVisible = !controlsVisible
                        true
                    }
                },
        )
        subtitle?.let {
            Text(
                it,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = if (controlsVisible) 208.dp else 28.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            )
        }
        if (subtitles.isEmpty() && embeddedSubtitle.isBlank()) Text("未检测到字幕", color = Color(0xFFA8B8C7), fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp))
        error?.let { Text(it, color = Color(0xFFFFA3A3), fontSize = 18.sp, modifier = Modifier.align(Alignment.Center).background(Color(0xCC101A26)).padding(16.dp)) }
        if (controlsVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xE6101A26)).padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TvButton(
                        "返回",
                        modifier = Modifier.focusRequester(backFocus).focusProperties {
                            down = playFocus
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                        onClick = onBack,
                    )
                }
                Spacer(Modifier.weight(1f))
                MediaControls(
                    player = player,
                    playing = playing,
                    position = position,
                    duration = duration,
                    modifier = Modifier.fillMaxWidth().background(Color(0xE6101A26)).padding(horizontal = 28.dp, vertical = 18.dp),
                    speed = speed,
                    onSpeed = {
                        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                        player.setPlaybackSpeed(speed)
                    },
                    scaleMode = effectiveScaleMode,
                    onScaleMode = { scaleMode = scaleMode.next() },
                    playFocus = playFocus,
                    rewind10Focus = rewind10Focus,
                    forward10Focus = forward10Focus,
                    speedFocus = speedFocus,
                    modeFocus = modeFocus,
                    seekFocus = seekFocus,
                    backFocus = backFocus,
                    lastControlsButton = lastControlsButton,
                )
            }
        }
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun VideoSurface(player: ExoPlayer, mode: VideoScaleMode, videoSize: VideoSize, density: Float) {
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val modifier = when {
            mode == VideoScaleMode.Original -> Modifier.requiredSize((videoSize.width / density).dp, (videoSize.height / density).dp)
            mode.targetRatio != null -> Modifier.fillMaxSize().aspectRatio(mode.targetRatio, matchHeightConstraintsFirst = true)
            else -> Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { PlayerView(it).apply { useController = false } },
            update = { view ->
                view.player = player
                view.resizeMode = mode.resizeMode
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun MediaControls(
    player: ExoPlayer,
    playing: Boolean,
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    speed: Float? = null,
    onSpeed: (() -> Unit)? = null,
    scaleMode: VideoScaleMode? = null,
    onScaleMode: (() -> Unit)? = null,
    playFocus: FocusRequester,
    rewind10Focus: FocusRequester,
    forward10Focus: FocusRequester,
    speedFocus: FocusRequester,
    modeFocus: FocusRequester,
    seekFocus: FocusRequester,
    backFocus: FocusRequester,
    lastControlsButton: MutableState<FocusRequester>,
) {
    // ±10 秒动作的唯一实现：按钮确认键与进度条左右键共用，保证两条入口步进口径一致
    val rewind10 = { player.seekTo((position - 10_000).coerceAtLeast(0)) }
    val forward10 = { player.seekTo((position + 10_000).coerceAtMost(duration)) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TvButton(
                if (playing) "暂停" else "播放",
                modifier = Modifier.focusRequester(playFocus).focusProperties {
                    left = FocusRequester.Cancel
                    right = rewind10Focus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = playFocus },
                onClick = { if (player.isPlaying) player.pause() else player.play() },
            )
            TvButton(
                "−10秒",
                modifier = Modifier.focusRequester(rewind10Focus).focusProperties {
                    left = playFocus
                    right = forward10Focus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = rewind10Focus },
                onClick = rewind10,
            )
            TvButton(
                "+10秒",
                modifier = Modifier.focusRequester(forward10Focus).focusProperties {
                    left = rewind10Focus
                    right = speedFocus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = forward10Focus },
                onClick = forward10,
            )
            if (speed != null && onSpeed != null) {
                TvButton(
                    "${speed}×",
                    modifier = Modifier.focusRequester(speedFocus).focusProperties {
                        left = forward10Focus
                        right = modeFocus
                        up = backFocus
                        down = seekFocus
                    }.onFocusChanged { if (it.isFocused) lastControlsButton.value = speedFocus },
                    onClick = onSpeed,
                )
            }
            if (scaleMode != null && onScaleMode != null) {
                TvButton(
                    scaleMode.label,
                    modifier = Modifier.focusRequester(modeFocus).focusProperties {
                        left = speedFocus
                        right = FocusRequester.Cancel
                        up = backFocus
                        down = seekFocus
                    }.onFocusChanged { if (it.isFocused) lastControlsButton.value = modeFocus },
                    onClick = onScaleMode,
                )
            }
        }
        // 进度行：左右键在本层 onPreviewKeyEvent（preview 阶段先于 TvSeekBar 内部消费）接管为
        // 「±10 秒步进 + 焦点移入方向对应的 seek 按钮」。进度条行内无其他可聚焦控件，若由
        // TvSeekBar 原地消费左右键，焦点将永远无法离开进度条（实测死角：任意次左/右焦点纹丝不动，
        // 上下行几何也不重叠，系统焦点搜索同样无候选）；步进仍随按键生效，落点按钮即同一动作的
        // 按钮化身，后续可用确认键继续逐次步进。左右键 KeyUp 会落入新按钮，但 TvButton 仅在
        // 确认键 KeyUp 触发（TvButton.kt onTvOk / clickable），不会被方向键误触。
        // ↑ 手动拦截回最近聚焦的按钮（默认播放/暂停），↓ 原地；时长未知时不消费左右键（不移出）。
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvSeekBar(
                positionMs = position,
                durationMs = duration,
                onSeek = { player.seekTo(it) },
                modifier = Modifier.weight(1f).focusRequester(seekFocus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                            Key.DirectionUp -> { lastControlsButton.value.requestFocus(); true }
                            Key.DirectionDown -> true
                            Key.DirectionLeft -> if (duration > 0) {
                                rewind10()
                                rewind10Focus.requestFocus()
                                true
                            } else {
                                false
                            }
                            Key.DirectionRight -> if (duration > 0) {
                                forward10()
                                forward10Focus.requestFocus()
                                true
                            } else {
                                false
                            }
                            else -> false
                        }
                    },
            )
            Spacer(Modifier.width(16.dp))
            Text("${format(position)} / ${format(duration)}", color = Color(0xFFA8B8C7), fontSize = 18.sp)
        }
    }
}


private fun format(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun playbackErrorMessage(error: androidx.media3.common.PlaybackException): String = when {
    error.message?.contains("MediaCodecVideoRenderer") == true -> "当前设备不支持此视频编码"
    error.message?.contains("MediaCodecAudioRenderer") == true -> "当前设备不支持此音频编码"
    else -> "媒体加载失败，请检查文件或网络连接"
}
