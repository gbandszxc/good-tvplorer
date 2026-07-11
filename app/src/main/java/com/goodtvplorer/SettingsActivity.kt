package com.goodtvplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.goodtvplorer.data.DisplaySettingsStore
import com.goodtvplorer.data.effectiveFontScale
import com.goodtvplorer.data.fontScalePercent
import com.goodtvplorer.data.nextFontScale
import com.goodtvplorer.ui.components.TvButton
import com.goodtvplorer.ui.theme.TvTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private val displaySettings by lazy { DisplaySettingsStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                val fontScale by displaySettings.fontScale.collectAsState(initial = 1f)
                val density = LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, effectiveFontScale(fontScale))) {
                    BackHandler { finish() }
                    SettingsScreen(
                        fontScale = fontScale,
                        onFontScale = { value -> lifecycleScope.launch { displaySettings.setFontScale(value) } },
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(fontScale: Float, onFontScale: (Float) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(36.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("设置", color = Color(0xFFF3F7FA), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            TvButton("显示设置", modifier = Modifier.fillMaxWidth(), onClick = {})
            Spacer(Modifier.weight(1f))
            TvButton("返回", modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = 42.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("显示设置", color = Color(0xFF7CC7D8), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("字体缩放", color = Color(0xFFF3F7FA), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("调整电视端的信息密度；更改会立即生效。", color = Color(0xFFA8B8C7), fontSize = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("−", modifier = Modifier.width(90.dp), enabled = fontScale > 0.8f, onClick = { onFontScale(nextFontScale(fontScale, -0.05f)) })
                Text("${fontScalePercent(fontScale)}%", color = Color(0xFFF3F7FA), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                TvButton("＋", modifier = Modifier.width(90.dp), enabled = fontScale < 1.2f, onClick = { onFontScale(nextFontScale(fontScale, 0.05f)) })
            }
            TvButton("恢复默认", modifier = Modifier.width(180.dp), onClick = { onFontScale(1f) })
        }
    }
}
