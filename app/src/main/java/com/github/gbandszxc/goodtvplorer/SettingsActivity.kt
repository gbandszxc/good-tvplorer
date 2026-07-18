package com.github.gbandszxc.goodtvplorer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.data.fontScalePercent
import com.github.gbandszxc.goodtvplorer.data.nextFontScale
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import com.github.gbandszxc.goodtvplorer.domain.CacheRepository
import com.github.gbandszxc.goodtvplorer.domain.Formatters
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.components.tvOkClick
import com.github.gbandszxc.goodtvplorer.ui.components.tvTabClick
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import kotlinx.coroutines.launch

private val SettingsSidebar = Color(0xFF0E1824)
private val SettingsDivider = Color(0xFF26384B)
private val SettingsFocusBorder = Color(0xFFFFE3A1)
private val SettingsMutedText = Color(0xFFA8B8C7)

private enum class SettingsSection(val title: String) {
    Display("显示设置"),
    Cache("缓存设置"),
    About("关于"),
}

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
private fun SettingsScreen(
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    cacheSize: suspend () -> Long,
    clearCache: suspend () -> Unit,
    onClose: () -> Unit,
) {
    var section by remember { mutableStateOf(SettingsSection.Display) }
    val firstMenuFocus = remember { FocusRequester() }
    val decreaseFontFocus = remember { FocusRequester() }
    val increaseFontFocus = remember { FocusRequester() }
    val aboutActionFocus = remember { FocusRequester() }
    val percentage = fontScalePercent(fontScale)
    val context = LocalContext.current
    val githubUrl = "https://github.com/gbandszxc/good-tvplorer"

    LaunchedEffect(Unit) { firstMenuFocus.requestFocus() }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .width((260 * fontScale).dp)
                .fillMaxHeight()
                .background(SettingsSidebar)
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            Text(
                text = "设置",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsMenuItem(
                    text = SettingsSection.Display.title,
                    selected = section == SettingsSection.Display,
                    modifier = Modifier.focusRequester(firstMenuFocus),
                    rightFocus = if (percentage > 80) decreaseFontFocus else increaseFontFocus,
                    onClick = { section = SettingsSection.Display },
                )
                SettingsMenuItem(SettingsSection.Cache.title, selected = section == SettingsSection.Cache) {
                    section = SettingsSection.Cache
                }
                SettingsMenuItem(
                    text = SettingsSection.About.title,
                    selected = section == SettingsSection.About,
                    rightFocus = aboutActionFocus,
                    onClick = { section = SettingsSection.About },
                )
            }
            Spacer(Modifier.weight(1f))
            SettingsMenuItem("返回", selected = false, onClick = onClose)
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(SettingsDivider))

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 36.dp, vertical = 32.dp),
        ) {
            Text(
                text = section.title,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            key(section) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (section) {
                        SettingsSection.Display -> DisplaySettingsContent(
                            fontScale = fontScale,
                            decreaseFontFocus = decreaseFontFocus,
                            increaseFontFocus = increaseFontFocus,
                            onFontScale = onFontScale,
                        )
                        SettingsSection.Cache -> CacheSettingsContent(cacheSize, clearCache)
                        SettingsSection.About -> AboutContent(
                            version = "v${BuildConfig.VERSION_NAME} / ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
                            firstActionFocus = aboutActionFocus,
                            onCheckUpdate = {
                                Toast.makeText(context, "检查更新功能开发中", Toast.LENGTH_SHORT).show()
                            },
                            onOpenGithub = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, githubUrl.toUri()))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    rightFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        animationSpec = tween(160),
        label = "$text-background",
    )
    val textColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(160),
        label = "$text-color",
    )
    val borderWidth by animateDpAsState(
        if (focused) 3.dp else 0.dp,
        animationSpec = tween(160),
        label = "$text-border",
    )
    val indicator = when {
        focused && selected -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                BorderStroke(borderWidth, if (focused) SettingsFocusBorder else Color.Transparent),
                RoundedCornerShape(8.dp),
            )
            .then(if (rightFocus == null) Modifier else Modifier.focusProperties { right = rightFocus })
            .onFocusChanged { focused = it.isFocused }
            .tvTabClick(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(indicator))
        Spacer(Modifier.width(16.dp))
        Text(text, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DisplaySettingsContent(
    fontScale: Float,
    decreaseFontFocus: FocusRequester,
    increaseFontFocus: FocusRequester,
    onFontScale: (Float) -> Unit,
) {
    val percentage = fontScalePercent(fontScale)

    SettingsGroup {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = "字体缩放",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "调整电视端的信息密度；100% 为应用标准字号，更改会立即生效。",
                color = SettingsMutedText,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvButton(
                    text = "−",
                    modifier = Modifier.width(64.dp).focusRequester(decreaseFontFocus),
                    enabled = percentage > 80,
                    contentPadding = PaddingValues(0.dp),
                    contentAlignment = Alignment.Center,
                    contentDescription = "减小字体",
                    onClick = { onFontScale(nextFontScale(fontScale, -0.05f)) },
                )
                Box(
                    Modifier
                        .width(112.dp)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "当前字体缩放 $percentage%" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$percentage%",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TvButton(
                    text = "＋",
                    modifier = Modifier.width(64.dp).focusRequester(increaseFontFocus),
                    enabled = percentage < 120,
                    contentPadding = PaddingValues(0.dp),
                    contentAlignment = Alignment.Center,
                    contentDescription = "增大字体",
                    onClick = { onFontScale(nextFontScale(fontScale, 0.05f)) },
                )
                TvButton(
                    text = "恢复默认",
                    modifier = Modifier.width(128.dp),
                    contentAlignment = Alignment.Center,
                    onClick = { onFontScale(1f) },
                )
            }
        }
    }
}

@Composable
private fun CacheSettingsContent(cacheSize: suspend () -> Long, clearCache: suspend () -> Unit) {
    var size by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { size = cacheSize() }

    SettingsGroup {
        SettingsRow(
            title = "当前缓存",
            description = "媒体缩略图与临时文件",
            detail = size?.let(Formatters::size) ?: "正在统计…",
        )
        SettingsGroupDivider()
        SettingsRow(
            title = "清理缓存",
            description = "不影响已保存的 SMB 连接和显示设置。",
            detail = "清理",
            enabled = size != null && size != 0L,
            onClick = {
                scope.launch {
                    clearCache()
                    size = cacheSize()
                }
            },
        )
    }
}

@Composable
private fun AboutContent(
    version: String,
    firstActionFocus: FocusRequester,
    onCheckUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    SettingsGroup {
        SettingsRow(
            title = "项目信息",
            description = "本地与 SMB / NAS 媒体文件管理器",
            detail = version,
        )
        SettingsGroupDivider()
        SettingsRow(
            title = "检查更新",
            description = "功能开发中，暂不检查或下载更新。",
            detail = "功能开发中",
            modifier = Modifier.focusRequester(firstActionFocus),
            onClick = onCheckUpdate,
        )
        SettingsGroupDivider()
        SettingsRow(
            title = "GitHub",
            description = "github.com/gbandszxc/good-tvplorer",
            detail = "打开",
            onClick = onOpenGithub,
        )
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .focusGroup()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, SettingsDivider), RoundedCornerShape(8.dp))
            .padding(4.dp),
        content = content,
    )
}

@Composable
private fun SettingsGroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(SettingsDivider),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val interactive = onClick != null
    val active = interactive && enabled
    val isFocused = active && focused
    val background by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(160),
        label = "$title-background",
    )
    val titleColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.onPrimary
            active || !interactive -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        },
        animationSpec = tween(160),
        label = "$title-color",
    )
    val supportingColor by animateColorAsState(
        when {
            isFocused -> MaterialTheme.colorScheme.onPrimary
            active || !interactive -> SettingsMutedText
            else -> SettingsMutedText.copy(alpha = 0.45f)
        },
        animationSpec = tween(160),
        label = "$title-supporting-color",
    )
    val borderWidth by animateDpAsState(
        if (isFocused) 3.dp else 0.dp,
        animationSpec = tween(160),
        label = "$title-border",
    )
    val interactionModifier = when {
        !interactive -> Modifier
        active -> Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvOkClick(onClick = onClick, role = Role.Button)
        else -> Modifier.tvOkClick(onClick = onClick, enabled = false, role = Role.Button)
    }

    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(
                BorderStroke(borderWidth, if (isFocused) SettingsFocusBorder else Color.Transparent),
                RoundedCornerShape(6.dp),
            )
            .then(interactionModifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).alignByBaseline(),
                color = titleColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            detail?.let {
                Text(
                    text = it,
                    modifier = Modifier.alignByBaseline(),
                    color = supportingColor,
                    fontSize = 18.sp,
                )
            }
        }
        Text(text = description, color = supportingColor, fontSize = 18.sp)
    }
}
