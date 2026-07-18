package com.github.gbandszxc.goodtvplorer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    background = Color(0xFF0B121A),
    surface = Color(0xFF152232),
    surfaceVariant = Color(0xFF101A26),
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF151007),
    onBackground = Color(0xFFF3F7FA),
    onSurface = Color(0xFFF3F7FA),
    onSurfaceVariant = Color(0xFFA8B8C7),
    secondary = Color(0xFF7CC7D8),
    error = Color(0xFFFFA3A3),
    onError = Color(0xFF351012),
    outline = Color(0xFF3A4D60),
    outlineVariant = Color(0xFF26384B),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
