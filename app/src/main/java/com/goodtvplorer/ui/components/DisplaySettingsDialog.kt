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

@Composable
fun DisplaySettingsDialog(fontScale: Float, onFontScale: (Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("显示设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("字体缩放 ${(fontScale * 100).toInt()}%", color = Color(0xFFF3F7FA), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("75%") { onFontScale(0.75f) }
                    TvButton("85%") { onFontScale(0.85f) }
                    TvButton("100%") { onFontScale(1f) }
                    TvButton("115%") { onFontScale(1.15f) }
                }
            }
        },
        confirmButton = { TvButton("关闭", onClick = onDismiss) },
    )
}
