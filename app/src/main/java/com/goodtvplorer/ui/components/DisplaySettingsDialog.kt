package com.goodtvplorer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodtvplorer.data.nextFontScale

@Composable
fun DisplaySettingsDialog(fontScale: Float, onFontScale: (Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("显示设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("字体缩放 ${(fontScale * 100).toInt()}%", color = Color(0xFFF3F7FA), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("−", enabled = fontScale > 0.8f) { onFontScale(nextFontScale(fontScale, -0.05f)) }
                    Text("${(fontScale * 100).toInt()}%", color = Color(0xFFF3F7FA), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    TvButton("＋", enabled = fontScale < 1.2f) { onFontScale(nextFontScale(fontScale, 0.05f)) }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("恢复默认") { onFontScale(1f) }
                TvButton("关闭", onClick = onDismiss)
            }
        },
    )
}
