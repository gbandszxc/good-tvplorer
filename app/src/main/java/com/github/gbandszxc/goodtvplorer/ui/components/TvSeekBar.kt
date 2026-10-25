package com.github.gbandszxc.goodtvplorer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import kotlin.math.roundToLong

/** 遥控器左右键步进 seek 的步长（毫秒），与音频页现状一致。 */
private const val SEEK_STEP_MS = 10_000L

/** 轨道与容器左右边缘的内边距，供绘制与触屏换算共用。 */
private val SeekBarHorizontalPadding = 12.dp

/** 轨道高度。 */
private val SeekBarTrackHeight = 8.dp

private val SeekBarThumbIdle = 6.dp
private val SeekBarThumbFocused = 8.dp

/**
 * 音 / 视频共用的遥控器进度条（自绘，无 Material 指示器）。
 *
 * - 轨道 8dp 全圆角：静止 OutlineVariant、缓冲 SurfaceRaised、进度 Accent（聚焦 Focus）。
 * - 滑块 12dp（聚焦 16dp），聚焦时 2dp FocusRing 描边 + Surface 背景。
 * - 触屏：点击立即 onSeek；水平拖动只预览（dragging/previewMs），松手提交，取消不提交。
 * - 遥控器：KeyDown 左右按 10 秒步进并消费；durationMs <= 0 时不消费方向键、不响应触屏。
 */
@Composable
fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bufferedMs: Long = 0,
    showThumb: Boolean = true,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(positionMs) }

    val shape = RoundedCornerShape(TvRadius.md)
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "seek-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "seek-border-color",
    )
    val progressColor by animateColorAsState(
        targetValue = if (focused) TvColors.Focus else TvColors.Accent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "seek-progress-color",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (focused) SeekBarThumbFocused else SeekBarThumbIdle,
        animationSpec = when {
            !motionEnabled -> tween(durationMillis = 0)
            focused -> TvMotion.expand<Dp>()
            else -> TvMotion.collapse<Dp>()
        },
        label = "seek-thumb-radius",
    )

    val displayPosition = if (dragging) previewMs else positionMs
    val progressFraction = if (durationMs <= 0) 0f else (displayPosition.toFloat() / durationMs).coerceIn(0f, 1f)
    val bufferedFraction = if (durationMs <= 0) 0f else (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(TvSizes.action)
            .clip(shape)
            .background(if (focused) TvColors.Surface else Color.Transparent)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics { contentDescription = "播放进度，左右键或触屏拖动调整" }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                val horizontalPadding = SeekBarHorizontalPadding.toPx()
                detectTapGestures { offset ->
                    onSeek(seekPosition(durationMs, offset.x, size.width, horizontalPadding))
                }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                val horizontalPadding = SeekBarHorizontalPadding.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        previewMs = seekPosition(durationMs, offset.x, size.width, horizontalPadding)
                    },
                    onDragEnd = {
                        onSeek(previewMs)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    previewMs = seekPosition(durationMs, change.position.x, size.width, horizontalPadding)
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
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = SeekBarHorizontalPadding.toPx()
            val trackHeight = SeekBarTrackHeight.toPx()
            val trackWidth = (size.width - inset * 2f).coerceAtLeast(0f)
            if (trackWidth <= 0f) return@Canvas
            val trackTop = (size.height - trackHeight) / 2f
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = TvColors.OutlineVariant,
                topLeft = Offset(inset, trackTop),
                size = Size(trackWidth, trackHeight),
                cornerRadius = cornerRadius,
            )
            if (bufferedFraction > 0f) {
                drawRoundRect(
                    color = TvColors.SurfaceRaised,
                    topLeft = Offset(inset, trackTop),
                    size = Size(trackWidth * bufferedFraction, trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
            if (progressFraction > 0f) {
                drawRoundRect(
                    color = progressColor,
                    topLeft = Offset(inset, trackTop),
                    size = Size(trackWidth * progressFraction, trackHeight),
                    cornerRadius = cornerRadius,
                )
            }
            if (showThumb) {
                val radius = thumbRadius.toPx()
                val centerX = inset + trackWidth * progressFraction
                val center = Offset(centerX, size.height / 2f)
                drawCircle(color = TvColors.Surface, radius = radius, center = center)
                drawCircle(
                    color = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
                    radius = radius,
                    center = center,
                    style = Stroke(width = if (focused) 2.dp.toPx() else 1.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 点击 / 拖动位置按扣除左右内边距后的可用宽度换算为进度（毫秒），与轨道视觉一致。
 */
private fun seekPosition(duration: Long, x: Float, width: Int, horizontalPadding: Float): Long {
    if (duration <= 0) return 0L
    val usableWidth = width.toFloat() - horizontalPadding * 2f
    if (usableWidth <= 0f) return 0L
    val fraction = ((x - horizontalPadding) / usableWidth).coerceIn(0f, 1f)
    return (duration.toDouble() * fraction).roundToLong().coerceIn(0L, duration)
}
