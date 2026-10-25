package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionTone
import com.github.gbandszxc.goodtvplorer.ui.components.TvDialog
import com.github.gbandszxc.goodtvplorer.ui.components.TvLabelMode
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType

private val presetMinutes = listOf(15, 30, 60, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    remainingMs: Long,
    onSelectMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstPresetFocus = remember { FocusRequester() }
    val remainingMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)

    TvDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = "定时退出",
        icon = TvIcons.Timer,
        width = 520.dp,
        dismissible = true,
        content = {
            Text(
                text = if (remainingMs > 0) "剩余约 $remainingMinutes 分钟" else "当前未开启",
                color = TvColors.TextSecondary,
                style = TvType.body,
            )
            Spacer(Modifier.height(TvSpacing.lg))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = presetMinutes.size,
            ) {
                presetMinutes.forEachIndexed { index, minutes ->
                    TvActionButton(
                        icon = TvIcons.Timer,
                        label = "$minutes 分钟",
                        onClick = { onSelectMinutes(minutes) },
                        labelMode = TvLabelMode.Always,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstPresetFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
            // Dialog 内容在独立子组合中布局，等一帧后再请求焦点；未挂载时按帧重试。
            LaunchedEffect(Unit) {
                repeat(3) {
                    withFrameNanos { }
                    val focused = runCatching { firstPresetFocus.requestFocus() }.getOrDefault(false)
                    if (focused) return@LaunchedEffect
                }
            }
        },
        actions = {
            TvActionButton(
                icon = TvIcons.Trash2,
                label = "关闭定时",
                onClick = onCancelTimer,
                enabled = remainingMs > 0,
                tone = TvActionTone.Destructive,
            )
            TvActionButton(
                icon = TvIcons.X,
                label = "取消",
                onClick = onDismiss,
            )
        },
    )
}
