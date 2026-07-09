package com.goodtvplorer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    background = Color(0xFF0B121A),
    surface = Color(0xFF152232),
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF151007),
    onBackground = Color(0xFFF3F7FA),
    onSurface = Color(0xFFF3F7FA),
    secondary = Color(0xFF7CC7D8),
    error = Color(0xFFFFA3A3),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
