package com.github.gbandszxc.goodtvplorer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TV 端统一色彩 token。所有页面必须从这里取色，禁止硬编码颜色值。
 *
 * 层级语义：Background（页面底） < SurfaceSunken（凹陷内容） < Surface（静止控件）
 * < SurfaceRaised（次级/hover 容器） < Panel（Dock、顶栏、侧栏、弹窗面板）。
 */
object TvColors {
    val Background = Color(0xFF0B121A)
    val Panel = Color(0xFF101A26)
    val Surface = Color(0xFF152232)
    val SurfaceRaised = Color(0xFF1B2B3D)
    val SurfaceSunken = Color(0xFF0D1621)

    val Focus = Color(0xFFFFC857)
    val FocusRing = Color(0xFFFFE3A1)
    val OnFocus = Color(0xFF151007)
    val OnFocusMuted = Color(0xFF4C3B12)

    val TextPrimary = Color(0xFFF3F7FA)
    val TextSecondary = Color(0xFFA8B8C7)
    val TextMuted = Color(0xFF728397)
    val Accent = Color(0xFF7CC7D8)

    val Error = Color(0xFFFFA3A3)
    val OnError = Color(0xFF351012)

    val Outline = Color(0xFF3A4D60)
    val OutlineVariant = Color(0xFF26384B)
    val Scrim = Color(0xD9050A0F)
}

/** 间距 token，单位 dp。 */
object TvSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp
}

/** 圆角 token，单位 dp。 */
object TvRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val pill = 100.dp
}

/** 共享形状，避免各页面重复构造。 */
val TvPanelShape = RoundedCornerShape(TvRadius.lg)
val TvControlShape = RoundedCornerShape(TvRadius.md)
val TvChipShape = RoundedCornerShape(TvRadius.sm)

/** 字体角色 token。颜色由调用处决定，便于随焦点态继承。 */
object TvType {
    val pageTitle = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    )
    val sectionTitle = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val rowTitle = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val gridTitle = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val body = TextStyle(
        fontSize = 20.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Normal,
    )
    val label = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val meta = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    )
    val caption = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    )
}

/** 关键尺寸 token，单位 dp。 */
object TvSizes {
    val action = 48.dp
    val actionCompact = 40.dp
    val toolbar = 56.dp
    val controlBar = 64.dp
    val rowMin = 60.dp
    val dock = 72.dp
    val previewPanel = 280.dp
    val icon = 22.dp
    val iconLarge = 24.dp
}

/** 统一动效规格：只表达状态变化，禁止 bounce/elastic。 */
object TvMotion {
    /** 展开曲线（easeOutExpo 风格）。 */
    val expandEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** 收起曲线。 */
    val collapseEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** 焦点颜色/描边过渡曲线。 */
    val colorEasing: Easing = LinearOutSlowInEasing

    const val expandMs = 220
    const val collapseMs = 140
    const val colorMs = 120
    const val scrimMs = 180
    const val crossfadeMs = 140

    fun <T> expand(durationMs: Int = expandMs): TweenSpec<T> =
        tween(durationMillis = durationMs, easing = expandEasing)

    fun <T> collapse(durationMs: Int = collapseMs): TweenSpec<T> =
        tween(durationMillis = durationMs, easing = collapseEasing)

    fun <T> color(durationMs: Int = colorMs): TweenSpec<T> =
        tween(durationMillis = durationMs, easing = colorEasing)
}
