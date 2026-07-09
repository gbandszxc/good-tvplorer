package com.goodtvplorer.ui.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.goodtvplorer.viewmodel.BrowserViewMode
import com.goodtvplorer.viewmodel.MainViewModel
import java.io.File

@Composable
fun BrowserScreen(
    path: String,
    state: BrowserState,
    thumbnails: Map<String, File>,
    viewMode: BrowserViewMode,
    onOpen: (FileItem) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleView: () -> Unit,
) {
    var focusedItem by remember(state.items) { mutableStateOf<FileItem?>(state.items.firstOrNull()) }
    val preview = focusedItem ?: state.items.firstOrNull()

    Column(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(28.dp)) {
        TopBar(path, viewMode, onToggleView, onRefresh, onBack)
        Row(Modifier.fillMaxSize().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SourceRail()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    state.loading -> LoadingPanel()
                    state.error != null -> MessagePanel("连接或读取失败", state.error, Color(0xFFFFA3A3))
                    state.items.isEmpty() -> MessagePanel("目录为空", "Back 返回上级，或刷新当前目录。", Color(0xFFC8D5E2))
                    viewMode == BrowserViewMode.List -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.items, key = { it.handle.sourceKey + it.handle.path }) { item ->
                            FileRow(item, thumbnails[MainViewModel.thumbKey(item.handle)], onFocus = { focusedItem = item }, onClick = { onOpen(item) })
                        }
                    }
                    else -> LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.items, key = { it.handle.sourceKey + it.handle.path }) { item ->
                            FileTile(item, thumbnails[MainViewModel.thumbKey(item.handle)], onFocus = { focusedItem = item }, onClick = { onOpen(item) })
                        }
                    }
                }
            }
            PreviewPanel(preview, preview?.let { thumbnails[MainViewModel.thumbKey(it.handle)] })
        }
    }
}

@Composable
private fun TopBar(path: String, viewMode: BrowserViewMode, onToggleView: () -> Unit, onRefresh: () -> Unit, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Good TVplorer", color = Color(0xFFEEF6FB), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(if (path.isBlank()) "媒体入口 / 根目录" else path, color = Color(0xFF9FB0C2), fontSize = 18.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(if (viewMode == BrowserViewMode.Grid) "网格" else "列表", onClick = onToggleView)
            TvButton("刷新", onClick = onRefresh)
            TvButton("返回", onClick = onBack)
        }
    }
}

@Composable
private fun SourceRail() {
    Column(
        Modifier.width(150.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color(0xFF101A26)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("位置", color = Color(0xFF7CC7D8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        listOf("本地", "SMB/NAS", "图片", "音频", "视频").forEach {
            Text(it, color = Color(0xFFD9E5EE), fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("OK 打开\nBack 返回", color = Color(0xFF8191A3), fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun FileRow(item: FileItem, thumbnail: File?, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth(), onFocus, onClick) { focused ->
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MediaThumb(item, thumbnail, Modifier.size(78.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(meta(item), color = if (focused) Color(0xFF4C3B12) else Color(0xFFA8B8C7), fontSize = 17.sp, maxLines = 1)
            }
            Text(kindLabel(item.kind), color = if (focused) Color(0xFF151007) else Color(0xFF7CC7D8), fontSize = 18.sp)
        }
    }
}

@Composable
private fun FileTile(item: FileItem, thumbnail: File?, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth(), onFocus, onClick) { focused ->
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaThumb(item, thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
            Text(item.name, color = if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

@Composable
private fun FocusSurface(modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, content: @Composable (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Color(0xFFFFC857) else Color(0xFF152232), label = "focus-bg")
    val border = if (focused) Color(0xFFFFE3A1) else Color(0xFF24364A)
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(BorderStroke(if (focused) 3.dp else 1.dp, border), RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
private fun PreviewPanel(item: FileItem?, thumbnail: File?) {
    Column(Modifier.width(260.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0xFF101A26)).padding(16.dp)) {
        Text("快速预览", color = Color(0xFF7CC7D8), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        if (item == null) {
            Text("选择文件后显示预览。", color = Color(0xFFA8B8C7), fontSize = 18.sp)
            return@Column
        }
        MediaThumb(item, thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 11f))
        Spacer(Modifier.height(16.dp))
        Text(item.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        Text(kindLabel(item.kind), color = Color(0xFFFFC857), fontSize = 18.sp)
        Text(meta(item), color = Color(0xFFA8B8C7), fontSize = 16.sp)
    }
}

@Composable
private fun MediaThumb(item: FileItem, thumbnail: File?, modifier: Modifier) {
    val model: Any? = thumbnail ?: if (item.kind == FileKind.Image) item.handle.path else null
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF0D1621)), contentAlignment = Alignment.Center) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(icon(item.kind), color = Color(0xFFC9D8E5), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingPanel() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("正在读取目录...", color = Color(0xFFC8D5E2), fontSize = 22.sp)
        }
    }
}

@Composable
private fun MessagePanel(title: String, body: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Color(0xFFC8D5E2), fontSize = 20.sp)
        }
    }
}

private fun meta(item: FileItem): String = listOf(Formatters.size(item.size), Formatters.time(item.modifiedAtMillis)).filter { it.isNotBlank() }.joinToString("  ").ifBlank { " " }

private fun kindLabel(kind: FileKind): String = when (kind) {
    FileKind.Directory -> "文件夹"
    FileKind.Image -> "图片"
    FileKind.Text -> "文本"
    FileKind.Audio -> "音频"
    FileKind.Video -> "视频"
    FileKind.Other -> "文件"
}

private fun icon(kind: FileKind): String = when (kind) {
    FileKind.Directory -> "DIR"
    FileKind.Image -> "IMG"
    FileKind.Text -> "TXT"
    FileKind.Audio -> "AUD"
    FileKind.Video -> "VID"
    FileKind.Other -> "FILE"
}
