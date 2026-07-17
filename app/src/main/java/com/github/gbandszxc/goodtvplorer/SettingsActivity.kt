package com.github.gbandszxc.goodtvplorer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.data.fontScalePercent
import com.github.gbandszxc.goodtvplorer.data.nextFontScale
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import com.github.gbandszxc.goodtvplorer.domain.CacheRepository
import com.github.gbandszxc.goodtvplorer.domain.Formatters
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.components.tvOkClick
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import kotlinx.coroutines.launch

private val SettingsSidebar = Color(0xFF0E1824)
private val SettingsDivider = Color(0xFF26384B)
private val SettingsSurface = Color(0xFF152232)
private val SettingsIndicator = Color(0xFF7CC7D8)
private val SettingsFocus = Color(0xFFFFC857)
private val SettingsFocusBorder = Color(0xFFFFE3A1)
private val SettingsText = Color(0xFFF3F7FA)
private val SettingsMutedText = Color(0xFFA8B8C7)
private val SettingsFocusText = Color(0xFF151007)

private enum class SettingsSection { Display, Cache, About }

class SettingsActivity : ComponentActivity() {
    private val displaySettings by lazy { DisplaySettingsRepository(applicationContext) }
    private val cache by lazy { CacheRepository(applicationContext) }

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
                        cacheSize = { cache.sizeBytes() },
                        clearCache = { cache.clear() },
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(fontScale: Float, onFontScale: (Float) -> Unit, cacheSize: suspend () -> Long, clearCache: suspend () -> Unit, onClose: () -> Unit) {
    var section by remember { mutableStateOf(SettingsSection.Display) }
    val context = LocalContext.current
    val githubUrl = "https://github.com/gbandszxc/good-tvplorer"

    Row(Modifier.fillMaxSize().background(Color(0xFF0B121A))) {
        Column(
            Modifier.width((260 * fontScale).dp).fillMaxHeight().background(SettingsSidebar).padding(horizontal = 28.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("设置", color = SettingsText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            SettingsMenuItem("显示设置", selected = section == SettingsSection.Display) { section = SettingsSection.Display }
            SettingsMenuItem("缓存设置", selected = section == SettingsSection.Cache) { section = SettingsSection.Cache }
            SettingsMenuItem("关于", selected = section == SettingsSection.About) { section = SettingsSection.About }
            Spacer(Modifier.weight(1f))
            SettingsMenuItem("返回", selected = false, onClick = onClose)
        }
        Spacer(Modifier.width(1.dp).fillMaxHeight().background(SettingsDivider))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 38.dp, vertical = 38.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (section) {
                SettingsSection.Display -> DisplaySettingsContent(fontScale, onFontScale)
                SettingsSection.Cache -> CacheSettingsContent(cacheSize, clearCache)
                SettingsSection.About -> AboutContent(
                    version = "v${BuildConfig.VERSION_NAME} / ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
                    onOpenGithub = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))) } },
                )
            }
        }
    }
}

@Composable
private fun CacheSettingsContent(cacheSize: suspend () -> Long, clearCache: suspend () -> Unit) {
    var size by remember { mutableStateOf<Long?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { size = cacheSize() }
    Text("缓存设置", color = SettingsIndicator, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Text("媒体缩略图和临时文件可随时清理，不影响已保存的 SMB 连接和显示设置。", color = SettingsMutedText, fontSize = 19.sp)
    SettingsActionCard("当前缓存", size?.let(Formatters::size) ?: "正在统计…", onClick = {})
    TvButton("清理缓存", enabled = size != null && size != 0L, onClick = { scope.launch { clearCache(); size = cacheSize() } })
}

@Composable
private fun SettingsMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) SettingsFocus else if (selected) SettingsSurface else Color.Transparent, label = "$text-background")
    val textColor by animateColorAsState(if (focused) SettingsFocusText else SettingsText, label = "$text-color")
    val borderWidth by animateDpAsState(if (focused) 3.dp else 0.dp, label = "$text-border")
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(8.dp)).background(background)
            .border(BorderStroke(borderWidth, if (focused) SettingsFocusBorder else Color.Transparent), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().tvOkClick(onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) SettingsIndicator else Color.Transparent))
        Spacer(Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DisplaySettingsContent(fontScale: Float, onFontScale: (Float) -> Unit) {
    Text("显示设置", color = SettingsIndicator, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("字体缩放", color = SettingsText, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
    Text("调整电视端的信息密度；更改会立即生效。", color = SettingsMutedText, fontSize = 19.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TvButton("−", modifier = Modifier.width(90.dp), enabled = fontScale > 0.8f, onClick = { onFontScale(nextFontScale(fontScale, -0.05f)) })
        Text("${fontScalePercent(fontScale)}%", color = SettingsText, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        TvButton("＋", modifier = Modifier.width(90.dp), enabled = fontScale < 1.2f, onClick = { onFontScale(nextFontScale(fontScale, 0.05f)) })
    }
    TvButton("恢复默认", modifier = Modifier.width(180.dp), onClick = { onFontScale(1f) })
}

@Composable
private fun AboutContent(version: String, onOpenGithub: () -> Unit) {
    Text("关于", color = SettingsIndicator, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    SettingsActionCard(
        title = "项目描述",
        description = "面向 Android TV 与电视遥控器的本地和 SMB / NAS 媒体文件管理器。",
        detail = version,
        onClick = {},
    )
    SettingsActionCard(
        title = "检查更新",
        description = "从 GitHub Release 查找适用于当前设备架构的新版本安装包。",
        onClick = {},
    )
    SettingsActionCard(
        title = "GitHub",
        description = "项目主页 · github.com/gbandszxc/good-tvplorer",
        onClick = onOpenGithub,
    )
}

@Composable
private fun SettingsActionCard(title: String, description: String, detail: String? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) SettingsFocus else SettingsSurface, label = "$title-background")
    val textColor by animateColorAsState(if (focused) SettingsFocusText else SettingsText, label = "$title-text")
    val mutedTextColor by animateColorAsState(if (focused) SettingsFocusText else SettingsMutedText, label = "$title-muted-text")
    val borderWidth by animateDpAsState(if (focused) 3.dp else 1.dp, label = "$title-border")
    Column(
        Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp)).background(background)
            .border(BorderStroke(borderWidth, if (focused) SettingsFocusBorder else SettingsDivider), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().tvOkClick(onClick).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), color = textColor, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, color = mutedTextColor, fontSize = 19.sp) }
        }
        Text(description, color = mutedTextColor, fontSize = 19.sp, maxLines = 2)
    }
}
