package com.github.gbandszxc.goodtvplorer.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** 全局动效开关：系统关闭动画（或测试强制）时为 false，所有时长应归零。 */
val LocalTvMotionEnabled = staticCompositionLocalOf { true }

private val TvColorScheme = darkColorScheme(
    primary = TvColors.Focus,
    onPrimary = TvColors.OnFocus,
    secondary = TvColors.Accent,
    background = TvColors.Background,
    onBackground = TvColors.TextPrimary,
    surface = TvColors.Surface,
    onSurface = TvColors.TextPrimary,
    surfaceVariant = TvColors.Panel,
    onSurfaceVariant = TvColors.TextSecondary,
    error = TvColors.Error,
    onError = TvColors.OnError,
    outline = TvColors.Outline,
    outlineVariant = TvColors.OutlineVariant,
)

/**
 * 读取系统 `ANIMATOR_DURATION_SCALE`，为 0 时视为全局关闭动画，并用
 * [ContentObserver] 跟随系统设置实时变化。开发者选项切换后无需重启页面。
 */
@Composable
fun rememberTvMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) {
        mutableStateOf(resolver.animatorDurationScaleEnabled())
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = resolver.animatorDurationScaleEnabled()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private fun ContentResolver.animatorDurationScaleEnabled(): Boolean =
    runCatching {
        Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f) > 0f

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    val motionEnabled = rememberTvMotionEnabled()
    CompositionLocalProvider(LocalTvMotionEnabled provides motionEnabled) {
        MaterialTheme(colorScheme = TvColorScheme, content = content)
    }
}
