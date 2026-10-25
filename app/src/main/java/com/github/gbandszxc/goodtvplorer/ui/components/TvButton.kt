package com.github.gbandszxc.goodtvplorer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType

/** 动作按钮的视觉语气。 */
enum class TvActionTone { Neutral, Primary, Destructive, Ghost }

/** 标签显示策略：仅聚焦时展开，或始终显示。 */
enum class TvLabelMode { OnFocus, Always }

/** 选中态底部短指示条宽度。 */
private val ActionSelectedIndicatorWidth = 24.dp
private val RailSelectedIndicatorWidth = 20.dp

/**
 * TV 图标动作按钮。
 *
 * 未聚焦时只显示图标；聚焦时标签以 `TvMotion.expand` 从 0 展开到内在宽度。
 * 禁用时仍可聚焦并完整显示焦点样式，但确认键不触发回调。
 */
@Composable
fun TvActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: TvActionTone = TvActionTone.Neutral,
    labelMode: TvLabelMode = TvLabelMode.OnFocus,
    compact: Boolean = false,
    selected: Boolean = false,
    contentDescription: String? = null,
) {
    TvActionButtonImpl(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tone = tone,
        labelMode = labelMode,
        compact = compact,
        selected = selected,
        contentDescription = contentDescription,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(horizontal = 13.dp),
        labelStyle = TvType.label,
    )
}

@Composable
private fun TvActionButtonImpl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    tone: TvActionTone,
    labelMode: TvLabelMode,
    compact: Boolean,
    selected: Boolean,
    contentDescription: String?,
    contentAlignment: Alignment,
    contentPadding: PaddingValues,
    labelStyle: TextStyle,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var focused by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(TvRadius.md)
    val height = if (compact) TvSizes.actionCompact else TvSizes.action

    val idleBackground = when (tone) {
        TvActionTone.Neutral -> TvColors.Surface
        TvActionTone.Primary -> TvColors.Panel
        TvActionTone.Destructive -> TvColors.Surface
        TvActionTone.Ghost -> Color.Transparent
    }
    val idleContent = when (tone) {
        TvActionTone.Neutral -> TvColors.TextPrimary
        TvActionTone.Primary -> TvColors.Accent
        TvActionTone.Destructive -> TvColors.Error
        TvActionTone.Ghost -> TvColors.TextSecondary
    }
    val idleBorderColor = when (tone) {
        TvActionTone.Neutral -> TvColors.OutlineVariant
        TvActionTone.Destructive -> TvColors.Error.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val idleBorderWidth = when (tone) {
        TvActionTone.Neutral, TvActionTone.Destructive -> 1.dp
        TvActionTone.Primary, TvActionTone.Ghost -> 0.dp
    }
    val focusBackground = if (tone == TvActionTone.Destructive) TvColors.Error else TvColors.Focus
    val focusContent = if (tone == TvActionTone.Destructive) TvColors.OnError else TvColors.OnFocus

    val background by animateColorAsState(
        targetValue = when {
            focused -> focusBackground
            selected -> TvColors.Panel
            else -> idleBackground
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-action-background",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            focused -> focusContent
            selected -> TvColors.Accent
            else -> idleContent
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-action-content",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else idleBorderColor,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-action-border-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else idleBorderWidth,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "tv-action-border-width",
    )

    val contentAlpha = if (enabled) 1f else 0.45f
    val labelExpanded = labelMode == TvLabelMode.Always || focused
    val measuredLabelWidth = remember(textMeasurer, label, labelStyle, density) {
        val layout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = labelStyle,
            maxLines = 1,
            softWrap = false,
        )
        with(density) { layout.size.width.toDp() }
    }
    val labelWidth by animateDpAsState(
        targetValue = if (labelExpanded) measuredLabelWidth + 10.dp else 0.dp,
        animationSpec = when {
            !motionEnabled -> tween(durationMillis = 0)
            labelExpanded -> TvMotion.expand<Dp>()
            else -> TvMotion.collapse<Dp>()
        },
        label = "tv-action-label-width",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (labelExpanded) 1f else 0f,
        animationSpec = when {
            !motionEnabled -> tween(durationMillis = 0)
            labelExpanded -> TvMotion.expand<Float>()
            else -> TvMotion.collapse<Float>()
        },
        label = "tv-action-label-alpha",
    )

    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = TvSizes.action)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription ?: label
                this.text = AnnotatedString(label)
            },
        contentAlignment = contentAlignment,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = contentColor.alpha * contentAlpha),
                modifier = Modifier.size(TvSizes.icon),
            )
            Text(
                text = label,
                modifier = Modifier
                    .width(labelWidth)
                    .padding(start = 10.dp)
                    .clipToBounds()
                    .graphicsLayer { alpha = labelAlpha }
                    // 文本语义由父节点显式提供，避免合并后 hasText 收到重复文本。
                    .clearAndSetSemantics {},
                color = contentColor.copy(alpha = contentColor.alpha * contentAlpha),
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
        if (selected && !focused) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(ActionSelectedIndicatorWidth)
                    .height(2.dp)
                    .background(TvColors.Accent),
            )
        }
    }
}

/**
 * 竖向 Dock 使用的图标动作。
 *
 * 按钮本体固定 48dp 不变宽；标签药丸通过 [androidx.compose.ui.draw.drawBehind]
 * 画到按钮右侧（x = 56dp），不参与布局，因此不会推挤 Dock 或内容区。
 */
@Composable
fun TvRailAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var focused by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(TvRadius.md)
    val background by animateColorAsState(
        targetValue = if (focused) TvColors.Focus else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-rail-background",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            focused -> TvColors.OnFocus
            selected -> TvColors.Accent
            else -> TvColors.TextPrimary
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-rail-content",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 0.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "tv-rail-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-rail-border-color",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = when {
            !motionEnabled -> tween(durationMillis = 0)
            focused -> TvMotion.expand<Float>()
            else -> TvMotion.collapse<Float>()
        },
        label = "tv-rail-label-alpha",
    )

    val labelLayout = remember(textMeasurer, label, density) {
        textMeasurer.measure(
            text = AnnotatedString(label),
            style = TvType.meta,
            maxLines = 1,
            softWrap = false,
        )
    }
    val pillWidth = with(density) { labelLayout.size.width.toDp() } + 20.dp
    val pillHeight = with(density) { labelLayout.size.height.toDp() } + 12.dp

    Box(
        modifier = modifier
            .size(TvSizes.action)
            .zIndex(if (focused) 1f else 0f)
            // 标签药丸绘制在 48dp 本体之外，必须位于 clip 之前，否则会被裁掉。
            .drawBehind {
                if (labelAlpha <= 0.01f) return@drawBehind
                val left = 56.dp.toPx()
                val top = (size.height - pillHeight.toPx()) / 2f
                val width = pillWidth.toPx()
                val height = pillHeight.toPx()
                val radius = TvRadius.md.toPx()
                drawRoundRect(
                    color = TvColors.Panel.copy(alpha = labelAlpha),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius, radius),
                )
                drawRoundRect(
                    color = TvColors.OutlineVariant.copy(alpha = labelAlpha),
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawText(
                    textLayoutResult = labelLayout,
                    color = TvColors.TextPrimary.copy(
                        alpha = labelAlpha * if (enabled) 1f else 0.45f,
                    ),
                    topLeft = Offset(
                        left + 10.dp.toPx(),
                        top + (height - labelLayout.size.height) / 2f,
                    ),
                )
            }
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = label
                this.text = AnnotatedString(label)
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor.copy(alpha = contentColor.alpha * if (enabled) 1f else 0.45f),
            modifier = Modifier.size(TvSizes.iconLarge),
        )
        if (selected && !focused) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .width(RailSelectedIndicatorWidth)
                    .height(2.dp)
                    .background(TvColors.Accent),
            )
        }
    }
}

/**
 * 旧页面兼容壳：保留原文字按钮签名，内部映射到 [TvActionButton]。
 *
 * @Deprecated 新代码请使用 [TvActionButton]；该壳仅用于本次重构过渡期。
 */
@Deprecated("迁移到 TvActionButton")
@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    contentAlignment: Alignment = Alignment.CenterStart,
    contentDescription: String? = null,
    fontSize: TextUnit = 20.sp,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    TvActionButtonImpl(
        icon = TvIcons.Check,
        label = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tone = if (destructive) TvActionTone.Destructive else TvActionTone.Neutral,
        labelMode = TvLabelMode.Always,
        compact = false,
        selected = false,
        contentDescription = contentDescription,
        contentAlignment = contentAlignment,
        contentPadding = contentPadding,
        labelStyle = TvType.label.copy(fontSize = fontSize),
    )
}

fun Modifier.tvOkClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = null,
    onTouchClick: (() -> Unit)? = null,
): Modifier = then(if (enabled) Modifier.onTvOk(onClick) else Modifier)
    .clickable(enabled = enabled, role = role, onClick = onTouchClick ?: onClick)

fun Modifier.tvTabClick(
    selected: Boolean,
    onClick: () -> Unit,
): Modifier = onTvOk(onClick)
    .selectable(selected = selected, role = Role.Tab, onClick = onClick)

/**
 * 无 ripple 的 TV 点击容器：方向键确认走 preview 拦截，触屏走 clickable。
 *
 * 由于需要持有 [MutableInteractionSource] 以关闭 indication，这里声明为
 * `@Composable` 工厂，只能在组合中调用。
 */
@Composable
fun Modifier.tvClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .then(if (enabled) Modifier.onTvOk(onClick) else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

private fun Modifier.onTvOk(onClick: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
    if (event.type == KeyEventType.KeyUp && isOk) {
        onClick()
        true
    } else {
        false
    }
}
