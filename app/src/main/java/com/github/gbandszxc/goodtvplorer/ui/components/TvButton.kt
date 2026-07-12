package com.github.gbandszxc.goodtvplorer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    fontSize: TextUnit = 20.sp,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isFocused = enabled && focused
    val bg by animateColorAsState(if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "button-bg")
    val fg by animateColorAsState(
        if (enabled) if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        label = "button-fg",
    )
    val border by animateDpAsState(if (isFocused) 3.dp else 1.dp, label = "button-border")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(BorderStroke(border, if (isFocused) Color(0xFFFFE3A1) else Color(0xFF26384B)), RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.onFocusChanged { focused = it.isFocused }.focusable().tvOkClick(onClick) else Modifier)
            .padding(contentPadding)
    ) {
        Text(text, color = fg, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}

fun Modifier.tvOkClick(onClick: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (
        event.type == KeyEventType.KeyUp &&
        (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
    ) {
        onClick()
        true
    } else {
        false
    }
}.clickable(onClick = onClick)
