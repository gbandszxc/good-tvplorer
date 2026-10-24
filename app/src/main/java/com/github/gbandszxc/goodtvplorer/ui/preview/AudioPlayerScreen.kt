package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.github.gbandszxc.goodtvplorer.R
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.components.tvOkClick
import com.github.gbandszxc.goodtvplorer.viewmodel.PreviewState
import java.io.File
import java.util.concurrent.TimeUnit

/** 项目统一的焦点描边色，与 TvButton 保持一致。 */
private val FocusRingColor = Color(0xFFFFE3A1)

private const val SEEK_STEP_MS = 10_000L

private enum class AudioOverlay { None, Cover, Lyrics }

@Composable
fun AudioPreview(
    name: String,
    state: PreviewState,
    audio: AudioPlaybackState,
    actions: AudioPlayerActions,
) {
    val colorScheme = MaterialTheme.colorScheme
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

    val hasMedia = state.media != null
    LaunchedEffect(hasMedia) { if (hasMedia) playFocus.requestFocus() }
    // 覆盖层关闭后在下一次组合中恢复焦点，避免在覆盖层仍持有焦点时抢先请求。
    LaunchedEffect(overlay, pendingFocus) {
        if (overlay == AudioOverlay.None) {
            pendingFocus?.requestFocus()
            pendingFocus = null
        }
    }

    fun closeOverlay(restore: FocusRequester) {
        pendingFocus = restore
        overlay = AudioOverlay.None
    }

    BackHandler(enabled = overlay == AudioOverlay.Cover) { closeOverlay(coverFocus) }
    BackHandler(enabled = overlay == AudioOverlay.Lyrics) { closeOverlay(lyricsButtonFocus) }

    Box(Modifier.fillMaxSize().background(colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colorScheme.onBackground,
                        fontSize = 28.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                TvButton("返回", onClick = actions.onBack)
            }
            when {
                state.loading -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error, color = colorScheme.error, fontSize = 26.sp) }
                state.media == null -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { Text("没有可用的音频", color = colorScheme.onSurfaceVariant, fontSize = 26.sp) }
                else -> AudioBody(
                    state = state,
                    audio = audio,
                    actions = actions,
                    playFocus = playFocus,
                    coverFocus = coverFocus,
                    lyricsButtonFocus = lyricsButtonFocus,
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
                showSleepTimer = false
            },
            onCancelTimer = {
                actions.onCancelSleepTimer()
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false },
        )
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
    onOpenCover: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(top = 18.dp)
                .weight(1f)
                .fillMaxWidth()
                .horizontalSwipe(actions.onPrevious, actions.onNext),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            CoverPane(
                cover = state.cover,
                onOpen = onOpenCover,
                focusRequester = coverFocus,
                playFocusRequester = playFocus,
                modifier = Modifier.weight(0.38f),
            )
            AudioLyricsPanel(
                cues = state.timedText,
                positionMs = audio.positionMs,
                loading = state.lyricsLoading,
                modifier = Modifier.weight(0.62f),
            )
        }
        audio.error?.let {
            Text(it, color = colorScheme.error, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AudioSeekBar(
                    positionMs = audio.positionMs,
                    durationMs = audio.durationMs,
                    onSeek = actions.onSeek,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "${formatAudioTime(audio.positionMs)} / ${formatAudioTime(audio.durationMs)}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f).focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvButton("上一首", onClick = actions.onPrevious)
                    TvButton(
                        text = if (audio.playing) "暂停" else "播放",
                        modifier = Modifier.focusRequester(playFocus).focusProperties { up = coverFocus },
                        onClick = actions.onTogglePlayPause,
                    )
                    TvButton("下一首", onClick = actions.onNext)
                    TvButton(audio.playMode.label, onClick = actions.onCyclePlayMode)
                    TvButton(
                        text = sleepTimerLabel(audio.sleepTimerRemainingMs),
                        onClick = onOpenSleepTimer,
                    )
                    TvButton(
                        text = "全屏歌词",
                        modifier = Modifier.focusRequester(lyricsButtonFocus),
                        onClick = onOpenLyrics,
                    )
                }
                state.lyricsSource?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(it, color = colorScheme.onSurfaceVariant, fontSize = 18.sp)
                }
            }
        }
    }
}

private fun sleepTimerLabel(remainingMs: Long): String =
    if (remainingMs > 0) "定时 ${(remainingMs + 59_999L) / 60_000L}分" else "定时退出"

@Composable
private fun CoverPane(
    cover: File?,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
    playFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val borderWidth by animateDpAsState(
        if (focused) 3.dp else 1.dp,
        animationSpec = tween(160),
        label = "cover-border",
    )
    val borderColor by animateColorAsState(
        if (focused) FocusRingColor else colorScheme.outlineVariant,
        animationSpec = tween(160),
        label = "cover-border-color",
    )
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(colorScheme.surfaceVariant)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .focusProperties { down = playFocusRequester }
            .focusable()
            .tvOkClick(onOpen, role = Role.Button)
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
                painterResource(R.drawable.ic_disc),
                contentDescription = "暂无音频封面",
                tint = colorScheme.secondary,
                modifier = Modifier.size(72.dp),
            )
        }
        if (focused) {
            Text(
                text = "确认键查看全屏封面",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colorScheme.background.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = colorScheme.onBackground,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun AudioSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }
    val borderWidth by animateDpAsState(
        if (focused) 3.dp else 1.dp,
        animationSpec = tween(160),
        label = "seek-border",
    )
    val borderColor by animateColorAsState(
        if (focused) FocusRingColor else colorScheme.outlineVariant,
        animationSpec = tween(160),
        label = "seek-border-color",
    )
    val displayPosition = if (dragging) previewMs else positionMs
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colorScheme.surface else colorScheme.surfaceVariant)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics { contentDescription = "播放进度，左右键或触屏拖动调整" }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    onSeek(seekPosition(durationMs, offset.x, size.width))
                }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        previewMs = seekPosition(durationMs, offset.x, size.width)
                    },
                    onDragEnd = {
                        onSeek(previewMs)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    previewMs = seekPosition(durationMs, change.position.x, size.width)
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || durationMs <= 0) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeek((positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight -> {
                        onSeek((positionMs + SEEK_STEP_MS).coerceAtMost(durationMs))
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        LinearProgressIndicator(
            progress = {
                if (durationMs <= 0) 0f else (displayPosition.toFloat() / durationMs).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth(),
            color = colorScheme.primary,
            trackColor = colorScheme.outlineVariant,
        )
    }
}

/**
 * 全屏覆盖层的焦点根节点：打开时独占焦点，确认键与触屏点击均关闭，
 * 避免方向键落回被覆盖的底层控件。
 */
@Composable
private fun FullscreenLayer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (event.type == KeyEventType.KeyUp && isOk) {
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
    val colorScheme = MaterialTheme.colorScheme
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_disc),
                    contentDescription = "暂无封面",
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(160.dp),
                )
                Text("暂无封面", color = colorScheme.onSurfaceVariant, fontSize = 20.sp)
            }
        }
        // 浅色封面会让底部文字失去对比度，用渐变把标题区压暗。
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(280.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = colorScheme.onBackground,
                fontSize = 26.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "按返回键关闭",
                modifier = Modifier.padding(top = 8.dp),
                color = colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
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
    FullscreenLayer(onClose = onClose) {
        AudioLyricsFullscreen(
            title = title,
            cues = state.timedText,
            positionMs = audio.positionMs,
            loading = state.lyricsLoading,
            onSeek = actions.onSeek,
            onBack = onClose,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun formatAudioTime(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return "%02d:%02d".format(total / 60, total % 60)
}
