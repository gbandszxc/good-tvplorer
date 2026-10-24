package com.github.gbandszxc.goodtvplorer.ui.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton

private val presetMinutes = listOf(15, 30, 60, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(
    remainingMs: Long,
    onSelectMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    // Dialog 拥有独立窗口，会重置 LocalDensity；在创建前捕获缩放后的值并重新提供。
    val density = LocalDensity.current
    val firstPresetFocus = remember { FocusRequester() }
    val remainingMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .heightIn(max = maxHeight)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape,
                        ),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            "定时退出",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (remainingMs > 0) "剩余约 $remainingMinutes 分钟" else "当前未开启",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 20.sp,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            maxItemsInEachRow = presetMinutes.size,
                        ) {
                            presetMinutes.forEachIndexed { index, minutes ->
                                TvButton(
                                    text = "$minutes 分钟",
                                    modifier = if (index == 0) {
                                        Modifier.focusRequester(firstPresetFocus)
                                    } else {
                                        Modifier
                                    },
                                    onClick = { onSelectMinutes(minutes) },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvButton(
                                text = "关闭定时",
                                destructive = true,
                                enabled = remainingMs > 0,
                                onClick = onCancelTimer,
                            )
                            TvButton(text = "取消", onClick = onDismiss)
                        }
                    }
                }
                LaunchedEffect(Unit) { firstPresetFocus.requestFocus() }
            }
        }
    }
}
