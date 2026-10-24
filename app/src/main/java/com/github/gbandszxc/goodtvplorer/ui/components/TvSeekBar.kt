package com.github.gbandszxc.goodtvplorer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/** 项目统一的焦点描边色，与 TvButton 保持一致。 */
private val FocusRingColor = Color(0xFFFFE3A1)

/** 遥控器左右键步进 seek 的步长（毫秒），与音频页现状一致。 */
private const val SEEK_STEP_MS = 10_000L

/**
 * 音 / 视频共用的遥控器进度条。
 * 样式沿用 TvButton 焦点 token：48dp 高、8dp 圆角；静止态 colorScheme.surfaceVariant 背景 + 1dp outlineVariant 描边，
 * 焦点态 surface 背景 + 3dp 焦点描边（0xFFFFE3A1，即 AudioPlayerScreen.kt:74 的 FocusRingColor）；
 * 进度 primary、轨道 outlineVariant。
 * 触屏：点击立即 onSeek；水平拖动仅预览（dragging/previewMs），松手 onSeek(previewMs)，取消不 seek。
 * 遥控器：KeyDown 的左/右按 10_000ms 步进 seek 并消费——音频页用法（按钮行无 seek 按钮，
 * 行内消费是唯一遥控器 seek 入口）；其余按键（含上/下）一律 return false，交给调用方 modifier
 * 上的 focusProperties/onPreviewKeyEvent 决定跨行移动。视频控制层因进度行内无其他可聚焦控件，
 * 行内消费会让焦点永远无法离开进度条，故调用方在 onPreviewKeyEvent（preview 阶段先于本层）
 * 接管左右键为「±10 秒 + 移入 −10秒/+10秒 按钮」，本层分支在该页不触发。
 * durationMs <= 0：不响应触屏、不消费方向键、进度恒 0，但保持可聚焦。
 */
@Composable
fun TvSeekBar(
    positionMs: Long,        // 当前播放位置（毫秒）；拖动预览期间内部改显示预览值
    durationMs: Long,        // 总时长（毫秒）；<= 0 视为不可用
    onSeek: (Long) -> Unit,  // 点击、松手、左右键步进共用回调
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

/** 点击 / 拖动位置按可用宽度换算为进度（毫秒），与视频页换算口径一致。 */
private fun seekPosition(duration: Long, x: Float, width: Int): Long =
    if (duration <= 0 || width <= 0) 0 else (duration * (x / width).coerceIn(0f, 1f)).roundToLong()
