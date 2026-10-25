package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.domain.FileSourceDataSource
import com.github.gbandszxc.goodtvplorer.domain.StreamingMedia
import com.github.gbandszxc.goodtvplorer.domain.TimedTextCue
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvChip
import com.github.gbandszxc.goodtvplorer.ui.components.TvSeekBar
import com.github.gbandszxc.goodtvplorer.ui.components.TvSpinner
import com.github.gbandszxc.goodtvplorer.ui.components.tvClickable
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvChipShape
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvPanelShape
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import com.github.gbandszxc.goodtvplorer.viewmodel.MainViewModel
import com.github.gbandszxc.goodtvplorer.viewmodel.PreviewState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit

/** 预览控制层淡入淡出时长；关闭系统动画时直接切换。 */
private const val PreviewControlsFadeMs = 200

/** 图片胶卷高度。 */
private val FilmstripHeight = 120.dp

/** 图片胶卷单项宽度。 */
private val FilmstripItemWidth = 128.dp

/** 图片错误文案沿用电视端大字号。 */
private val PreviewErrorFontSize = 26.sp

/** 预览加载弧线尺寸：电视观看距离下的可辨识下限。 */
private val PreviewSpinnerSize = 36.dp

/** 胶卷无缩略图时的占位图标尺寸。 */
private val FilmstripFallbackIconSize = 40.dp

/** 自适应分页测量完成前的首帧行数，与历史固定分页一致，避免首帧空页。 */
private const val DefaultTextLinesPerPage = 14

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

/**
 * 控制层显隐：200ms 展开曲线 fade；reduced motion 时零时长直接显隐。
 * 只做透明度过渡，不移动控制层位置，避免呼出/收起时焦点目标跳动。
 */
@Composable
private fun PreviewControlsVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    val durationMs = if (motionEnabled) PreviewControlsFadeMs else 0
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = durationMs, easing = TvMotion.expandEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = durationMs, easing = TvMotion.expandEasing)),
    ) {
        content()
    }
}

/** 预览页统一错误块：AlertTriangle + 说明，承载在半透明凹陷面板内。 */
@Composable
private fun PreviewErrorPanel(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(TvPanelShape)
            .background(TvColors.SurfaceSunken.copy(alpha = 0.9f))
            .border(1.dp, TvColors.OutlineVariant, TvPanelShape)
            .padding(horizontal = TvSpacing.xxl, vertical = TvSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvSpacing.md),
    ) {
        Icon(
            imageVector = TvIcons.AlertTriangle,
            contentDescription = null,
            tint = TvColors.Error,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = message,
            color = TvColors.Error,
            style = TvType.body.copy(fontSize = PreviewErrorFontSize),
            textAlign = TextAlign.Center,
        )
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
                TvSpinner(modifier = Modifier.align(Alignment.Center), size = PreviewSpinnerSize)
            }
            state.error != null -> {
                state.placeholder?.let {
                    AsyncImage(model = it, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                PreviewErrorPanel(state.error, Modifier.align(Alignment.Center))
            }
            imageRequest != null -> {
                if (!loaded && state.placeholder != null) {
                    AsyncImage(model = state.placeholder, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (!loaded && state.placeholder == null && loadError == null) {
                    TvSpinner(modifier = Modifier.align(Alignment.Center), size = PreviewSpinnerSize)
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
                    PreviewErrorPanel(it, Modifier.align(Alignment.Center))
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
        PreviewControlsVisibility(visible = controlsVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(TvSizes.toolbar)
                        .background(TvColors.Panel.copy(alpha = 0.92f))
                        .padding(horizontal = TvSpacing.xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 显式 Cancel 固定原位，不依赖几何搜索结果；进出胶卷仅经上/下键
                    TvActionButton(
                        icon = TvIcons.ArrowLeft,
                        label = "返回",
                        onClick = onBack,
                        contentDescription = "返回",
                        modifier = Modifier.focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                        },
                    )
                    Spacer(Modifier.width(TvSpacing.lg))
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        color = TvColors.TextPrimary,
                        style = TvType.sectionTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(TvSpacing.md))
                    TvChip("${selectedFilmIndex + 1} / ${images.size}")
                }
                Spacer(Modifier.weight(1f))
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(FilmstripHeight)
                        .background(TvColors.Panel.copy(alpha = 0.92f))
                        .padding(horizontal = TvSpacing.xxl, vertical = TvSpacing.sm),
                    state = filmstripState,
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
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
    val shape = RoundedCornerShape(TvRadius.md)
    // 聚焦优先于选中：琥珀填充 + FocusRing 描边；未聚焦的选中项用 Accent 描边表达状态。
    val borderWidth = if (focused || selected) 2.dp else 1.dp
    val borderColor = when {
        focused -> TvColors.FocusRing
        selected -> TvColors.Accent
        else -> TvColors.OutlineVariant
    }
    Box(
        Modifier.width(FilmstripItemWidth).fillMaxHeight().clip(shape)
            .background(if (focused) TvColors.Focus else TvColors.Surface)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .tvClickable(onClick = onSelect)
            .semantics { contentDescription = item.name },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(TvSpacing.xs),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = TvIcons.Image,
                contentDescription = null,
                tint = if (focused) TvColors.OnFocus else TvColors.TextSecondary,
                modifier = Modifier.size(FilmstripFallbackIconSize),
            )
        }
        if (selected) {
            TvChip(
                text = "当前",
                modifier = Modifier.align(Alignment.BottomEnd).padding(TvSpacing.xs),
                accent = true,
            )
        }
    }
}

@Composable
fun TextPreview(name: String, state: PreviewState, onBack: () -> Unit) {
    var page by remember(state.text) { mutableIntStateOf(0) }
    var linesPerPage by remember(state.text) { mutableIntStateOf(DefaultTextLinesPerPage) }
    val pages = remember(state.text, linesPerPage) { textPages(state.text, linesPerPage) }
    val currentPage = page.coerceIn(0, pages.lastIndex)
    LaunchedEffect(pages.size) { page = page.coerceIn(0, pages.lastIndex) }
    // horizontalSwipe 内部 key 为 Unit，手势协程会长期持有首次组合的 lambda；
    // 通过 State 读取最新分页结果，避免自适应行数变化后仍用旧页数翻页。
    val latestPage by rememberUpdatedState(currentPage)
    val latestPages by rememberUpdatedState(pages)
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
    Column(Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = TvSpacing.xxl)) {
        Row(
            Modifier.fillMaxWidth().height(TvSizes.toolbar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvActionButton(
                icon = TvIcons.ArrowLeft,
                label = "返回",
                onClick = onBack,
                contentDescription = "返回",
                modifier = Modifier.focusRequester(textBackFocus),
            )
            Spacer(Modifier.width(TvSpacing.lg))
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = TvColors.TextPrimary,
                style = TvType.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(TvSpacing.md))
            AutoWrapHint()
        }
        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                TvSpinner(size = PreviewSpinnerSize)
            }
            state.error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PreviewErrorPanel(state.error)
            }
            else -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = TvSpacing.lg)
                    .focusRequester(pageFocus)
                    .onFocusChanged { bodyFocused = it.isFocused }
                    .focusProperties { up = textBackFocus; down = prevPageFocus }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // 按钮持焦时左右键让位给焦点移动，正文持焦时翻页语义不变
                        if (!bodyFocused) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.PageUp, Key.DirectionLeft -> { page = (currentPage - 1).coerceAtLeast(0); true }
                            Key.PageDown, Key.DirectionRight -> { page = (currentPage + 1).coerceAtMost(pages.lastIndex); true }
                            else -> false
                        }
                    }
                    .horizontalSwipe(
                        onPrevious = { page = (latestPage - 1).coerceAtLeast(0) },
                        onNext = { page = (latestPage + 1).coerceAtMost(latestPages.lastIndex) },
                    ),
            ) {
                if (state.truncated) {
                    Text(
                        text = "仅显示前 1MB",
                        color = TvColors.Accent,
                        style = TvType.meta,
                    )
                    Spacer(Modifier.height(TvSpacing.sm))
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val lineHeightDp = with(LocalDensity.current) { 30.sp.toDp() }
                    val computedLinesPerPage = (maxHeight / lineHeightDp).toInt().coerceIn(6, 30)
                    // 约束只在布局阶段可得，用 SideEffect 回写分页行数，避免在组合中直接改状态。
                    SideEffect {
                        if (computedLinesPerPage != linesPerPage) linesPerPage = computedLinesPerPage
                    }
                    Column(Modifier.fillMaxSize()) {
                        pages[currentPage].forEach { line ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${line.number}",
                                    modifier = Modifier.width(56.dp),
                                    color = TvColors.TextMuted,
                                    style = TvType.body,
                                )
                                Text(
                                    text = line.text.ifEmpty { " " },
                                    modifier = Modifier.weight(1f),
                                    color = TvColors.TextPrimary,
                                    style = TvType.body,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(TvSizes.toolbar),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvChip("${currentPage + 1} / ${pages.size}")
                    Spacer(Modifier.width(TvSpacing.lg))
                    TvActionButton(
                        icon = TvIcons.ChevronLeft,
                        label = "上一页",
                        onClick = { page = (currentPage - 1).coerceAtLeast(0) },
                        enabled = currentPage > 0,
                        compact = true,
                        modifier = Modifier.focusRequester(prevPageFocus).focusProperties {
                            up = textBackFocus
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = nextPageFocus
                        },
                    )
                    Spacer(Modifier.width(TvSpacing.md))
                    TvActionButton(
                        icon = TvIcons.ChevronRight,
                        label = "下一页",
                        onClick = { page = (currentPage + 1).coerceAtMost(pages.lastIndex) },
                        enabled = currentPage < pages.lastIndex,
                        compact = true,
                        modifier = Modifier.focusRequester(nextPageFocus).focusProperties {
                            up = textBackFocus
                            down = FocusRequester.Cancel
                            left = prevPageFocus
                            right = FocusRequester.Cancel
                        },
                    )
                }
            }
        }
    }
}

/** 静态“自动换行”提示：chip 外观 + WrapText 图标，不可聚焦、不参与动作序列。 */
@Composable
private fun AutoWrapHint() {
    Row(
        modifier = Modifier
            .clip(TvChipShape)
            .background(TvColors.SurfaceSunken)
            .border(1.dp, TvColors.OutlineVariant, TvChipShape)
            .padding(horizontal = TvSpacing.sm, vertical = TvSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.xs),
    ) {
        Icon(
            imageVector = TvIcons.WrapText,
            contentDescription = null,
            tint = TvColors.TextSecondary,
            modifier = Modifier.size(TvSizes.icon),
        )
        Text(
            text = "自动换行",
            color = TvColors.TextSecondary,
            style = TvType.caption,
            maxLines = 1,
        )
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
    state.loading -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        TvSpinner(size = PreviewSpinnerSize)
    }
    state.error != null -> VideoErrorPanel(message = state.error, onBack = onBack, onRetry = onRetry)
    state.media != null -> VideoPlayer(name, state.media, state.timedText, onPrevious, onNext, onBack)
    else -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.lg),
        ) {
            Icon(
                imageVector = TvIcons.Film,
                contentDescription = null,
                tint = TvColors.TextMuted,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "没有可用的视频",
                color = TvColors.TextSecondary,
                style = TvType.sectionTitle,
            )
        }
    }
}

/** 视频预览失败面板：显式聚焦「重试」，与「返回」并列，确认键语义一目了然。 */
@Composable
private fun VideoErrorPanel(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(TvPanelShape)
                .background(TvColors.Panel)
                .border(1.dp, TvColors.OutlineVariant, TvPanelShape)
                .padding(horizontal = TvSpacing.huge, vertical = TvSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xl),
        ) {
            Icon(
                imageVector = TvIcons.AlertTriangle,
                contentDescription = null,
                tint = TvColors.Error,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = message,
                color = TvColors.Error,
                style = TvType.body,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                TvActionButton(
                    icon = TvIcons.RefreshCw,
                    label = "重试",
                    onClick = onRetry,
                    contentDescription = "重试",
                    modifier = Modifier.focusRequester(retryFocus).focusProperties {
                        left = FocusRequester.Cancel
                        right = backFocus
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
                )
                TvActionButton(
                    icon = TvIcons.ArrowLeft,
                    label = "返回",
                    onClick = onBack,
                    contentDescription = "返回",
                    modifier = Modifier.focusRequester(backFocus).focusProperties {
                        left = retryFocus
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    },
                )
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
    val prevFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val rewind10Focus = remember { FocusRequester() }
    val forward10Focus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
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
    val noSubtitles = subtitles.isEmpty() && embeddedSubtitle.isBlank()
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
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = TvSpacing.xxxl, vertical = if (controlsVisible) 208.dp else TvSpacing.xxl)
                    .background(Color.Black.copy(alpha = 0.7f), TvChipShape)
                    .padding(horizontal = TvSpacing.md, vertical = TvSpacing.xs),
                color = TvColors.TextPrimary,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            )
        }
        error?.let {
            Row(
                modifier = Modifier.align(Alignment.Center)
                    .clip(TvPanelShape)
                    .background(TvColors.Panel.copy(alpha = 0.92f))
                    .border(1.dp, TvColors.OutlineVariant, TvPanelShape)
                    .padding(horizontal = TvSpacing.xl, vertical = TvSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
            ) {
                Icon(
                    imageVector = TvIcons.AlertTriangle,
                    contentDescription = null,
                    tint = TvColors.Error,
                    modifier = Modifier.size(TvSizes.icon),
                )
                Text(it, color = TvColors.Error, style = TvType.body)
            }
        }
        PreviewControlsVisibility(visible = controlsVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(TvSizes.toolbar)
                        .background(TvColors.Panel.copy(alpha = 0.92f))
                        .padding(horizontal = TvSpacing.xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvActionButton(
                        icon = TvIcons.ArrowLeft,
                        label = "返回",
                        onClick = onBack,
                        contentDescription = "返回",
                        modifier = Modifier.focusRequester(backFocus).focusProperties {
                            down = playFocus
                            up = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    )
                    Spacer(Modifier.width(TvSpacing.lg))
                    Text(
                        text = name,
                        modifier = Modifier.weight(1f),
                        color = TvColors.TextPrimary,
                        style = TvType.sectionTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (noSubtitles) {
                        Spacer(Modifier.width(TvSpacing.md))
                        TvChip("未检测到字幕")
                    }
                }
                Spacer(Modifier.weight(1f))
                MediaControls(
                    player = player,
                    playing = playing,
                    position = position,
                    duration = duration,
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = TvSpacing.xxl, vertical = TvSpacing.lg),
                    speed = speed,
                    onSpeed = {
                        speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; else -> 1f }
                        player.setPlaybackSpeed(speed)
                    },
                    scaleMode = effectiveScaleMode,
                    onScaleMode = { scaleMode = scaleMode.next() },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    playFocus = playFocus,
                    prevFocus = prevFocus,
                    rewind10Focus = rewind10Focus,
                    forward10Focus = forward10Focus,
                    nextFocus = nextFocus,
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
    speed: Float,
    onSpeed: () -> Unit,
    scaleMode: VideoScaleMode,
    onScaleMode: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playFocus: FocusRequester,
    prevFocus: FocusRequester,
    rewind10Focus: FocusRequester,
    forward10Focus: FocusRequester,
    nextFocus: FocusRequester,
    speedFocus: FocusRequester,
    modeFocus: FocusRequester,
    seekFocus: FocusRequester,
    backFocus: FocusRequester,
    lastControlsButton: MutableState<FocusRequester>,
) {
    // ±10 秒动作的唯一实现：按钮确认键与进度条左右键共用，保证两条入口步进口径一致
    val rewind10 = { player.seekTo((position - 10_000).coerceAtLeast(0)) }
    val forward10 = { player.seekTo((position + 10_000).coerceAtMost(duration)) }
    Column(
        modifier = modifier
            .clip(TvPanelShape)
            .background(TvColors.Panel.copy(alpha = 0.92f))
            .padding(horizontal = TvSpacing.xl, vertical = TvSpacing.lg),
    ) {
        // 控制行在进度行上方：back ↓ play、按钮 ↓ 进度条、进度条 ↑ 记忆按钮的确定性焦点图
        // 依赖该垂直顺序，与原控制层一致。
        Row(
            Modifier.fillMaxWidth().height(TvSizes.action),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvActionButton(
                icon = TvIcons.SkipBack,
                label = "上一个",
                onClick = onPrevious,
                modifier = Modifier.focusRequester(prevFocus).focusProperties {
                    left = FocusRequester.Cancel
                    right = rewind10Focus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = prevFocus },
            )
            TvActionButton(
                icon = TvIcons.Rewind10,
                label = "−10 秒",
                onClick = rewind10,
                modifier = Modifier.focusRequester(rewind10Focus).focusProperties {
                    left = prevFocus
                    right = playFocus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = rewind10Focus },
            )
            TvActionButton(
                icon = if (playing) TvIcons.Pause else TvIcons.Play,
                label = if (playing) "暂停" else "播放",
                onClick = { if (player.isPlaying) player.pause() else player.play() },
                modifier = Modifier.focusRequester(playFocus).focusProperties {
                    left = rewind10Focus
                    right = forward10Focus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = playFocus },
            )
            TvActionButton(
                icon = TvIcons.Forward10,
                label = "+10 秒",
                onClick = forward10,
                modifier = Modifier.focusRequester(forward10Focus).focusProperties {
                    left = playFocus
                    right = nextFocus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = forward10Focus },
            )
            TvActionButton(
                icon = TvIcons.SkipForward,
                label = "下一个",
                onClick = onNext,
                modifier = Modifier.focusRequester(nextFocus).focusProperties {
                    left = forward10Focus
                    right = speedFocus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = nextFocus },
            )
            TvActionButton(
                icon = TvIcons.Gauge,
                label = "${speed}×",
                onClick = onSpeed,
                modifier = Modifier.focusRequester(speedFocus).focusProperties {
                    left = nextFocus
                    right = modeFocus
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = speedFocus },
            )
            TvActionButton(
                icon = TvIcons.AspectRatio,
                label = scaleMode.label,
                onClick = onScaleMode,
                modifier = Modifier.focusRequester(modeFocus).focusProperties {
                    left = speedFocus
                    right = FocusRequester.Cancel
                    up = backFocus
                    down = seekFocus
                }.onFocusChanged { if (it.isFocused) lastControlsButton.value = modeFocus },
            )
        }
        Spacer(Modifier.height(TvSpacing.md))
        // 进度行：左侧当前时间、右侧总时长，不再合并为单段文本。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(format(position), color = TvColors.TextSecondary, style = TvType.meta, maxLines = 1)
            Spacer(Modifier.width(TvSpacing.sm))
            // 进度条左右键在本层 onPreviewKeyEvent（preview 阶段先于 TvSeekBar 内部消费）接管为
            // 「±10 秒步进 + 焦点移入方向对应的 seek 按钮」。进度条行内无其他可聚焦控件，若由
            // TvSeekBar 原地消费左右键，焦点将永远无法离开进度条；步进仍随按键生效，落点按钮即
            // 同一动作的按钮化身，后续可用确认键继续逐次步进。
            // ↑ 手动拦截回最近聚焦的按钮（默认播放/暂停），↓ 原地；时长未知时不消费左右键（不移出）。
            TvSeekBar(
                positionMs = position,
                durationMs = duration,
                onSeek = { player.seekTo(it) },
                bufferedMs = player.bufferedPosition,
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
            Spacer(Modifier.width(TvSpacing.sm))
            Text(format(duration), color = TvColors.TextSecondary, style = TvType.meta, maxLines = 1)
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
