package com.goodtvplorer.ui.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.goodtvplorer.R
import com.goodtvplorer.data.FileItem
import com.goodtvplorer.data.FileKind
import com.goodtvplorer.domain.Formatters
import com.goodtvplorer.ui.components.TvButton
import com.goodtvplorer.ui.components.tvOkClick
import com.goodtvplorer.viewmodel.BrowserState
import com.goodtvplorer.viewmodel.BrowserPreviewMetadataState
import com.goodtvplorer.viewmodel.BrowserSort
import com.goodtvplorer.viewmodel.BrowserSortField
import com.goodtvplorer.viewmodel.BrowserViewMode
import com.goodtvplorer.viewmodel.MainViewModel
import com.goodtvplorer.viewmodel.SortDirection
import com.goodtvplorer.viewmodel.filterAndSortBrowserItems
import java.io.File

@Composable
fun BrowserScreen(
    path: String,
    state: BrowserState,
    thumbnails: Map<String, File>,
    viewMode: BrowserViewMode,
    sort: BrowserSort,
    searchQuery: String,
    searchItems: List<FileItem>?,
    searchLoading: Boolean,
    previewMetadata: BrowserPreviewMetadataState,
    focusAnchorPath: String?,
    onOpen: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onOpenPath: (String) -> Unit,
    onSortChange: (BrowserSort) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPreviewMetadataRequest: (FileItem) -> Unit,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
) {
    var searchHasFocus by remember { mutableStateOf(false) }
    var suppressContentAnchorFocus by remember(path) { mutableStateOf(false) }
    val searchableItems = searchItems ?: state.items
    val visibleItems = remember(searchableItems, searchQuery, sort) {
        filterAndSortBrowserItems(searchableItems, searchQuery, sort)
    }
    val defaultFocusedPath = remember(visibleItems, focusAnchorPath) {
        visibleItems.firstOrNull { it.handle.path == focusAnchorPath }?.handle?.path
            ?: visibleItems.firstOrNull()?.handle?.path
    }
    var focusedItem by remember(visibleItems, defaultFocusedPath) {
        mutableStateOf(visibleItems.firstOrNull { it.handle.path == defaultFocusedPath })
    }
    val preview = focusedItem ?: visibleItems.firstOrNull()
    val focusNavigateUp = !state.loading && state.error == null && state.items.isEmpty() && !searchHasFocus
    LaunchedEffect(preview?.handle?.sourceKey, preview?.handle?.path) {
        preview?.let(onPreviewMetadataRequest)
    }

    Column(Modifier.fillMaxSize()) {
        BrowserToolbar(
            path,
            sort,
            searchQuery,
            onOpenPath,
            onSortChange,
            onSearchQueryChange,
            onSearchFocusChange = { searchHasFocus = it },
            onSearchMoveDown = { suppressContentAnchorFocus = true },
        )
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    state.loading -> LoadingPanel()
                    state.error != null -> MessagePanel("连接或读取失败", state.error, Color(0xFFFFA3A3))
                    viewMode == BrowserViewMode.List -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "navigate-up") {
                            NavigateUpRow(initiallyFocused = focusNavigateUp, onClick = onNavigateUp)
                        }
                        when {
                            state.items.isEmpty() -> item { InlineMessage("目录为空", "选择返回上一级，或刷新当前目录。") }
                            visibleItems.isEmpty() && searchLoading -> item { InlineMessage("正在递归搜索", "正在检索当前目录及其子目录。") }
                            visibleItems.isEmpty() -> item { InlineMessage("未找到匹配项目", "尝试修改搜索词。") }
                            else -> items(visibleItems, key = { it.handle.sourceKey + it.handle.path }) { item ->
                                FileRow(item, thumbnails[MainViewModel.thumbKey(item)], onThumbnailVisible, onThumbnailHidden, initiallyFocused = !searchHasFocus && !suppressContentAnchorFocus && item.handle.path == defaultFocusedPath, onFocus = { focusedItem = item }, onClick = { onOpen(item) })
                            }
                        }
                    }
                    else -> LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item(key = "navigate-up") {
                            NavigateUpTile(initiallyFocused = focusNavigateUp, onClick = onNavigateUp)
                        }
                        when {
                            state.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { InlineMessage("目录为空", "选择返回上一级，或刷新当前目录。") }
                            visibleItems.isEmpty() && searchLoading -> item(span = { GridItemSpan(maxLineSpan) }) { InlineMessage("正在递归搜索", "正在检索当前目录及其子目录。") }
                            visibleItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { InlineMessage("未找到匹配项目", "尝试修改搜索词。") }
                            else -> items(visibleItems, key = { it.handle.sourceKey + it.handle.path }) { item ->
                                FileTile(item, thumbnails[MainViewModel.thumbKey(item)], onThumbnailVisible, onThumbnailHidden, initiallyFocused = !searchHasFocus && !suppressContentAnchorFocus && item.handle.path == defaultFocusedPath, onFocus = { focusedItem = item }, onClick = { onOpen(item) })
                            }
                        }
                    }
                }
            }
            PreviewPanel(preview, preview?.let { thumbnails[MainViewModel.thumbKey(it)] }, previewMetadata, onThumbnailVisible, onThumbnailHidden)
        }
    }
}

@Composable
private fun BrowserToolbar(
    path: String,
    sort: BrowserSort,
    searchQuery: String,
    onOpenPath: (String) -> Unit,
    onSortChange: (BrowserSort) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onSearchMoveDown: () -> Unit,
) {
    var editing by remember(path) { mutableStateOf(false) }
    var enteredPath by remember(path) { mutableStateOf(path) }
    val inputFocusRequester = remember { FocusRequester() }
    val displayPath = if (path.isBlank()) "/" else path

    fun submitPath() {
        editing = false
        onOpenPath(enteredPath)
    }

    BackHandler(enabled = editing || searchQuery.isNotBlank()) {
        if (editing) editing = false else onSearchQueryChange("")
    }
    LaunchedEffect(editing) {
        if (editing) inputFocusRequester.requestFocus()
    }

    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortMenuButton(sort, onSortChange)
        Row(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color(0xFF101A26)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PathEditButton(onClick = {
                enteredPath = path
                editing = true
            })
            Spacer(Modifier.width(12.dp))
            if (editing) {
                BasicTextField(
                    value = enteredPath,
                    onValueChange = { enteredPath = it },
                    modifier = Modifier.weight(1f).focusRequester(inputFocusRequester).onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyUp && it.key == Key.Enter) {
                            submitPath()
                            true
                        } else false
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFF3F7FA), fontSize = 20.sp),
                    cursorBrush = SolidColor(Color(0xFFC8D5E2)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitPath() }),
                )
            } else {
                Text(displayPath, modifier = Modifier.weight(1f), color = Color(0xFF9FB0C2), fontSize = 20.sp, maxLines = 1)
            }
        }
        SearchField(searchQuery, onSearchQueryChange, onSearchFocusChange, onSearchMoveDown)
    }
}

@Composable
private fun SortMenuButton(sort: BrowserSort, onSortChange: (BrowserSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarIconButton(R.drawable.ic_sort, "排序", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF152232))) {
            sortOptions.forEach { option ->
                val selected = option == sort
                DropdownMenuItem(
                    text = {
                        Row(Modifier.width(196.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(option.iconRes()), contentDescription = null, tint = if (selected) Color(0xFFFFC857) else Color(0xFFA8B8C7), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(option.fieldLabel(), modifier = Modifier.weight(1f), color = if (selected) Color(0xFFFFC857) else Color(0xFFF3F7FA), fontSize = 18.sp)
                            Text(option.directionArrow(), color = if (selected) Color(0xFFFFC857) else Color(0xFFA8B8C7), fontSize = 22.sp)
                        }
                    },
                    onClick = { onSortChange(option); expanded = false },
                    modifier = Modifier.semantics { contentDescription = option.label() },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onMoveDown: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val border = if (focused) Color(0xFFFFE3A1) else Color.Transparent
    Row(
        Modifier.width(240.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Color(0xFF101A26))
            .border(BorderStroke(if (focused) 3.dp else 1.dp, border), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = if (focused) Color(0xFFFFC857) else Color(0xFFA8B8C7), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }.onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (it.key) {
                    Key.DirectionUp -> {
                        focusManager.moveFocus(FocusDirection.Up)
                        true
                    }
                    Key.DirectionDown -> {
                        onMoveDown()
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    else -> false
                }
            }.semantics { contentDescription = "搜索当前目录" },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFF3F7FA), fontSize = 18.sp),
            cursorBrush = SolidColor(Color(0xFFC8D5E2)),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) Text("搜索当前目录", color = Color(0xFF728397), fontSize = 18.sp, maxLines = 1)
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ToolbarIconButton(iconRes: Int, description: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color(0xFFFFC857) else Color(0xFF101A26), label = "$description-background")
    val tint by animateColorAsState(if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), label = "$description-tint")
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(background)
            .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color.Transparent), RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().tvOkClick(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(iconRes), contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private val sortOptions = BrowserSortField.entries.flatMap { field ->
    SortDirection.entries.map { direction -> BrowserSort(field, direction) }
}

private fun BrowserSort.label(): String {
    return "${fieldLabel()} ${if (direction == SortDirection.Ascending) "正序" else "倒序"}"
}

private fun BrowserSort.fieldLabel(): String {
    val fieldLabel = when (field) {
        BrowserSortField.Name -> "文件名"
        BrowserSortField.Size -> "文件大小"
        BrowserSortField.ModifiedTime -> "最后修改时间"
    }
    return fieldLabel
}

private fun BrowserSort.directionArrow(): String = if (direction == SortDirection.Ascending) "↑" else "↓"

private fun BrowserSort.iconRes(): Int = when (field) {
    BrowserSortField.Name -> R.drawable.ic_sort_name
    BrowserSortField.Size -> R.drawable.ic_sort_size
    BrowserSortField.ModifiedTime -> R.drawable.ic_sort_time
}

@Composable
private fun PathEditButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color(0xFFFFC857) else Color.Transparent, label = "path-edit-background")
    val tint by animateColorAsState(if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), label = "path-edit-tint")
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(background)
            .border(BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color.Transparent), RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().tvOkClick(onClick)
            .semantics { contentDescription = "编辑路径" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_edit), contentDescription = "编辑路径", tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FileRow(item: FileItem, thumbnail: File?, onThumbnailVisible: (FileItem) -> Unit, onThumbnailHidden: (FileItem) -> Unit, initiallyFocused: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth(), initiallyFocused, onFocus, onClick) { focused ->
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaThumb(item, thumbnail, Modifier.size(48.dp), onThumbnailVisible, onThumbnailHidden)
            Column(Modifier.weight(1f)) {
                Text(item.name, color = if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(meta(item), color = if (focused) Color(0xFF4C3B12) else Color(0xFFA8B8C7), fontSize = 16.sp, maxLines = 1)
            }
            Text(kindLabel(item.kind), color = if (focused) Color(0xFF151007) else Color(0xFF7CC7D8), fontSize = 16.sp)
        }
    }
}

@Composable
private fun NavigateUpRow(initiallyFocused: Boolean, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth(), initiallyFocused, onFocus = {}, onClick = onClick) { focused ->
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = null, tint = if (focused) Color(0xFF151007) else Color(0xFF7CC7D8), modifier = Modifier.size(48.dp))
            Text("返回上一级", color = if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NavigateUpTile(initiallyFocused: Boolean, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth(), initiallyFocused, onFocus = {}, onClick = onClick) { focused ->
        GridTileContent(focused, "返回上一级") { modifier ->
            Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (focused) Color(0xFFE7B94F) else Color(0xFF0D1621)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = null, tint = if (focused) Color(0xFF151007) else Color(0xFF7CC7D8), modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun InlineMessage(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Color(0xFFC8D5E2), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(body, color = Color(0xFFA8B8C7), fontSize = 18.sp)
    }
}

@Composable
private fun FileTile(item: FileItem, thumbnail: File?, onThumbnailVisible: (FileItem) -> Unit, onThumbnailHidden: (FileItem) -> Unit, initiallyFocused: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth(), initiallyFocused, onFocus, onClick) { focused ->
        GridTileContent(focused, item.name) { modifier ->
            MediaThumb(item, thumbnail, modifier, onThumbnailVisible, onThumbnailHidden)
        }
    }
}

@Composable
private fun GridTileContent(focused: Boolean, title: String, media: @Composable (Modifier) -> Unit) {
    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        media(Modifier.fillMaxWidth().aspectRatio(16f / 10f))
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                title,
                modifier = Modifier.fillMaxWidth().then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                color = if (focused) Color(0xFF151007) else Color(0xFFF3F7FA),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FocusSurface(modifier: Modifier, initiallyFocused: Boolean, onFocus: () -> Unit, onClick: () -> Unit, content: @Composable (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val bg by animateColorAsState(if (focused) Color(0xFFFFC857) else Color(0xFF152232), label = "focus-bg")
    val border = if (focused) Color(0xFFFFE3A1) else Color(0xFF24364A)
    LaunchedEffect(initiallyFocused) {
        if (initiallyFocused) focusRequester.requestFocus()
    }
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(BorderStroke(if (focused) 3.dp else 1.dp, border), RoundedCornerShape(10.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRequester(focusRequester)
            .focusable()
            .tvOkClick(onClick),
    ) {
        content(focused)
    }
}

@Composable
private fun PreviewPanel(item: FileItem?, thumbnail: File?, metadata: BrowserPreviewMetadataState, onThumbnailVisible: (FileItem) -> Unit, onThumbnailHidden: (FileItem) -> Unit) {
    Column(Modifier.width(260.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0xFF101A26)).padding(16.dp)) {
        Text("快速预览", color = Color(0xFF7CC7D8), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        if (item == null) {
            Text("选择文件后显示预览。", color = Color(0xFFA8B8C7), fontSize = 18.sp)
            return@Column
        }
        MediaThumb(item, thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 11f), onThumbnailVisible, onThumbnailHidden)
        Spacer(Modifier.height(16.dp))
        Text(item.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
        Spacer(Modifier.height(8.dp))
        Text(kindLabel(item.kind), color = Color(0xFFFFC857), fontSize = 18.sp)
        Text(meta(item), color = Color(0xFFA8B8C7), fontSize = 16.sp)
        val metadataMatchesItem = metadata.itemKey == MainViewModel.thumbKey(item)
        if (metadataMatchesItem && metadata.loading) {
            Spacer(Modifier.height(12.dp))
            Text("正在读取媒体信息…", color = Color(0xFF728397), fontSize = 16.sp)
        } else if (metadataMatchesItem && metadata.metadata.entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            metadata.metadata.entries.forEach { entry ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.label, modifier = Modifier.width(52.dp), color = Color(0xFF7CC7D8), fontSize = 16.sp)
                    Text(entry.value, modifier = Modifier.weight(1f), color = Color(0xFFC8D5E2), fontSize = 16.sp, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(item: FileItem, thumbnail: File?, modifier: Modifier, onThumbnailVisible: (FileItem) -> Unit, onThumbnailHidden: (FileItem) -> Unit) {
    DisposableEffect(item, thumbnail) {
        val requested = item.kind == FileKind.Image && thumbnail == null
        if (requested) onThumbnailVisible(item)
        onDispose { if (requested) onThumbnailHidden(item) }
    }
    val model: Any? = thumbnail
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
