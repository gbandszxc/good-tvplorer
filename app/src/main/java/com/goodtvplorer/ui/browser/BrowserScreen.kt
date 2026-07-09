package com.goodtvplorer.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.domain.Formatters
import com.goodtvplorer.ui.components.TvButton
import com.goodtvplorer.viewmodel.BrowserState
import com.goodtvplorer.viewmodel.MainViewModel
import java.io.File

@Composable
fun BrowserScreen(path: String, state: BrowserState, thumbnails: Map<String, File>, onOpen: (FileItem) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF101418)).padding(36.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (path.isBlank()) "/" else path, color = Color.White, fontSize = 26.sp, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("刷新", onClick = onRefresh)
                TvButton("返回", onClick = onBack)
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error, color = Color(0xFFFCA5A5), fontSize = 26.sp) }
            state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("目录为空", color = Color(0xFFCBD5E1), fontSize = 26.sp) }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 22.dp)) {
                items(state.items, key = { it.handle.sourceKey + it.handle.path }) { item ->
                    FileRow(item = item, thumbnail = thumbnails[MainViewModel.thumbKey(item.handle)], onClick = { onOpen(item) })
                }
            }
        }
    }
}

@Composable
private fun FileRow(item: FileItem, thumbnail: File?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color(0xFFD6F35F) else Color(0xFF182028)
    val fg = if (focused) Color(0xFF101418) else Color.White
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.Transparent), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (item.kind == FileKind.Image) {
            AsyncImage(model = thumbnail ?: item.handle.path, contentDescription = item.name, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        } else {
            Text(icon(item.kind), fontSize = 36.sp, color = fg, modifier = Modifier.size(64.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.name, color = fg, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(listOf(Formatters.size(item.size), Formatters.time(item.modifiedAtMillis)).filter { it.isNotBlank() }.joinToString("  "), color = fg.copy(alpha = 0.75f), fontSize = 18.sp)
        }
    }
}

private fun icon(kind: FileKind): String = when (kind) {
    FileKind.Directory -> "[D]"
    FileKind.Image -> "[I]"
    FileKind.Text -> "[T]"
    FileKind.Audio -> "[A]"
    FileKind.Other -> "[F]"
}
