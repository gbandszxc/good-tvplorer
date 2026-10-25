package com.github.gbandszxc.goodtvplorer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.gbandszxc.goodtvplorer.data.FileItem
import com.github.gbandszxc.goodtvplorer.data.FileKind
import com.github.gbandszxc.goodtvplorer.domain.Formatters
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvChip
import com.github.gbandszxc.goodtvplorer.ui.components.TvLabelMode
import com.github.gbandszxc.goodtvplorer.ui.components.TvMenu
import com.github.gbandszxc.goodtvplorer.ui.components.TvMenuItem
import com.github.gbandszxc.goodtvplorer.ui.components.TvMetaRow
import com.github.gbandszxc.goodtvplorer.ui.components.TvSkeleton
import com.github.gbandszxc.goodtvplorer.ui.components.TvSpinner
import com.github.gbandszxc.goodtvplorer.ui.components.tvClickable
import com.github.gbandszxc.goodtvplorer.ui.main.MainFocusNavigation
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvControlShape
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvPanelShape
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserPreviewMetadataState
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserSort
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserSortField
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserState
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserViewMode
import com.github.gbandszxc.goodtvplorer.viewmodel.MainViewModel
import com.github.gbandszxc.goodtvplorer.viewmodel.SortDirection
import com.github.gbandszxc.goodtvplorer.viewmodel.filterAndSortBrowserItems
import java.io.File

private const val NavigateUpFocusKey = "\u0000navigate-up"

/** 搜索框展开宽度：顶栏右侧最大可用宽度。 */
private val SearchExpandedWidth = 280.dp

/** 网格卡片最小宽度，列数按可用宽度计算。 */
private val GridCellMinWidth = 168.dp

/** 网格卡片聚焦缩放与时长。 */
private const val GridFocusScale = 1.03f
private const val GridFocusScaleMs = 180

/** 状态图标尺寸（规格要求 40dp，非通用 token）。 */
private val StateIconSize = 40.dp

/** 缩略图占位图标尺寸。 */
private val ThumbIconSize = 32.dp

@Composable
internal fun BrowserScreen(
    path: String,
    canNavigateUp: Boolean,
    navigation: MainFocusNavigation,
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
    // 首屏由内容抢回焦点：默认焦点可能先落在 Dock 上，不能因此抑制内容自动聚焦。
    var initialFocusConsumed by remember(path) { mutableStateOf(false) }
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
    val defaultContentKey = defaultFocusedPath ?: NavigateUpFocusKey.takeIf { canNavigateUp }
    val contentAvailable = !state.loading && state.error == null && defaultContentKey != null
    val shouldAutoFocusContent = contentAvailable && !initialFocusConsumed && !searchHasFocus
    val emptyDirectoryHint = if (canNavigateUp) "选择返回上一级，或刷新当前目录。" else "刷新当前目录以重新检查文件。"
    val errorMessage = state.error
    SideEffect { navigation.contentAvailable = contentAvailable }
    LaunchedEffect(preview?.handle?.sourceKey, preview?.handle?.path) {
        preview?.let(onPreviewMetadataRequest)
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.lg),
    ) {
        BrowserToolbar(
            path,
            sort,
            searchQuery,
            navigation,
            onOpenPath,
            onSortChange,
            onSearchQueryChange,
            onSearchFocusChange = { searchHasFocus = it },
        )
        Row(
            Modifier.weight(1f).fillMaxWidth().testTag("browser-content"),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    state.loading -> LoadingPanel()
                    errorMessage != null -> ErrorPanel(errorMessage)
                    viewMode == BrowserViewMode.List -> BrowserList(
                        visibleItems = visibleItems,
                        directoryEmpty = state.items.isEmpty(),
                        searchLoading = searchLoading,
                        canNavigateUp = canNavigateUp,
                        navigation = navigation,
                        thumbnails = thumbnails,
                        shouldAutoFocusContent = shouldAutoFocusContent,
                        defaultContentKey = defaultContentKey,
                        emptyDirectoryHint = emptyDirectoryHint,
                        onInitialFocus = { initialFocusConsumed = true },
                        onItemFocused = { focusedItem = it },
                        onOpen = onOpen,
                        onNavigateUp = onNavigateUp,
                        onThumbnailVisible = onThumbnailVisible,
                        onThumbnailHidden = onThumbnailHidden,
                    )
                    else -> BrowserGrid(
                        visibleItems = visibleItems,
                        directoryEmpty = state.items.isEmpty(),
                        searchLoading = searchLoading,
                        canNavigateUp = canNavigateUp,
                        navigation = navigation,
                        thumbnails = thumbnails,
                        shouldAutoFocusContent = shouldAutoFocusContent,
                        defaultContentKey = defaultContentKey,
                        emptyDirectoryHint = emptyDirectoryHint,
                        onInitialFocus = { initialFocusConsumed = true },
                        onItemFocused = { focusedItem = it },
                        onOpen = onOpen,
                        onNavigateUp = onNavigateUp,
                        onThumbnailVisible = onThumbnailVisible,
                        onThumbnailHidden = onThumbnailHidden,
                    )
                }
            }
            // 网格模式释放整行宽度给卡片，快速预览只在列表模式出现。
            if (viewMode == BrowserViewMode.List) {
                PreviewPanel(
                    preview,
                    preview?.let { thumbnails[MainViewModel.thumbKey(it)] },
                    previewMetadata,
                    onThumbnailVisible,
                    onThumbnailHidden,
                )
            }
        }
    }
}

@Composable
private fun BrowserToolbar(
    path: String,
    sort: BrowserSort,
    searchQuery: String,
    navigation: MainFocusNavigation,
    onOpenPath: (String) -> Unit,
    onSortChange: (BrowserSort) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TvSizes.toolbar)
            .testTag("browser-toolbar")
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceActions(navigation)
        SortMenuButton(sort, navigation, onSortChange)
        PathField(
            path = path,
            navigation = navigation,
            onOpenPath = onOpenPath,
            modifier = Modifier.weight(1f),
        )
        SearchField(searchQuery, navigation, onSearchQueryChange, onSearchFocusChange)
    }
}

@Composable
private fun SourceActions(navigation: MainFocusNavigation) {
    // 无内容时来源控件的下行回落到路径，避免焦点停在顶栏。
    val contentDown = if (navigation.contentAvailable) navigation.content else navigation.path
    Row(
        Modifier.height(TvSizes.toolbar).testTag("source-bar"),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvActionButton(
            icon = TvIcons.HardDrive,
            label = "本机",
            onClick = { navigation.onSelectLocalSource() },
            labelMode = TvLabelMode.Always,
            selected = !navigation.networkSelected,
            modifier = Modifier
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = contentDown
                    left = FocusRequester.Cancel
                    right = navigation.networkSource
                }
                .focusRequester(navigation.localSource)
                .onFocusChanged { if (it.isFocused) navigation.focusSource() },
        )
        TvActionButton(
            icon = TvIcons.Server,
            label = "网络",
            onClick = { navigation.onSelectNetworkSource() },
            labelMode = TvLabelMode.Always,
            selected = navigation.networkSelected,
            modifier = Modifier
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = contentDown
                    left = navigation.localSource
                    right = navigation.sort
                }
                .focusRequester(navigation.networkSource)
                .onFocusChanged { if (it.isFocused) navigation.focusSource() },
        )
    }
}

@Composable
private fun PathField(
    path: String,
    navigation: MainFocusNavigation,
    onOpenPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayPath = if (path.isBlank()) "/" else path
    var editing by remember(path) { mutableStateOf(false) }
    var enteredPath by remember(path) { mutableStateOf(TextFieldValue(displayPath)) }
    var restorePathFocus by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val inputFocusRequester = remember { FocusRequester() }
    val motionEnabled = LocalTvMotionEnabled.current

    fun submitPath() {
        editing = false
        restorePathFocus = true
        onOpenPath(enteredPath.text)
    }

    BackHandler(enabled = editing) {
        editing = false
        restorePathFocus = true
    }
    LaunchedEffect(editing, restorePathFocus) {
        when {
            editing -> inputFocusRequester.requestFocus()
            restorePathFocus -> {
                navigation.path.requestFocus()
                restorePathFocus = false
            }
        }
    }

    val borderWidth by animateDpAsState(
        targetValue = if (focused || editing) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "path-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused || editing) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "path-border-color",
    )

    Box(
        modifier = modifier
            .height(TvSizes.action)
            .clip(TvControlShape)
            .background(TvColors.Panel)
            .border(borderWidth, borderColor, TvControlShape)
            .focusProperties {
                up = navigation.selectedSource
                down = navigation.contentTarget
                left = navigation.sort
                right = navigation.search
            }
            .focusRequester(navigation.path)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) navigation.focusToolbar(navigation.path)
            }
            .focusable()
            .tvClickable(
                onClick = {
                    if (editing) {
                        inputFocusRequester.requestFocus()
                    } else {
                        enteredPath = TextFieldValue(displayPath)
                        editing = true
                    }
                },
            )
            .semantics { contentDescription = "编辑路径" }
            .padding(horizontal = TvSpacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            Icon(
                imageVector = TvIcons.Folder,
                contentDescription = null,
                tint = if (focused || editing) TvColors.Focus else TvColors.Accent,
                modifier = Modifier.size(TvSizes.icon),
            )
            if (editing) {
                BasicTextField(
                    value = enteredPath,
                    onValueChange = { enteredPath = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties {
                            up = navigation.selectedSource
                            down = navigation.contentTarget
                        }
                        .focusRequester(inputFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft ->
                                    if (enteredPath.selection.min <= 0) {
                                        navigation.sort.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                Key.DirectionRight ->
                                    if (enteredPath.selection.max >= enteredPath.text.length) {
                                        navigation.search.requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                else -> false
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                submitPath()
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    textStyle = TvType.label.copy(color = TvColors.TextPrimary),
                    cursorBrush = SolidColor(TvColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitPath() }),
                )
            } else {
                Text(
                    text = displayPath,
                    modifier = Modifier.weight(1f),
                    color = TvColors.TextPrimary,
                    style = TvType.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (focused) {
                    Icon(
                        imageVector = TvIcons.Pencil,
                        contentDescription = null,
                        tint = TvColors.Focus,
                        modifier = Modifier.size(TvSizes.icon),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    navigation: MainFocusNavigation,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val motionEnabled = LocalTvMotionEnabled.current
    var queryValue by remember { mutableStateOf(TextFieldValue(query)) }
    LaunchedEffect(query) { if (query != queryValue.text) queryValue = TextFieldValue(query) }
    val expanded = focused || query.isNotEmpty()
    val width by animateDpAsState(
        targetValue = if (expanded) SearchExpandedWidth else TvSizes.action,
        animationSpec = when {
            !motionEnabled -> tween(durationMillis = 0)
            expanded -> TvMotion.expand<Dp>()
            else -> TvMotion.collapse<Dp>()
        },
        label = "search-width",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "search-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "search-border-color",
    )

    BackHandler(enabled = query.isNotEmpty()) {
        queryValue = TextFieldValue("")
        onQueryChange("")
        navigation.path.requestFocus()
    }

    // 折叠态容器内容宽 32dp，22dp 图标以 5dp 起始内边距精确居中；
    // 展开后图标回到左侧，输入框让出 26dp（图标 + 4dp 间距）。
    val iconStart by animateDpAsState(
        targetValue = if (expanded) 0.dp else 5.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "search-icon-start",
    )

    Box(
        Modifier
            .width(width)
            .height(TvSizes.action)
            .clip(TvControlShape)
            .background(TvColors.Panel)
            .border(borderWidth, borderColor, TvControlShape)
            // 触屏点击容器时把焦点交给内部输入框；折叠态下输入框仍有非零宽度。
            .pointerInput(navigation) {
                detectTapGestures { navigation.search.requestFocus() }
            }
            .padding(horizontal = TvSpacing.sm),
    ) {
        BasicTextField(
            value = queryValue,
            onValueChange = {
                queryValue = it
                onQueryChange(it.text)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (expanded) 26.dp else 0.dp)
                .focusProperties {
                    up = navigation.selectedSource
                    down = navigation.contentTarget
                    left = navigation.path
                    right = FocusRequester.Cancel
                }
                .focusRequester(navigation.search)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                    if (it.isFocused) navigation.focusToolbar(navigation.search)
                }
                // 单行输入框会消费方向键，上下行与左边界必须显式转交焦点。
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft ->
                            if (queryValue.selection.min <= 0) {
                                navigation.path.requestFocus()
                                true
                            } else {
                                false
                            }
                        Key.DirectionUp -> {
                            navigation.selectedSource.requestFocus()
                            true
                        }
                        Key.DirectionDown -> {
                            if (navigation.contentAvailable) navigation.content.requestFocus()
                            true
                        }
                        else -> false
                    }
                }
                .semantics { contentDescription = "搜索当前目录" },
            singleLine = true,
            textStyle = TvType.label.copy(color = TvColors.TextPrimary),
            cursorBrush = SolidColor(TvColors.Accent),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank() && expanded) {
                        Text(
                            text = "搜索当前目录",
                            color = TvColors.TextMuted,
                            style = TvType.label,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Icon(
            imageVector = TvIcons.Search,
            contentDescription = null,
            tint = if (focused) TvColors.Focus else TvColors.TextSecondary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = iconStart)
                .size(TvSizes.icon),
        )
    }
}

@Composable
private fun SortMenuButton(sort: BrowserSort, navigation: MainFocusNavigation, onSortChange: (BrowserSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    val selectedOptionFocus = remember { FocusRequester() }
    LaunchedEffect(expanded, restoreFocus) {
        if (!expanded && restoreFocus) {
            navigation.sort.requestFocus()
            restoreFocus = false
        }
    }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        repeat(5) { // 弹窗内容在独立窗口组合，按帧等待挂载；未挂载时 requestFocus 返回 false
            withFrameNanos {}
            if (selectedOptionFocus.requestFocus()) return@LaunchedEffect
        }
    }
    TvActionButton(
        icon = TvIcons.ArrowUpDown,
        label = "排序：${sort.fieldLabel()} ${sort.directionArrow()}",
        onClick = { expanded = true },
        labelMode = TvLabelMode.OnFocus,
        contentDescription = "排序",
        modifier = Modifier
            .focusProperties {
                up = navigation.selectedSource
                down = navigation.contentTarget
                left = navigation.selectedSource
                right = navigation.path
            }
            .focusRequester(navigation.sort)
            .onFocusChanged { if (it.isFocused) navigation.focusToolbar(navigation.sort) },
    )
    TvMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            restoreFocus = true
        },
    ) {
        sortOptions.forEach { option ->
            TvMenuItem(
                icon = option.fieldIcon(),
                label = option.fieldLabel(),
                selected = option == sort,
                trailing = option.directionArrow(),
                onClick = {
                    onSortChange(option)
                    expanded = false
                    restoreFocus = true
                },
                focusRequester = selectedOptionFocus.takeIf { option == sort },
            )
        }
    }
}

@Composable
private fun BrowserList(
    visibleItems: List<FileItem>,
    directoryEmpty: Boolean,
    searchLoading: Boolean,
    canNavigateUp: Boolean,
    navigation: MainFocusNavigation,
    thumbnails: Map<String, File>,
    shouldAutoFocusContent: Boolean,
    defaultContentKey: String?,
    emptyDirectoryHint: String,
    onInitialFocus: () -> Unit,
    onItemFocused: (FileItem) -> Unit,
    onOpen: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
) {
    val totalItems = visibleItems.size + if (canNavigateUp) 1 else 0
    LazyColumn(
        modifier = Modifier
            .testTag("browser-items")
            .focusRequester(navigation.content)
            .focusRestorer()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
    ) {
        if (canNavigateUp) {
            item(key = "navigate-up") {
                NavigateUpRow(
                    focusModifier = contentFocusModifier(
                        up = navigation.path,
                        down = if (totalItems == 1) FocusRequester.Cancel else FocusRequester.Default,
                        left = navigation.dockTarget(browserActionsVisible = true),
                        right = FocusRequester.Cancel,
                        onFocused = navigation::focusContent,
                    ),
                    initiallyFocused = shouldAutoFocusContent && defaultContentKey == NavigateUpFocusKey,
                    onInitialFocus = onInitialFocus,
                    onClick = onNavigateUp,
                )
            }
        }
        when {
            directoryEmpty -> item {
                EmptyState(
                    icon = TvIcons.Folder,
                    title = "目录为空",
                    body = emptyDirectoryHint,
                    modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.75f),
                )
            }
            visibleItems.isEmpty() && searchLoading -> item {
                SearchingState(Modifier.fillParentMaxWidth().fillParentMaxHeight(0.75f))
            }
            visibleItems.isEmpty() -> item {
                EmptyState(
                    icon = TvIcons.Search,
                    title = "未找到匹配项目",
                    body = "尝试修改搜索词。",
                    modifier = Modifier.fillParentMaxWidth().fillParentMaxHeight(0.75f),
                )
            }
            else -> itemsIndexed(visibleItems, key = { _, item -> item.handle.sourceKey + item.handle.path }) { index, item ->
                val position = index + if (canNavigateUp) 1 else 0
                FileRow(
                    item,
                    thumbnails[MainViewModel.thumbKey(item)],
                    onThumbnailVisible,
                    onThumbnailHidden,
                    focusModifier = contentFocusModifier(
                        up = if (position == 0) navigation.path else FocusRequester.Default,
                        down = if (position == totalItems - 1) FocusRequester.Cancel else FocusRequester.Default,
                        left = navigation.dockTarget(browserActionsVisible = true),
                        right = FocusRequester.Cancel,
                        onFocused = navigation::focusContent,
                    ),
                    initiallyFocused = shouldAutoFocusContent && defaultContentKey == item.handle.path,
                    onInitialFocus = onInitialFocus,
                    onFocus = { onItemFocused(item) },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun BrowserGrid(
    visibleItems: List<FileItem>,
    directoryEmpty: Boolean,
    searchLoading: Boolean,
    canNavigateUp: Boolean,
    navigation: MainFocusNavigation,
    thumbnails: Map<String, File>,
    shouldAutoFocusContent: Boolean,
    defaultContentKey: String?,
    emptyDirectoryHint: String,
    onInitialFocus: () -> Unit,
    onItemFocused: (FileItem) -> Unit,
    onOpen: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = (maxWidth / GridCellMinWidth).toInt().coerceIn(3, 6)
        val totalItems = visibleItems.size + if (canNavigateUp) 1 else 0
        val lastRowStart = if (totalItems == 0) 0 else ((totalItems - 1) / columns) * columns
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .testTag("browser-items")
                .focusRequester(navigation.content)
                .focusRestorer()
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            if (canNavigateUp) {
                item(key = "navigate-up") {
                    NavigateUpTile(
                        focusModifier = contentFocusModifier(
                            up = navigation.path,
                            down = if (lastRowStart == 0) FocusRequester.Cancel else FocusRequester.Default,
                            left = navigation.dockTarget(browserActionsVisible = true),
                            right = if (totalItems == 1) FocusRequester.Cancel else FocusRequester.Default,
                            onFocused = navigation::focusContent,
                        ),
                        initiallyFocused = shouldAutoFocusContent && defaultContentKey == NavigateUpFocusKey,
                        onInitialFocus = onInitialFocus,
                        onClick = onNavigateUp,
                    )
                }
            }
            when {
                directoryEmpty -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = TvIcons.Folder,
                        title = "目录为空",
                        body = emptyDirectoryHint,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
                visibleItems.isEmpty() && searchLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchingState(Modifier.fillMaxWidth().height(240.dp))
                }
                visibleItems.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = TvIcons.Search,
                        title = "未找到匹配项目",
                        body = "尝试修改搜索词。",
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                    )
                }
                else -> itemsIndexed(visibleItems, key = { _, item -> item.handle.sourceKey + item.handle.path }) { index, item ->
                    val position = index + if (canNavigateUp) 1 else 0
                    FileTile(
                        item,
                        thumbnails[MainViewModel.thumbKey(item)],
                        onThumbnailVisible,
                        onThumbnailHidden,
                        focusModifier = contentFocusModifier(
                            up = if (position < columns) navigation.path else FocusRequester.Default,
                            down = if (position >= lastRowStart) FocusRequester.Cancel else FocusRequester.Default,
                            left = if (position % columns == 0) navigation.dockTarget(browserActionsVisible = true) else FocusRequester.Default,
                            right = if (position % columns == columns - 1 || position == totalItems - 1) FocusRequester.Cancel else FocusRequester.Default,
                            onFocused = navigation::focusContent,
                        ),
                        initiallyFocused = shouldAutoFocusContent && defaultContentKey == item.handle.path,
                        onInitialFocus = onInitialFocus,
                        onFocus = { onItemFocused(item) },
                        onClick = { onOpen(item) },
                    )
                }
            }
        }
    }
}

private fun contentFocusModifier(
    up: FocusRequester,
    down: FocusRequester,
    left: FocusRequester,
    right: FocusRequester,
    onFocused: () -> Unit,
): Modifier = Modifier
    .focusProperties {
        this.up = up
        this.down = down
        this.left = left
        this.right = right
    }
    .onFocusChanged { if (it.isFocused) onFocused() }

@Composable
private fun FileRow(
    item: FileItem,
    thumbnail: File?,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
    focusModifier: Modifier,
    initiallyFocused: Boolean,
    onInitialFocus: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    FocusSurface(
        Modifier.fillMaxWidth().then(focusModifier),
        initiallyFocused,
        onInitialFocus,
        onFocus,
        onClick,
        touchFocusFirst = true,
    ) { focused ->
        Row(
            Modifier
                .heightIn(min = TvSizes.rowMin)
                .padding(TvSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
        ) {
            MediaThumb(item, thumbnail, Modifier.size(TvSizes.action), onThumbnailVisible, onThumbnailHidden)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
            ) {
                Text(
                    text = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                    color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                    style = TvType.rowTitle,
                    maxLines = 1,
                    overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metaEntries(item).forEach { entry ->
                        Text(
                            text = entry,
                            color = if (focused) TvColors.OnFocusMuted else TvColors.TextSecondary,
                            style = TvType.meta,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigateUpRow(focusModifier: Modifier, initiallyFocused: Boolean, onInitialFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth().then(focusModifier), initiallyFocused, onInitialFocus, onFocus = {}, onClick = onClick) { focused ->
        Row(
            Modifier
                .heightIn(min = TvSizes.rowMin)
                .padding(TvSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
        ) {
            Box(Modifier.size(TvSizes.action), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TvIcons.FolderUp,
                    contentDescription = null,
                    tint = if (focused) TvColors.OnFocus else TvColors.Accent,
                    modifier = Modifier.size(TvSizes.iconLarge),
                )
            }
            Text(
                text = "返回上一级",
                color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                style = TvType.rowTitle,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NavigateUpTile(focusModifier: Modifier, initiallyFocused: Boolean, onInitialFocus: () -> Unit, onClick: () -> Unit) {
    FocusSurface(
        Modifier.fillMaxWidth().then(focusModifier),
        initiallyFocused,
        onInitialFocus,
        onFocus = {},
        onClick = onClick,
        scaleOnFocus = true,
    ) { focused ->
        Column(
            Modifier.padding(TvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(TvRadius.sm))
                    .background(if (focused) TvColors.Focus else TvColors.SurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TvIcons.FolderUp,
                    contentDescription = null,
                    tint = if (focused) TvColors.OnFocus else TvColors.Accent,
                    modifier = Modifier.size(ThumbIconSize),
                )
            }
            Text(
                text = "返回上一级",
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                style = TvType.gridTitle,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FileTile(
    item: FileItem,
    thumbnail: File?,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
    focusModifier: Modifier,
    initiallyFocused: Boolean,
    onInitialFocus: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    FocusSurface(
        Modifier.fillMaxWidth().then(focusModifier),
        initiallyFocused,
        onInitialFocus,
        onFocus,
        onClick,
        touchFocusFirst = true,
        scaleOnFocus = true,
    ) { focused ->
        Column(
            Modifier.padding(TvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
        ) {
            MediaThumb(
                item,
                thumbnail,
                Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                onThumbnailVisible,
                onThumbnailHidden,
                focused = focused,
            )
            Text(
                text = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                style = TvType.gridTitle,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            Text(
                text = gridMeta(item),
                color = if (focused) TvColors.OnFocusMuted else TvColors.TextSecondary,
                style = TvType.meta,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FocusSurface(
    modifier: Modifier,
    initiallyFocused: Boolean,
    onInitialFocus: () -> Unit,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    touchFocusFirst: Boolean = false,
    scaleOnFocus: Boolean = false,
    content: @Composable (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val motionEnabled = LocalTvMotionEnabled.current
    val background by animateColorAsState(
        targetValue = if (focused) TvColors.Focus else TvColors.Surface,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "focus-surface-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "focus-surface-border-color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 1.dp,
        animationSpec = if (motionEnabled) TvMotion.color<Dp>() else tween(durationMillis = 0),
        label = "focus-surface-border-width",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused && scaleOnFocus) GridFocusScale else 1f,
        animationSpec = if (motionEnabled) TvMotion.expand<Float>(GridFocusScaleMs) else tween(durationMillis = 0),
        label = "focus-surface-scale",
    )
    LaunchedEffect(initiallyFocused) {
        if (!initiallyFocused) return@LaunchedEffect
        // 列表项在首帧可能尚未完成布局，逐帧重试直到焦点真正落地。
        repeat(10) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                onInitialFocus()
                return@LaunchedEffect
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(TvControlShape)
            .background(background)
            .border(borderWidth, borderColor, TvControlShape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusRequester(focusRequester)
            .focusable()
            .tvClickable(
                onClick = onClick,
                onTouchClick = {
                    if (touchFocusFirst && !focused) focusRequester.requestFocus() else onClick()
                },
            ),
    ) {
        content(focused)
    }
}

@Composable
private fun PreviewPanel(item: FileItem?, thumbnail: File?, metadata: BrowserPreviewMetadataState, onThumbnailVisible: (FileItem) -> Unit, onThumbnailHidden: (FileItem) -> Unit) {
    Column(
        Modifier
            .width(TvSizes.previewPanel)
            .fillMaxHeight()
            .testTag("preview-panel")
            .clip(TvPanelShape)
            .background(TvColors.Panel)
            .padding(TvSpacing.lg),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("快速预览", color = TvColors.Accent, style = TvType.label)
            item?.let { TvChip(kindLabel(it.kind), accent = true) }
        }
        Spacer(Modifier.height(TvSpacing.md))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("preview-content"),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            if (item == null) {
                Text("选择文件后显示预览。", color = TvColors.TextSecondary, style = TvType.body)
                return@Column
            }
            MediaThumb(item, thumbnail, Modifier.fillMaxWidth().aspectRatio(16f / 10f), onThumbnailVisible, onThumbnailHidden)
            Text(item.name, color = TvColors.TextPrimary, style = TvType.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val size = Formatters.size(item.size)
            if (size.isNotBlank()) TvMetaRow(TvIcons.Database, size)
            val time = Formatters.time(item.modifiedAtMillis)
            if (time.isNotBlank()) TvMetaRow(TvIcons.Timer, time)
            TvMetaRow(TvIcons.FileText, kindLabel(item.kind))
            val metadataMatchesItem = metadata.itemKey == MainViewModel.thumbKey(item)
            val textSnippet = metadata.textSnippet
            when {
                metadataMatchesItem && metadata.loading -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
                ) {
                    TvSpinner(size = TvSizes.icon)
                    Text("正在读取媒体信息…", color = TvColors.TextMuted, style = TvType.caption)
                }
                metadataMatchesItem && textSnippet != null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(TvRadius.md))
                        .background(TvColors.SurfaceSunken)
                        .padding(TvSpacing.md),
                ) {
                    Text(
                        text = textSnippet,
                        color = TvColors.TextSecondary,
                        style = TvType.meta,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                metadataMatchesItem && metadata.metadata.entries.isNotEmpty() -> metadata.metadata.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm)) {
                        Text(
                            text = entry.label,
                            modifier = Modifier.width(52.dp),
                            color = TvColors.Accent,
                            style = TvType.meta,
                            maxLines = 1,
                        )
                        Text(
                            text = entry.value,
                            modifier = Modifier.weight(1f),
                            color = TvColors.TextSecondary,
                            style = TvType.meta,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaThumb(
    item: FileItem,
    thumbnail: File?,
    modifier: Modifier,
    onThumbnailVisible: (FileItem) -> Unit,
    onThumbnailHidden: (FileItem) -> Unit,
    focused: Boolean = false,
) {
    DisposableEffect(item, thumbnail) {
        val requested = item.kind in setOf(FileKind.Image, FileKind.Audio, FileKind.Video) && thumbnail == null
        if (requested) onThumbnailVisible(item)
        onDispose { if (requested) onThumbnailHidden(item) }
    }
    val model: Any? = thumbnail
    Box(
        modifier
            .clip(RoundedCornerShape(TvRadius.sm))
            // 网格卡片聚焦时让缩略图井与卡片同色，避免深色井吞掉深色图标。
            .background(if (focused) TvColors.Focus else TvColors.SurfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                imageVector = fileKindIcon(item.kind),
                contentDescription = kindLabel(item.kind),
                tint = if (focused) TvColors.OnFocus else TvColors.TextSecondary,
                modifier = Modifier.size(ThumbIconSize),
            )
        }
    }
}

@Composable
private fun LoadingPanel() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
    ) {
        repeat(6) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TvSizes.rowMin)
                    .padding(TvSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
            ) {
                TvSkeleton(Modifier.size(TvSizes.action))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
                ) {
                    TvSkeleton(Modifier.fillMaxWidth(0.6f).height(18.dp))
                    TvSkeleton(Modifier.fillMaxWidth(0.35f).height(14.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "正在读取目录…",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = TvColors.TextMuted,
            style = TvType.caption,
        )
        Spacer(Modifier.height(TvSpacing.md))
    }
}

@Composable
private fun ErrorPanel(cause: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(TvSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            Icon(
                imageVector = TvIcons.AlertTriangle,
                contentDescription = null,
                tint = TvColors.Error,
                modifier = Modifier.size(StateIconSize),
            )
            Text("连接或读取失败", color = TvColors.Error, style = TvType.sectionTitle)
            Text(cause, color = TvColors.TextPrimary, style = TvType.body)
            Text("使用左侧刷新按钮重试", color = TvColors.TextMuted, style = TvType.caption)
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvColors.Accent,
                modifier = Modifier.size(StateIconSize),
            )
            Text(title, color = TvColors.TextPrimary, style = TvType.sectionTitle)
            Text(body, color = TvColors.TextSecondary, style = TvType.body)
        }
    }
}

@Composable
private fun SearchingState(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            TvSpinner(size = TvSizes.iconLarge)
            Text("正在递归搜索", color = TvColors.TextPrimary, style = TvType.sectionTitle)
            Text("正在检索当前目录及其子目录。", color = TvColors.TextSecondary, style = TvType.body)
        }
    }
}

private val sortOptions = BrowserSortField.entries.flatMap { field ->
    SortDirection.entries.map { direction -> BrowserSort(field, direction) }
}

private fun BrowserSort.fieldLabel(): String = when (field) {
    BrowserSortField.Name -> "文件名"
    BrowserSortField.Size -> "文件大小"
    BrowserSortField.ModifiedTime -> "最后修改时间"
}

private fun BrowserSort.directionArrow(): String = if (direction == SortDirection.Ascending) "↑" else "↓"

private fun BrowserSort.fieldIcon(): ImageVector = when (field) {
    BrowserSortField.Name -> TvIcons.FileText
    BrowserSortField.Size -> TvIcons.Database
    BrowserSortField.ModifiedTime -> TvIcons.Timer
}

private fun metaEntries(item: FileItem): List<String> = buildList {
    if (item.kind == FileKind.Directory) {
        add(kindLabel(item.kind))
        Formatters.time(item.modifiedAtMillis).takeIf { it.isNotBlank() }?.let { add(it) }
    } else {
        Formatters.size(item.size).takeIf { it.isNotBlank() }?.let { add(it) }
        Formatters.time(item.modifiedAtMillis).takeIf { it.isNotBlank() }?.let { add(it) }
        add(kindLabel(item.kind))
    }
}

private fun gridMeta(item: FileItem): String = Formatters.size(item.size).ifBlank { kindLabel(item.kind) }

private fun kindLabel(kind: FileKind): String = when (kind) {
    FileKind.Directory -> "文件夹"
    FileKind.Image -> "图片"
    FileKind.Text -> "文本"
    FileKind.Audio -> "音频"
    FileKind.Video -> "视频"
    FileKind.Other -> "文件"
}

/** 每种文件类型对应的 Lucide 图标，列表/网格缩略图占位共用。 */
internal fun fileKindIcon(kind: FileKind): ImageVector = when (kind) {
    FileKind.Directory -> TvIcons.Folder
    FileKind.Image -> TvIcons.Image
    FileKind.Text -> TvIcons.FileText
    FileKind.Audio -> TvIcons.Disc
    FileKind.Video -> TvIcons.Film
    FileKind.Other -> TvIcons.File
}
