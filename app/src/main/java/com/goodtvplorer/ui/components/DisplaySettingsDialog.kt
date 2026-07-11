package com.goodtvplorer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.goodtvplorer.data.fontScalePercent
import com.goodtvplorer.data.nextFontScale

@Composable
fun DisplaySettingsDialog(fontScale: Float, onFontScale: (Float) -> Unit, onDismiss: () -> Unit) {
    val compactPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF101A26), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.width(460.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("显示设置", color = Color(0xFFF3F7FA), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("字体缩放 ${fontScalePercent(fontScale)}%", color = Color(0xFFF3F7FA), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton("−", enabled = fontScale > 0.8f, contentPadding = compactPadding, fontSize = 20.sp) { onFontScale(nextFontScale(fontScale, -0.05f)) }
                    TvButton("＋", enabled = fontScale < 1.2f, contentPadding = compactPadding, fontSize = 20.sp) { onFontScale(nextFontScale(fontScale, 0.05f)) }
                    TvButton("恢复默认", contentPadding = compactPadding, fontSize = 20.sp) { onFontScale(1f) }
                    TvButton("关闭", contentPadding = compactPadding, fontSize = 20.sp, onClick = onDismiss)
                }
            }
        }
    }
}
