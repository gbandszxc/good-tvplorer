package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvChip
import com.github.gbandszxc.goodtvplorer.ui.components.TvSeekBar
import com.github.gbandszxc.goodtvplorer.ui.components.TvSpinner
import com.github.gbandszxc.goodtvplorer.ui.components.tvClickable
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import com.github.gbandszxc.goodtvplorer.viewmodel.PreviewState
import java.io.File
import java.util.concurrent.TimeUnit

private enum class AudioOverlay { None, Cover, Lyrics }

@Composable
fun AudioPreview(
    name: String,
    state: PreviewState,
    audio: AudioPlaybackState,
    actions: AudioPlayerActions,
) {
    val title = audio.title.ifBlank { name }
    val subtitle = listOf(audio.artist, audio.album)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    var overlay by remember { mutableStateOf(AudioOverlay.None) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var pendingFocus by remember { mutableStateOf<FocusRequester?>(null) }

    val playFocus = remember { FocusRequester() }
    val coverFocus = remember { FocusRequester() }
    val lyricsButtonFocus = remember { FocusRequester() }
    val sleepTimerFocus = remember { FocusRequester() }

    val hasMedia = state.media != null
    LaunchedEffect(hasMedia) { if (hasMedia) playFocus.requestFocus() }
    // 覆盖层或定时弹窗关闭后在下一次组合中恢复焦点，避免在覆盖层仍持有焦点时抢先请求。
    // 弹窗拥有独立窗口，关闭时主窗口的 Compose 焦点整体丢失（实测 0 个 focused 节点），
    // 不显式恢复的话后续按键会被系统安置到任意控件。
    LaunchedEffect(overlay, showSleepTimer, pendingFocus) {
        if (overlay == AudioOverlay.None && !showSleepTimer) {
            // 媒体在弹窗/覆盖层打开期间被移除时目标按钮可能已不在组合中，恢复失败则交给系统焦点救援
            runCatching { pendingFocus?.requestFocus() }
            pendingFocus = null
        }
    }

    fun closeOverlay(restore: FocusRequester) {
        pendingFocus = restore
        overlay = AudioOverlay.None
    }

    // 定时弹窗经 Back、取消或选择档位关闭后，焦点统一回到「定时退出」按钮
    fun closeSleepTimer() {
        pendingFocus = sleepTimerFocus
        showSleepTimer = false
    }

    BackHandler(enabled = overlay == AudioOverlay.Cover) { closeOverlay(coverFocus) }
    BackHandler(enabled = overlay == AudioOverlay.Lyrics) { closeOverlay(lyricsButtonFocus) }

    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        Column(Modifier.fillMaxSize().padding(TvSpacing.xxl)) {
            AudioTopBar(title = title, subtitle = subtitle, onBack = actions.onBack)
            when {
                state.loading -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    TvSpinner(size = 36.dp)
                }
                state.error != null -> AudioMessage(
                    icon = TvIcons.AlertTriangle,
                    iconTint = TvColors.Error,
                    title = state.error,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    titleColor = TvColors.Error,
                )
                state.media == null -> AudioMessage(
                    icon = TvIcons.Disc,
                    iconTint = TvColors.TextMuted,
                    title = "没有可用的音频",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> AudioBody(
                    state = state,
                    audio = audio,
                    actions = actions,
                    playFocus = playFocus,
                    coverFocus = coverFocus,
                    lyricsButtonFocus = lyricsButtonFocus,
                    sleepTimerFocus = sleepTimerFocus,
                    onOpenCover = { overlay = AudioOverlay.Cover },
                    onOpenLyrics = { overlay = AudioOverlay.Lyrics },
                    onOpenSleepTimer = { showSleepTimer = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (overlay == AudioOverlay.Cover) {
            CoverOverlay(
                cover = state.cover,
                title = title,
                subtitle = subtitle,
                onClose = { closeOverlay(coverFocus) },
            )
        }
        if (overlay == AudioOverlay.Lyrics) {
            LyricsOverlay(
                title = title,
                state = state,
                audio = audio,
                actions = actions,
                onClose = { closeOverlay(lyricsButtonFocus) },
            )
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            remainingMs = audio.sleepTimerRemainingMs,
            onSelectMinutes = {
                actions.onStartSleepTimer(it)
                closeSleepTimer()
            },
            onCancelTimer = {
                actions.onCancelSleepTimer()
                closeSleepTimer()
            },
            onDismiss = { closeSleepTimer() },
        )
    }
}

/** 顶栏：返回图标动作 + 标题 + 艺术家/专辑副标题，固定 56dp 高。 */
@Composable
private fun AudioTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(TvSizes.toolbar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvActionButton(
            icon = TvIcons.ArrowLeft,
            label = "返回",
            onClick = onBack,
        )
        Spacer(Modifier.width(TvSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TvColors.TextPrimary,
                style = TvType.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = TvColors.TextSecondary,
                    style = TvType.meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 居中的图标 + 标题空态/错误态。
 */
@Composable
private fun AudioMessage(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = TvColors.TextSecondary,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(TvSizes.action),
            )
            Text(
                text = title,
                color = titleColor,
                style = TvType.body,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioBody(
    state: PreviewState,
    audio: AudioPlaybackState,
    actions: AudioPlayerActions,
    playFocus: FocusRequester,
    coverFocus: FocusRequester,
    lyricsButtonFocus: FocusRequester,
    sleepTimerFocus: FocusRequester,
    onOpenCover: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seekFocus = remember { FocusRequester() }
    val timerMinutes = sleepTimerMinutes(audio.sleepTimerRemainingMs)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(top = TvSpacing.lg)
                .weight(1f)
                .fillMaxWidth()
                .horizontalSwipe(actions.onPrevious, actions.onNext),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.xl),
        ) {
            CoverPane(
                cover = state.cover,
                onOpen = onOpenCover,
                focusRequester = coverFocus,
                playFocusRequester = playFocus,
                modifier = Modifier.weight(0.36f),
            )
            AudioLyricsPanel(
                cues = state.timedText,
                positionMs = audio.positionMs,
                loading = state.lyricsLoading,
                modifier = Modifier.weight(0.64f),
            )
        }
        audio.error?.let {
            Text(
                text = it,
                color = TvColors.Error,
                style = TvType.meta,
                modifier = Modifier.padding(top = TvSpacing.sm),
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = TvSpacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TvSeekBar(
                    positionMs = audio.positionMs,
                    durationMs = audio.durationMs,
                    onSeek = actions.onSeek,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(seekFocus)
                        .focusProperties {
                            up = coverFocus
                            down = playFocus
                        },
                )
                Spacer(Modifier.width(TvSpacing.lg))
                Text(
                    text = "${formatAudioTime(audio.positionMs)} / ${formatAudioTime(audio.durationMs)}",
                    color = TvColors.TextSecondary,
                    style = TvType.meta,
                    maxLines = 1,
                )
            }
            // 状态行：即使没有焦点，也能看到当前播放模式、定时剩余和歌词来源。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = TvSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
            ) {
                TvChip(text = audio.playMode.label)
                if (timerMinutes > 0) {
                    TvChip(text = "定时 $timerMinutes 分", accent = true)
                }
                state.lyricsSource?.let {
                    Text(
                        text = it,
                        modifier = Modifier.weight(1f),
                        color = TvColors.TextSecondary,
                        style = TvType.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = TvSpacing.md).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvActionButton(
                    icon = TvIcons.SkipBack,
                    label = "上一首",
                    onClick = actions.onPrevious,
                    modifier = Modifier.focusProperties { up = seekFocus },
                )
                TvActionButton(
                    icon = if (audio.playing) TvIcons.Pause else TvIcons.Play,
                    label = if (audio.playing) "暂停" else "播放",
                    onClick = actions.onTogglePlayPause,
                    modifier = Modifier.focusRequester(playFocus).focusProperties { up = seekFocus },
                )
                TvActionButton(
                    icon = TvIcons.SkipForward,
                    label = "下一首",
                    onClick = actions.onNext,
                    modifier = Modifier.focusProperties { up = seekFocus },
                )
                TvActionButton(
                    icon = playModeIcon(audio.playMode),
                    label = audio.playMode.label,
                    onClick = actions.onCyclePlayMode,
                    modifier = Modifier.focusProperties { up = seekFocus },
                )
                TvActionButton(
                    icon = TvIcons.Timer,
                    label = sleepTimerLabel(audio.sleepTimerRemainingMs),
                    onClick = onOpenSleepTimer,
                    selected = timerMinutes > 0,
                    modifier = Modifier.focusRequester(sleepTimerFocus).focusProperties { up = seekFocus },
                )
                TvActionButton(
                    icon = TvIcons.Expand,
                    label = "全屏歌词",
                    onClick = onOpenLyrics,
                    modifier = Modifier
                        .focusRequester(lyricsButtonFocus)
                        .focusProperties { up = seekFocus },
                )
            }
        }
    }
}

private fun playModeIcon(mode: AudioPlayMode): ImageVector = when (mode) {
    AudioPlayMode.Sequential -> TvIcons.List
    AudioPlayMode.RepeatOne -> TvIcons.RepeatOne
    AudioPlayMode.RepeatAll -> TvIcons.Repeat
    AudioPlayMode.Shuffle -> TvIcons.Shuffle
}

private fun sleepTimerMinutes(remainingMs: Long): Long =
    if (remainingMs > 0) (remainingMs + 59_999L) / 60_000L else 0L

private fun sleepTimerLabel(remainingMs: Long): String {
    val minutes = sleepTimerMinutes(remainingMs)
    return if (minutes > 0) "定时 $minutes 分" else "定时退出"
}

@Composable
private fun CoverPane(
    cover: File?,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.lg)
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(0),
        label = "cover-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(0),
        label = "cover-border-color",
    )
    Box(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(TvColors.Panel)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .focusProperties { down = playFocusRequester }
            .focusable()
            .tvClickable(onClick = onOpen)
            .semantics { contentDescription = "音频封面，按确认键查看全屏封面" },
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = TvIcons.Disc,
                contentDescription = "暂无音频封面",
                tint = TvColors.TextMuted,
                modifier = Modifier.size(72.dp),
            )
        }
        if (focused) {
            TvChip(
                text = "确认键查看全屏封面",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(TvSpacing.md),
            )
        }
    }
}

/**
 * 全屏覆盖层的焦点根节点：打开时独占焦点，或把初始焦点交给 [contentFocus] 指定的内部控件，
 * 避免方向键落回被覆盖的底层控件。
 * 未传 [contentFocus] 时（如封面层）确认键由本层消费并关闭；
 * 传入 [contentFocus] 时（如歌词层「返回」按钮）确认键穿透给内部控件自行处理，
 * 否则本层的 onPreviewKeyEvent 会在 KeyUp 抢先关闭覆盖层，内部按钮无法被独立确认。
 */
@Composable
private fun FullscreenLayer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentFocus: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { (contentFocus ?: focusRequester).requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyUp && isOk && contentFocus == null) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        content = content,
    )
}

@Composable
private fun CoverOverlay(
    cover: File?,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
) {
    FullscreenLayer(
        onClose = onClose,
        modifier = Modifier
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .semantics { contentDescription = "全屏封面，按返回键关闭" },
    ) {
        cover?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.22f).blur(24.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = "音频封面",
                modifier = Modifier.fillMaxSize().padding(48.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TvSpacing.md),
            ) {
                Icon(
                    imageVector = TvIcons.Disc,
                    contentDescription = "暂无封面",
                    tint = TvColors.TextMuted,
                    modifier = Modifier.size(160.dp),
                )
                Text("暂无封面", color = TvColors.TextSecondary, style = TvType.body)
            }
        }
        // 浅色封面会让底部文字失去对比度，用渐变把标题区压暗。
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = TvSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
        ) {
            Text(
                text = title,
                color = TvColors.TextPrimary,
                style = TvType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = TvColors.TextSecondary,
                    style = TvType.meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "按返回键关闭",
                modifier = Modifier.padding(top = TvSpacing.sm),
                color = TvColors.TextSecondary,
                style = TvType.caption,
            )
        }
    }
}

@Composable
private fun LyricsOverlay(
    title: String,
    state: PreviewState,
    audio: AudioPlaybackState,
    actions: AudioPlayerActions,
    onClose: () -> Unit,
) {
    val lyricsBackFocus = remember { FocusRequester() }
    FullscreenLayer(onClose = onClose, contentFocus = lyricsBackFocus) {
        AudioLyricsFullscreen(
            title = title,
            cues = state.timedText,
            positionMs = audio.positionMs,
            loading = state.lyricsLoading,
            onSeek = actions.onSeek,
            onBack = onClose,
            backButtonFocus = lyricsBackFocus,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun formatAudioTime(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return "%02d:%02d".format(total / 60, total % 60)
}
