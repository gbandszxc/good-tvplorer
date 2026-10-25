package com.github.gbandszxc.goodtvplorer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import kotlinx.coroutines.delay

/** 元信息行图标尺寸（规格要求 18dp，非通用 token）。 */
private val TvMetaIconSize = 18.dp

/** 提示药丸垂直内边距（规格要求 10dp，非通用 token）。 */
private val TvHintVerticalPadding = 10.dp

/** 菜单项高度。 */
private val TvMenuItemHeight = 52.dp

/** 开关胶囊轨道的固定尺寸。 */
private val ToggleTrackWidth = 52.dp
private val ToggleTrackHeight = 30.dp
private val ToggleThumbSize = 22.dp
private val ToggleEdgePadding = 4.dp

/** 加载弧线粗细。 */
private val SpinnerStroke = 3.dp

/** 骨架微光周期。 */
private const val TvSkeletonMs = 1100

/**
 * 自绘开关：胶囊轨道 + 圆点，单一焦点节点（`Role.Checkbox`）。
 * 不使用 Material Checkbox / Switch。
 */
@Composable
fun TvToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val trackWidth = ToggleTrackWidth
    val trackHeight = ToggleTrackHeight
    val thumbSize = ToggleThumbSize
    val edgePadding = ToggleEdgePadding
    val pill = RoundedCornerShape(TvRadius.pill)

    val trackColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) TvColors.Accent else TvColors.SurfaceRaised,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-toggle-track",
    )
    val thumbColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) TvColors.Panel else TvColors.TextSecondary,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-toggle-thumb",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "tv-toggle-ring-width",
    )
    val ringColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-toggle-ring-color",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - edgePadding else edgePadding,
        animationSpec = when {
            !motionEnabled -> tween(durationMillis = 0)
            checked -> TvMotion.expand<Dp>()
            else -> TvMotion.collapse<Dp>()
        },
        label = "tv-toggle-thumb-offset",
    )

    Row(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .onFocusChanged { focused = it.isFocused }
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(pill)
                .background(trackColor)
                .border(ringWidth, ringColor, pill),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .clip(pill)
                    .background(thumbColor),
            )
        }
        Text(
            text = label,
            color = TvColors.TextPrimary,
            style = TvType.label,
            maxLines = 1,
        )
    }
}

/**
 * 自绘输入框：`BasicTextField` + 自定义容器装饰。
 *
 * label 在容器上方；容器 56dp 高、SurfaceSunken 底、1dp Outline 描边、聚焦 2dp FocusRing。
 * `BasicTextField` 自带 SetText 语义，仪器测试可通过 `hasSetTextAction()` 命中。
 *
 * @param modifier 作用在真正获得焦点的 [BasicTextField] 上，
 * 用于 `focusRequester` / `focusProperties` / `onPreviewKeyEvent` / `onFocusChanged`。
 * @param containerModifier 作用在外层 Column 上，用于 `weight` / `width` / `fillMaxWidth` 等布局约束。
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    containerModifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.sm)
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "tv-text-field-border-width",
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.Outline,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-text-field-border-color",
    )

    Column(containerModifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = TvColors.TextSecondary,
                style = TvType.caption,
                maxLines = 1,
            )
            Spacer(Modifier.height(TvSpacing.xs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(TvColors.SurfaceSunken)
                .border(borderWidth, borderColor, shape),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                // 焦点相关修饰符（focusRequester / focusProperties / 按键拦截）必须挂在
                // BasicTextField 自身的焦点链上；fillMaxWidth 只负责铺满容器。
                modifier = modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
                enabled = enabled,
                textStyle = TvType.body.copy(color = TvColors.TextPrimary),
                cursorBrush = SolidColor(TvColors.Accent),
                singleLine = singleLine,
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                decorationBox = { innerTextField ->
                    // 内边距放在装饰层内部，BasicTextField 的语义宽度保持与容器一致。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = TvSpacing.md),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = TvColors.TextMuted,
                                style = TvType.body,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

/** 小圆角标签 chip。 */
@Composable
fun TvChip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val shape = RoundedCornerShape(TvRadius.sm)
    Box(
        modifier = modifier
            .clip(shape)
            .background(TvColors.SurfaceSunken)
            .border(1.dp, TvColors.OutlineVariant, shape)
            .padding(horizontal = TvSpacing.sm, vertical = TvSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (accent) TvColors.Accent else TvColors.TextSecondary,
            style = TvType.caption,
            maxLines = 1,
        )
    }
}

/** 自绘旋转弧线加载指示；系统关闭动画时静止。 */
@Composable
fun TvSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    val angle = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "tv-spinner")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "tv-spinner-angle",
        ).value
    } else {
        0f
    }
    Canvas(modifier.size(size)) {
        val strokeWidth = SpinnerStroke.toPx()
        val diameter = kotlin.math.min(this.size.width, this.size.height)
        val inset = strokeWidth / 2f
        drawArc(
            color = TvColors.Accent,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameter - strokeWidth, diameter - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/** 自绘进度条（替换 Material LinearProgressIndicator）。 */
@Composable
fun TvProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val fraction = progress.coerceIn(0f, 1f)
        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = TvColors.OutlineVariant,
            size = size,
            cornerRadius = cornerRadius,
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = if (focused) TvColors.Focus else TvColors.Accent,
                size = Size(size.width * fraction, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}

/** 圆角骨架块 + 横向微光；系统关闭动画时退化为静态块。 */
@Composable
fun TvSkeleton(modifier: Modifier = Modifier) {
    val motionEnabled = LocalTvMotionEnabled.current
    val phase = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "tv-skeleton")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = TvSkeletonMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "tv-skeleton-phase",
        ).value
    } else {
        0f
    }
    Canvas(modifier) {
        val cornerRadius = CornerRadius(TvRadius.sm.toPx(), TvRadius.sm.toPx())
        if (!motionEnabled || size.width <= 0f) {
            drawRoundRect(
                color = TvColors.SurfaceRaised,
                size = size,
                cornerRadius = cornerRadius,
            )
        } else {
            val sweepStart = (phase * 2f - 1f) * size.width
            val sweepEnd = phase * 2f * size.width
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(TvColors.Surface, TvColors.SurfaceRaised, TvColors.Surface),
                    start = Offset(sweepStart, 0f),
                    end = Offset(sweepEnd, size.height),
                ),
                size = size,
                cornerRadius = cornerRadius,
            )
        }
    }
}

/** 图标 + 元信息文本行，间距 8dp。 */
@Composable
fun TvMetaRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TvColors.TextSecondary,
            modifier = Modifier.size(TvMetaIconSize),
        )
        Text(
            text = text,
            color = TvColors.TextSecondary,
            style = TvType.meta,
            maxLines = 1,
        )
    }
}

/**
 * 轻量提示状态：只承载一条消息，由 [TvHintHost] 负责展示与自动清除。
 *
 * `revision` 在每次 [show] 时自增，使相同文案再次展示时也能重置消失计时。
 */
@Stable
class TvHintState internal constructor() {
    var message: String? by mutableStateOf(null)
        private set

    internal var revision by mutableIntStateOf(0)
        private set

    fun show(message: String) {
        this.message = message
        revision += 1
    }

    internal fun clear() {
        message = null
    }
}

@Composable
fun rememberTvHintState(): TvHintState = remember { TvHintState() }

/**
 * 底部居中提示药丸。进入 180ms 上移 12dp + fade，2.2 秒后自动消失。
 * 不使用 Material Snackbar。
 */
@Composable
fun TvHintHost(
    state: TvHintState,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    val density = LocalDensity.current
    var lastMessage by remember { mutableStateOf<String?>(null) }
    val message = state.message

    LaunchedEffect(message, state.revision) {
        if (message != null) {
            lastMessage = message
            delay(2200)
            state.clear()
        }
    }

    val slidePx = with(density) { 12.dp.roundToPx() }
    val enterMs = if (motionEnabled) TvMotion.scrimMs else 0
    val exitMs = if (motionEnabled) TvMotion.collapseMs else 0
    val pill = RoundedCornerShape(TvRadius.pill)

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(animationSpec = tween(durationMillis = enterMs)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = enterMs),
                    initialOffsetY = { slidePx },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = exitMs)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = exitMs),
                    targetOffsetY = { slidePx },
                ),
        ) {
            Box(
                modifier = Modifier
                    .clip(pill)
                    .background(TvColors.Panel)
                    .border(1.dp, TvColors.OutlineVariant, pill)
                    .padding(horizontal = TvSpacing.lg, vertical = TvHintVerticalPadding),
            ) {
                Text(
                    text = message ?: lastMessage.orEmpty(),
                    color = TvColors.TextPrimary,
                    style = TvType.body,
                )
            }
        }
    }
}

/**
 * 弹窗 / 菜单内的自绘菜单项：52dp 高、聚焦 Focus 填充、选中 Accent。
 */
@Composable
fun TvMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.md)

    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) TvColors.Focus else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-menu-item-background",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            focused -> TvColors.OnFocus
            selected -> TvColors.Accent
            else -> TvColors.TextPrimary
        },
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-menu-item-content",
    )
    val ringWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 0.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "tv-menu-item-ring-width",
    )
    val ringColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else Color.Transparent,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "tv-menu-item-ring-color",
    )

    Row(
        modifier = modifier
            .height(TvMenuItemHeight)
            .clip(shape)
            .background(background)
            .border(ringWidth, ringColor, shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = label
                this.text = AnnotatedString(label)
            }
            .padding(horizontal = TvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(TvSizes.icon),
        )
        Spacer(Modifier.width(TvSpacing.md))
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                // 文本语义由菜单项父节点显式提供，避免合并后 hasText 收到重复文本。
                .clearAndSetSemantics {},
            color = contentColor,
            style = TvType.label,
            maxLines = 1,
        )
        if (!trailing.isNullOrEmpty()) {
            Spacer(Modifier.width(TvSpacing.sm))
            Text(
                text = trailing,
                modifier = Modifier.clearAndSetSemantics {},
                color = if (focused) TvColors.OnFocusMuted else TvColors.TextSecondary,
                style = TvType.meta,
                maxLines = 1,
            )
        }
    }
}
