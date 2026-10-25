package com.github.gbandszxc.goodtvplorer.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType

/** 菜单面板距屏幕顶部的距离，为 56dp 顶栏留出空间。 */
private val TvMenuTopPadding = 72.dp

/** 面板进入动画时长。 */
private const val PanelEnterMs = 200

/**
 * 统一 TV 弹窗：自绘遮罩 + Panel 面板，无系统 dim 与系统窗口动画。
 *
 * 仅在 [visible] 时组合 [Dialog]；内容通过捕获的已缩放 [androidx.compose.ui.unit.Density]
 * 重新 provide，保证 80%-120% 显示缩放在独立弹窗窗口内同样生效。
 */
@Composable
fun TvDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    width: Dp = 520.dp,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    if (!visible) return
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            TransparentDialogWindow()
            // 该窗口/输入路径下 DialogProperties.dismissOnBackPress 未生效，显式接管返回键。
            BackHandler(enabled = dismissible) { onDismissRequest() }
            DialogPanel(
                title = title,
                modifier = modifier,
                icon = icon,
                width = width,
                content = content,
                actions = actions,
            )
        }
    }
}

/**
 * 统一 TV 菜单：全屏透明 Dialog + 自绘遮罩 + 右上角 Panel 面板。
 *
 * Dialog 窗口天然把焦点限制在菜单内，替代 Material DropdownMenu。
 */
@Composable
fun TvMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 340.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CompositionLocalProvider(LocalDensity provides density) {
            TransparentDialogWindow()
            BackHandler { onDismissRequest() }
            val motionEnabled = LocalTvMotionEnabled.current
            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { entered = true }
            val scrimAlpha by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (motionEnabled) TvMotion.scrimMs else 0,
                    easing = LinearEasing,
                ),
                label = "tv-menu-scrim",
            )
            val panelProgress by animateFloatAsState(
                targetValue = if (entered) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (motionEnabled) PanelEnterMs else 0,
                    easing = TvMotion.expandEasing,
                ),
                label = "tv-menu-panel",
            )
            val shape = RoundedCornerShape(TvRadius.lg)

            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(TvColors.Scrim.copy(alpha = TvColors.Scrim.alpha * scrimAlpha))
                        .pointerInput(Unit) {
                            detectTapGestures { onDismissRequest() }
                        },
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = TvMenuTopPadding, end = TvSpacing.xxl)
                        .width(width)
                        .then(modifier)
                        .graphicsLayer {
                            alpha = panelProgress
                            scaleX = 0.96f + 0.04f * panelProgress
                            scaleY = 0.96f + 0.04f * panelProgress
                        }
                        .clip(shape)
                        .background(TvColors.Panel)
                        .border(1.dp, TvColors.OutlineVariant, shape)
                        .padding(TvSpacing.sm),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DialogPanel(
    title: String,
    modifier: Modifier,
    icon: ImageVector?,
    width: Dp,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val motionEnabled = LocalTvMotionEnabled.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) TvMotion.scrimMs else 0,
            easing = LinearEasing,
        ),
        label = "tv-dialog-scrim",
    )
    val panelProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (motionEnabled) PanelEnterMs else 0,
            easing = TvMotion.expandEasing,
        ),
        label = "tv-dialog-panel",
    )
    val shape = RoundedCornerShape(TvRadius.lg)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .matchParentSize()
                .background(TvColors.Scrim.copy(alpha = TvColors.Scrim.alpha * scrimAlpha)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(width)
                .then(modifier)
                .graphicsLayer {
                    alpha = panelProgress
                    scaleX = 0.96f + 0.04f * panelProgress
                    scaleY = 0.96f + 0.04f * panelProgress
                }
                .clip(shape)
                .background(TvColors.Panel)
                .border(1.dp, TvColors.OutlineVariant, shape)
                .padding(TvSpacing.xxl),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TvColors.Accent,
                        modifier = Modifier.size(TvSizes.iconLarge),
                    )
                }
                Text(
                    text = title,
                    color = TvColors.TextPrimary,
                    style = TvType.sectionTitle,
                )
            }
            Spacer(Modifier.height(TvSpacing.xl))
            content()
            Spacer(Modifier.height(TvSpacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

/**
 * 关闭 Dialog 的系统 dim 与窗口动画，并把窗口背景设为透明，
 * 由组件自己绘制 [TvColors.Scrim]。
 */
@Composable
private fun TransparentDialogWindow() {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.setDimAmount(0f)
        window.setWindowAnimations(0)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        // 自绘 Scrim 需要覆盖整屏，避免窗口按内容 wrap 后遮罩不完整。
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
    }
}
