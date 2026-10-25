package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gbandszxc.goodtvplorer.domain.TimedTextCue
import com.github.gbandszxc.goodtvplorer.domain.currentCueIndex
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvSpinner
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val FOLLOW_RESUME_DELAY_MS = 4_000L
private const val NEARBY_CUE_COUNT = 3

/** 歌词面板当前行 / 其余行。 */
private val PanelCurrentStyle = TvType.body.copy(
    fontSize = 28.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.SemiBold,
)
private val PanelOtherStyle = TvType.body.copy(fontSize = 22.sp, lineHeight = 32.sp)

/** 全屏歌词当前行 / 其余行。 */
private val FullscreenCurrentStyle = TvType.body.copy(
    fontSize = 34.sp,
    lineHeight = 48.sp,
    fontWeight = FontWeight.SemiBold,
)
private val FullscreenOtherStyle = TvType.body.copy(fontSize = 26.sp, lineHeight = 40.sp)

@Composable
fun AudioLyricsPanel(
    cues: List<TimedTextCue>,
    positionMs: Long,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = currentCueIndex(cues, positionMs)
    val following = rememberFollowEnabled(listState)
    FollowCurrentCue(listState, currentIndex, following)
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(TvRadius.lg))
            .background(TvColors.SurfaceSunken)
            .padding(TvSpacing.xl),
    ) {
        if (cues.isEmpty()) {
            LyricsPlaceholder(
                loading = loading,
                titleStyle = TvType.body,
                hintStyle = TvType.meta,
                spinnerSize = 28.dp,
                iconSize = 32.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(cues) { index, cue ->
                    val current = index == currentIndex
                    Text(
                        text = cue.text,
                        color = if (current) TvColors.TextPrimary else TvColors.TextSecondary,
                        style = if (current) PanelCurrentStyle else PanelOtherStyle,
                    )
                }
            }
        }
    }
}

@Composable
fun AudioLyricsFullscreen(
    title: String,
    cues: List<TimedTextCue>,
    positionMs: Long,
    loading: Boolean,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    backButtonFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = currentCueIndex(cues, positionMs)
    val following = rememberFollowEnabled(listState)
    FollowCurrentCue(listState, currentIndex, following)
    Box(modifier.fillMaxSize().background(TvColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TvSizes.toolbar)
                    .padding(horizontal = TvSpacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvActionButton(
                    icon = TvIcons.ArrowLeft,
                    label = "返回",
                    onClick = onBack,
                    modifier = Modifier.focusRequester(backButtonFocus),
                )
                Spacer(Modifier.width(TvSpacing.md))
                Text(
                    text = "歌词全屏 · $title",
                    modifier = Modifier.weight(1f),
                    color = TvColors.TextPrimary,
                    style = TvType.sectionTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (cues.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LyricsPlaceholder(
                        loading = loading,
                        titleStyle = TvType.sectionTitle,
                        hintStyle = TvType.meta,
                        spinnerSize = 36.dp,
                        iconSize = 48.dp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(cues) { index, cue ->
                        val current = index == currentIndex
                        // 单行且无时间标签时不提供跳转；多行歌词即使首行从 0 开始也可回跳。
                        val seekable = cue.startMs > 0 || cues.size > 1
                        Text(
                            text = cue.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(cue.startMs, seekable) {
                                    detectTapGestures { if (seekable) onSeek(cue.startMs) }
                                }
                                .padding(vertical = TvSpacing.xs),
                            color = if (current) TvColors.TextPrimary else TvColors.TextSecondary,
                            style = if (current) FullscreenCurrentStyle else FullscreenOtherStyle,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 歌词占位：加载时显示自绘旋转弧线；无歌词时用 Music 图标 + 标题 + 说明，
 * 不使用 Material 进度控件或空态组件。
 */
@Composable
private fun LyricsPlaceholder(
    loading: Boolean,
    titleStyle: TextStyle,
    hintStyle: TextStyle,
    spinnerSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
    ) {
        if (loading) {
            TvSpinner(size = spinnerSize)
            Text(
                text = "歌词加载中…",
                color = TvColors.TextPrimary,
                style = titleStyle,
            )
        } else {
            Icon(
                imageVector = TvIcons.Music,
                contentDescription = null,
                tint = TvColors.TextMuted,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = "未找到歌词",
                color = TvColors.TextPrimary,
                style = titleStyle,
            )
            Text(
                text = "支持同名 .lrc 或内嵌歌词",
                color = TvColors.TextSecondary,
                style = hintStyle,
            )
        }
    }
}

/**
 * 触摸拖动时暂停自动跟随，停止滚动 4 秒后恢复。
 * 只把用户拖动（[DragInteraction]）视为暂停来源，程序化的自动滚动不触发暂停。
 */
@Composable
private fun rememberFollowEnabled(listState: LazyListState): Boolean {
    var following by remember(listState) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        coroutineScope {
            var userDragging = false
            var resumePending = false
            launch {
                listState.interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is DragInteraction.Start -> {
                            userDragging = true
                            resumePending = true
                            following = false
                        }
                        is DragInteraction.Stop, is DragInteraction.Cancel -> userDragging = false
                    }
                }
            }
            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collectLatest { scrolling ->
                    if (!scrolling && resumePending && !userDragging) {
                        delay(FOLLOW_RESUME_DELAY_MS)
                        resumePending = false
                        following = true
                    }
                }
        }
    }
    return following
}

@Composable
private fun FollowCurrentCue(
    listState: LazyListState,
    currentIndex: Int,
    following: Boolean,
) {
    LaunchedEffect(listState, currentIndex, following) {
        if (!following || currentIndex < 0) return@LaunchedEffect
        // 视口高度在首次布局后才可用，等待一次非零测量再计算 1/3 偏移。
        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }
        // scrollOffset 为正值会把目标行向上推，负值才能在行首预留视口 1/3 的空白。
        val offset = -(viewportHeight / 3)
        if (abs(currentIndex - listState.firstVisibleItemIndex) <= NEARBY_CUE_COUNT) {
            listState.animateScrollToItem(currentIndex, offset)
        } else {
            // 跨越大段进度（如 seek）时直接定位，避免逐项长距离动画。
            listState.scrollToItem(currentIndex, offset)
        }
    }
}
