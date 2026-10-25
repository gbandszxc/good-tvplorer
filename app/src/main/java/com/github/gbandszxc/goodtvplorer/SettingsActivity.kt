package com.github.gbandszxc.goodtvplorer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.data.fontScalePercent
import com.github.gbandszxc.goodtvplorer.data.nextFontScale
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import com.github.gbandszxc.goodtvplorer.domain.CacheRepository
import com.github.gbandszxc.goodtvplorer.domain.Formatters
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvHintHost
import com.github.gbandszxc.goodtvplorer.ui.components.rememberTvHintState
import com.github.gbandszxc.goodtvplorer.ui.components.tvClickable
import com.github.gbandszxc.goodtvplorer.ui.components.tvTabClick
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import kotlinx.coroutines.launch

/** 侧栏宽度（规格要求 240dp，非通用 token）。 */
private val SettingsSidebarWidth = 240.dp

/** 侧栏/设置行最小高度（规格要求 84dp）。 */
private val SettingsRowMinHeight = 84.dp

/** 菜单项高度（规格要求 64dp）。 */
private val SettingsMenuItemHeight = 64.dp

/** 设置行文字样式：18sp 正文，说明文字使用常规字重。 */
private val SettingsDescriptionStyle = TvType.label.copy(fontWeight = FontWeight.Normal)

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
                val displayScale = fontScale.coerceIn(0.8f, 1.2f)
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density * displayScale,
                        fontScale = effectiveFontScale(displayScale) / displayScale,
                    ),
                ) {
                    BackHandler { finish() }
                    SettingsScreen(
                        fontScale = displayScale,
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
    val displayMenuFocus = remember { FocusRequester() }
    val cacheMenuFocus = remember { FocusRequester() }
    val aboutMenuFocus = remember { FocusRequester() }
    val backMenuFocus = remember { FocusRequester() }
    val decreaseFontFocus = remember { FocusRequester() }
    val increaseFontFocus = remember { FocusRequester() }
    val aboutActionFocus = remember { FocusRequester() }
    val cacheContentFocus = remember { FocusRequester() }
    val percentage = fontScalePercent(fontScale)
    // 侧栏菜单右行统一指向当前 section 内容区的首个可操作控件：
    // 显式目标与内容区在 key(section) 内同帧挂载，不再有指向未附加 requester 的哑火死角。
    val sectionContentFocus = when (section) {
        SettingsSection.Display -> if (percentage > 80) decreaseFontFocus else increaseFontFocus
        SettingsSection.Cache -> cacheContentFocus
        SettingsSection.About -> aboutActionFocus
    }
    val context = LocalContext.current
    val githubUrl = "https://github.com/gbandszxc/good-tvplorer"
    val hint = rememberTvHintState()

    LaunchedEffect(Unit) { displayMenuFocus.requestFocus() }

    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(SettingsSidebarWidth)
                    .fillMaxHeight()
                    .background(TvColors.Panel)
                    .padding(horizontal = TvSpacing.xl, vertical = TvSpacing.xxxl),
            ) {
                Text(
                    text = "设置",
                    color = TvColors.TextPrimary,
                    style = TvType.pageTitle,
                )
                Spacer(Modifier.height(TvSpacing.xxl))
                Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.sm)) {
                    SettingsMenuItem(
                        icon = TvIcons.Monitor,
                        text = SettingsSection.Display.title,
                        selected = section == SettingsSection.Display,
                        focusRequester = displayMenuFocus,
                        rightFocus = sectionContentFocus,
                        onClick = { section = SettingsSection.Display },
                    )
                    SettingsMenuItem(
                        icon = TvIcons.Database,
                        text = SettingsSection.Cache.title,
                        selected = section == SettingsSection.Cache,
                        focusRequester = cacheMenuFocus,
                        rightFocus = sectionContentFocus,
                        onClick = { section = SettingsSection.Cache },
                    )
                    SettingsMenuItem(
                        icon = TvIcons.Info,
                        text = SettingsSection.About.title,
                        selected = section == SettingsSection.About,
                        focusRequester = aboutMenuFocus,
                        rightFocus = sectionContentFocus,
                        onClick = { section = SettingsSection.About },
                    )
                }
                Spacer(Modifier.weight(1f))
                SettingsMenuItem(
                    icon = TvIcons.ArrowLeft,
                    text = "返回",
                    selected = false,
                    focusRequester = backMenuFocus,
                    onClick = onClose,
                )
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(TvColors.OutlineVariant))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 36.dp, vertical = TvSpacing.xxxl),
            ) {
                Text(
                    text = section.title,
                    color = TvColors.Accent,
                    style = TvType.pageTitle,
                )
                Spacer(Modifier.height(TvSpacing.xxl))
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
                                menuFocus = displayMenuFocus,
                                decreaseFontFocus = decreaseFontFocus,
                                increaseFontFocus = increaseFontFocus,
                                onFontScale = onFontScale,
                            )
                            SettingsSection.Cache -> CacheSettingsContent(
                                cacheSize = cacheSize,
                                clearCache = clearCache,
                                menuFocus = cacheMenuFocus,
                                firstActionFocus = cacheContentFocus,
                            )
                            SettingsSection.About -> AboutContent(
                                version = "v${BuildConfig.VERSION_NAME} / ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}",
                                menuFocus = aboutMenuFocus,
                                firstActionFocus = aboutActionFocus,
                                onCheckUpdate = { hint.show("检查更新功能开发中") },
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
        TvHintHost(
            state = hint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .zIndex(10f),
        )
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    rightFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.md)
    val background by animateColorAsState(
        targetValue = when {
            focused -> TvColors.Focus
            selected -> TvColors.Surface
            else -> Color.Transparent
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$text-background",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            focused -> TvColors.OnFocus
            selected -> TvColors.Accent
            else -> TvColors.TextPrimary
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$text-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 0.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "$text-border",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$text-border-color",
    )

    Row(
        modifier
            .fillMaxWidth()
            .height(SettingsMenuItemHeight)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .then(if (rightFocus == null) Modifier else Modifier.focusProperties { right = rightFocus })
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvTabClick(
                selected = selected,
                onClick = {
                    focusRequester.requestFocus()
                    onClick()
                },
            )
            .padding(horizontal = TvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(TvSizes.icon),
        )
        // 选中态在图标与文字之间显示小圆点；槽位常驻，文字起始位置不随选中/聚焦变化。
        Spacer(Modifier.width(TvSpacing.sm))
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected && focused -> TvColors.OnFocus
                        selected -> TvColors.Accent
                        else -> Color.Transparent
                    },
                ),
        )
        Spacer(Modifier.width(TvSpacing.sm))
        Text(
            text = text,
            color = contentColor,
            style = TvType.sectionTitle,
            maxLines = 1,
        )
    }
}

@Composable
private fun DisplaySettingsContent(
    fontScale: Float,
    menuFocus: FocusRequester,
    decreaseFontFocus: FocusRequester,
    increaseFontFocus: FocusRequester,
    onFontScale: (Float) -> Unit,
) {
    val percentage = fontScalePercent(fontScale)

    SettingsGroup {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRowMinHeight)
                .padding(horizontal = TvSpacing.xl, vertical = 14.dp),
        ) {
            Text(
                text = "界面缩放",
                color = TvColors.TextPrimary,
                style = TvType.sectionTitle,
            )
            Text(
                text = "调整电视端的信息密度；尺寸和文字同步变化，100% 为应用标准大小，更改会立即生效。",
                color = TvColors.TextSecondary,
                style = SettingsDescriptionStyle,
            )
            Spacer(Modifier.height(TvSpacing.lg))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
            ) {
                TvActionButton(
                    icon = TvIcons.Minus,
                    label = "缩小界面",
                    onClick = { onFontScale(nextFontScale(fontScale, -0.05f)) },
                    modifier = Modifier
                        .focusRequester(decreaseFontFocus)
                        .focusProperties { left = menuFocus },
                    enabled = percentage > 80,
                )
                Box(
                    Modifier
                        .width(112.dp)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "当前界面缩放 $percentage%" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$percentage%",
                        color = TvColors.TextPrimary,
                        style = TvType.sectionTitle.copy(fontSize = 28.sp),
                        maxLines = 1,
                    )
                }
                TvActionButton(
                    icon = TvIcons.Plus,
                    label = "放大界面",
                    onClick = { onFontScale(nextFontScale(fontScale, 0.05f)) },
                    modifier = Modifier.focusRequester(increaseFontFocus),
                    enabled = percentage < 120,
                )
                TvActionButton(
                    icon = TvIcons.RefreshCw,
                    label = "恢复默认",
                    onClick = { onFontScale(1f) },
                    modifier = Modifier.focusProperties { right = FocusRequester.Cancel },
                )
            }
        }
    }
}

@Composable
private fun CacheSettingsContent(
    cacheSize: suspend () -> Long,
    clearCache: suspend () -> Unit,
    menuFocus: FocusRequester,
    firstActionFocus: FocusRequester,
) {
    var size by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { size = cacheSize() }

    SettingsGroup {
        SettingsRow(
            icon = TvIcons.Database,
            title = "当前缓存",
            description = "媒体缩略图与临时文件",
            detail = size?.let(Formatters::size) ?: "正在统计…",
        )
        SettingsGroupDivider()
        SettingsRow(
            icon = TvIcons.Trash2,
            title = "清理缓存",
            description = "不影响已保存的 SMB 连接和显示设置。",
            detail = "清理",
            modifier = Modifier.focusRequester(firstActionFocus)
                .focusProperties { left = menuFocus },
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
    menuFocus: FocusRequester,
    firstActionFocus: FocusRequester,
    onCheckUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    SettingsGroup {
        SettingsRow(
            icon = TvIcons.Info,
            title = "项目信息",
            description = "本地与 SMB / NAS 媒体文件管理器",
            detail = version,
        )
        SettingsGroupDivider()
        SettingsRow(
            icon = TvIcons.RefreshCw,
            title = "检查更新",
            description = "功能开发中，暂不检查或下载更新。",
            detail = "功能开发中",
            modifier = Modifier.focusRequester(firstActionFocus)
                .focusProperties { left = menuFocus },
            onClick = onCheckUpdate,
        )
        SettingsGroupDivider()
        SettingsRow(
            icon = TvIcons.Github,
            title = "GitHub",
            description = "github.com/gbandszxc/good-tvplorer",
            detail = "打开",
            modifier = Modifier.focusProperties { left = menuFocus },
            onClick = onOpenGithub,
        )
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(TvRadius.md)
    Column(
        modifier
            .fillMaxWidth()
            .focusGroup()
            .clip(shape)
            .background(TvColors.Surface)
            .border(1.dp, TvColors.OutlineVariant, shape)
            .padding(TvSpacing.xs),
        content = content,
    )
}

@Composable
private fun SettingsGroupDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TvSpacing.xl)
            .height(1.dp)
            .background(TvColors.OutlineVariant),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    val interactive = onClick != null
    val active = interactive && enabled
    val isFocused = interactive && focused
    val shape = RoundedCornerShape(TvRadius.sm)
    val disabledAlpha = 0.45f

    val background by animateColorAsState(
        targetValue = if (isFocused) TvColors.Focus else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$title-background",
    )
    val titleColor by animateColorAsState(
        targetValue = when {
            isFocused -> TvColors.OnFocus
            interactive && !enabled -> TvColors.TextPrimary.copy(alpha = disabledAlpha)
            else -> TvColors.TextPrimary
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$title-color",
    )
    val supportingColor by animateColorAsState(
        targetValue = when {
            isFocused -> TvColors.OnFocusMuted
            interactive && !enabled -> TvColors.TextSecondary.copy(alpha = disabledAlpha)
            else -> TvColors.TextSecondary
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$title-supporting-color",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> TvColors.OnFocus
            interactive && !enabled -> TvColors.TextSecondary.copy(alpha = disabledAlpha)
            interactive -> TvColors.Accent
            else -> TvColors.TextSecondary
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$title-icon-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 0.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "$title-border",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) TvColors.FocusRing else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "$title-border-color",
    )
    // 操作行禁用时保持可聚焦并完整显示焦点样式，确认是否生效交由 tvClickable 的 enabled 决定。
    val interactionModifier = when {
        !interactive -> Modifier
        else -> Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvClickable(enabled = active, role = Role.Button, onClick = { onClick?.invoke() })
    }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SettingsRowMinHeight)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .then(interactionModifier)
            .padding(horizontal = TvSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(TvSizes.icon),
        )
        Spacer(Modifier.width(TvSpacing.lg))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = titleColor,
                    style = TvType.sectionTitle,
                    maxLines = 1,
                )
                detail?.let {
                    Spacer(Modifier.width(TvSpacing.md))
                    Text(
                        text = it,
                        color = supportingColor,
                        style = TvType.label,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = description,
                color = supportingColor,
                style = SettingsDescriptionStyle,
            )
        }
    }
}
