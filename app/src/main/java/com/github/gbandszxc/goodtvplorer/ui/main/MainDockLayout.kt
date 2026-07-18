package com.github.gbandszxc.goodtvplorer.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.gbandszxc.goodtvplorer.R
import com.github.gbandszxc.goodtvplorer.data.SmbConnectionInfo
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.components.tvOkClick
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserViewMode

private enum class MainFocusRegion { Source, Toolbar, Content }

@Stable
internal class MainFocusNavigation {
    val localSource = FocusRequester()
    val networkSource = FocusRequester()
    val sort = FocusRequester()
    val path = FocusRequester()
    val search = FocusRequester()
    val content = FocusRequester()
    val view = FocusRequester()
    val refresh = FocusRequester()
    val back = FocusRequester()
    val connections = FocusRequester()
    val settings = FocusRequester()

    var networkSelected by mutableStateOf(false)
    var contentAvailable by mutableStateOf(false)
    var contentInitialFocusAllowed by mutableStateOf(true)
        private set
    private var lastMainRegion by mutableStateOf(MainFocusRegion.Content)
    private var lastToolbar by mutableStateOf(path)
    private var lastDock by mutableStateOf(view)

    val selectedSource: FocusRequester
        get() = if (networkSelected) networkSource else localSource

    val toolbarTarget: FocusRequester
        get() = lastToolbar

    val contentTarget: FocusRequester
        get() = if (contentAvailable) content else FocusRequester.Cancel

    fun dockTarget(browserActionsVisible: Boolean): FocusRequester =
        if (!browserActionsVisible && lastDock !== connections && lastDock !== settings) connections else lastDock

    fun mainTarget(showNetworkHub: Boolean): FocusRequester = when (lastMainRegion) {
        MainFocusRegion.Source -> selectedSource
        MainFocusRegion.Toolbar -> if (showNetworkHub) selectedSource else toolbarTarget
        MainFocusRegion.Content -> when {
            contentAvailable -> content
            showNetworkHub -> selectedSource
            else -> toolbarTarget
        }
    }

    fun focusSource() {
        contentInitialFocusAllowed = false
        lastMainRegion = MainFocusRegion.Source
    }

    fun focusToolbar(target: FocusRequester) {
        contentInitialFocusAllowed = false
        lastMainRegion = MainFocusRegion.Toolbar
        lastToolbar = target
    }

    fun focusContent() {
        contentInitialFocusAllowed = true
        lastMainRegion = MainFocusRegion.Content
    }

    fun focusDock(target: FocusRequester, focused: Boolean) {
        if (focused) {
            contentInitialFocusAllowed = false
            lastDock = target
        }
    }
}

@Composable
internal fun MainDockLayout(
    networkSelected: Boolean,
    showNetworkHub: Boolean,
    connections: List<SmbConnectionInfo>,
    onLocal: () -> Unit,
    onNetwork: () -> Unit,
    onOpenSmb: (String) -> Unit,
    onConnections: () -> Unit,
    onSettings: () -> Unit,
    browserViewMode: BrowserViewMode?,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    displayScale: Float = 1f,
    content: @Composable (MainFocusNavigation) -> Unit,
) {
    val navigation = remember { MainFocusNavigation() }
    val layoutScale = displayScale.coerceIn(0.8f, 1.2f)
    val layoutSpacing = 16.dp * layoutScale
    SideEffect {
        navigation.networkSelected = networkSelected
        if (showNetworkHub) navigation.contentAvailable = true
    }
    Row(
        Modifier.fillMaxSize().testTag("main-layout").background(Color(0xFF0B121A)),
    ) {
        SideDock(
            onConnections,
            onSettings,
            browserViewMode,
            onToggleView,
            onRefresh,
            onBack,
            navigation,
            showNetworkHub,
            layoutScale,
        )
        Spacer(Modifier.width(layoutSpacing))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(top = layoutSpacing, end = layoutSpacing, bottom = layoutSpacing),
            verticalArrangement = Arrangement.spacedBy(layoutSpacing),
        ) {
            TopDock(networkSelected, showNetworkHub, layoutScale, navigation, onLocal, onNetwork)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (showNetworkHub) {
                    NetworkHub(connections, navigation, onOpenSmb, onConnections)
                } else {
                    content(navigation)
                }
            }
        }
    }
}

@Composable
private fun TopDock(
    networkSelected: Boolean,
    showNetworkHub: Boolean,
    displayScale: Float,
    navigation: MainFocusNavigation,
    onLocal: () -> Unit,
    onNetwork: () -> Unit,
) {
    val down = if (showNetworkHub) navigation.contentTarget else navigation.path
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .testTag("source-bar")
            .focusRestorer(fallback = navigation.selectedSource)
            .focusGroup(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        SourceTab(
            label = "本机",
            selected = !networkSelected,
            requester = navigation.localSource,
            left = FocusRequester.Cancel,
            right = navigation.networkSource,
            down = down,
            navigation = navigation,
            displayScale = displayScale,
            onClick = onLocal,
        )
        Spacer(Modifier.width(12.dp * displayScale))
        SourceTab(
            label = "网络",
            selected = networkSelected,
            requester = navigation.networkSource,
            left = navigation.localSource,
            right = FocusRequester.Cancel,
            down = down,
            navigation = navigation,
            displayScale = displayScale,
            onClick = onNetwork,
        )
    }
}

@Composable
private fun SourceTab(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    left: FocusRequester,
    right: FocusRequester,
    down: FocusRequester,
    navigation: MainFocusNavigation,
    displayScale: Float,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = when {
            focused -> Color(0xFFFFC857)
            selected -> Color(0xFF152232)
            else -> Color.Transparent
        },
        label = "source-tab-background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (focused) Color(0xFF151007) else Color(0xFFF3F7FA),
        label = "source-tab-content",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) Color(0xFFFFE3A1) else Color.Transparent,
        label = "source-tab-border",
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier
            .height(48.dp)
            .focusProperties {
                up = FocusRequester.Cancel
                this.down = down
                this.left = left
                this.right = right
            }
            .focusRequester(requester)
            .shadow(if (selected && !focused) 8.dp else 0.dp, shape)
            .clip(shape)
            .background(background)
            .border(if (focused) 3.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) navigation.focusSource()
            }
            .focusable()
            .tvOkClick(onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 18.dp * displayScale, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = contentColor, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SideDock(
    onConnections: () -> Unit,
    onSettings: () -> Unit,
    browserViewMode: BrowserViewMode?,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    navigation: MainFocusNavigation,
    showNetworkHub: Boolean,
    displayScale: Float,
) {
    val browserActionsVisible = browserViewMode != null
    val dockRight = navigation.mainTarget(showNetworkHub)
    Column(
        Modifier.width(72.dp * displayScale).fillMaxHeight().testTag("main-dock")
            .background(Color(0xFF101A26)).padding(vertical = 14.dp)
            .focusRestorer(fallback = navigation.dockTarget(browserActionsVisible))
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (browserActionsVisible) {
            val viewLabel = if (browserViewMode == BrowserViewMode.Grid) "切换为列表视图" else "切换为网格视图"
            val viewIcon = if (browserViewMode == BrowserViewMode.Grid) R.drawable.ic_view_list else R.drawable.ic_view_grid
            DockIconButton(viewIcon, viewLabel, navigation.view, FocusRequester.Cancel, navigation.refresh, dockRight, navigation, onToggleView)
            Spacer(Modifier.height(8.dp))
            DockIconButton(R.drawable.ic_refresh, "刷新", navigation.refresh, navigation.view, navigation.back, dockRight, navigation, onRefresh)
            Spacer(Modifier.height(8.dp))
            DockIconButton(R.drawable.ic_back, "返回上级", navigation.back, navigation.refresh, navigation.connections, dockRight, navigation, onBack)
        }
        Spacer(Modifier.weight(1f))
        DockIconButton(
            R.drawable.ic_connections,
            "连接管理",
            navigation.connections,
            if (browserActionsVisible) navigation.back else FocusRequester.Cancel,
            navigation.settings,
            dockRight,
            navigation,
            onConnections,
        )
        Spacer(Modifier.height(8.dp))
        DockIconButton(R.drawable.ic_settings, "设置", navigation.settings, navigation.connections, FocusRequester.Cancel, dockRight, navigation, onSettings)
    }
}

@Composable
private fun DockIconButton(
    icon: Int,
    label: String,
    focusRequester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    right: FocusRequester,
    navigation: MainFocusNavigation,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color(0xFFFFC857) else Color.Transparent, label = "dock-background")
    val tint by animateColorAsState(if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), label = "dock-tint")
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(background)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color.Transparent, RoundedCornerShape(12.dp))
            .focusProperties {
                this.up = up
                this.down = down
                left = FocusRequester.Cancel
                this.right = right
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused || !confirming) {
                    focused = it.isFocused
                    navigation.focusDock(focusRequester, it.isFocused)
                }
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                when {
                    event.type == KeyEventType.KeyDown && isOk -> {
                        confirming = true
                        true
                    }
                    event.type == KeyEventType.KeyUp && isOk -> {
                        onClick()
                        focusRequester.requestFocus()
                        confirming = false
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun NetworkHub(
    connections: List<SmbConnectionInfo>,
    navigation: MainFocusNavigation,
    onOpenSmb: (String) -> Unit,
    onConnections: () -> Unit,
) {
    var focusedConnectionId by remember { mutableStateOf(connections.firstOrNull()?.id) }
    val entryConnectionId = focusedConnectionId?.takeIf { id -> connections.any { it.id == id } }
        ?: connections.firstOrNull()?.id
    if (connections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("尚未配置 SMB / NAS", color = Color(0xFFF3F7FA), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("添加连接后即可浏览网络媒体库。", color = Color(0xFFA8B8C7), fontSize = 20.sp)
                TvButton(
                    "去配置",
                    modifier = Modifier
                        .focusProperties {
                            up = navigation.selectedSource
                            down = FocusRequester.Cancel
                            left = navigation.dockTarget(browserActionsVisible = false)
                            right = FocusRequester.Cancel
                        }
                        .focusRequester(navigation.content)
                        .onFocusChanged { if (it.isFocused) navigation.focusContent() },
                    onClick = onConnections,
                )
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .focusRestorer(fallback = navigation.content)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("网络位置", color = Color(0xFF7CC7D8), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            connections.forEachIndexed { index, connection ->
                val entryModifier = if (connection.id == entryConnectionId) Modifier.focusRequester(navigation.content) else Modifier
                TvButton(
                    "${connection.name}   ${connection.host}/${connection.share}",
                    modifier = Modifier.fillMaxWidth()
                        .focusProperties {
                            if (index == 0) up = navigation.selectedSource
                            if (index == connections.lastIndex) down = FocusRequester.Cancel
                            left = navigation.dockTarget(browserActionsVisible = false)
                            right = FocusRequester.Cancel
                        }
                        .then(entryModifier)
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedConnectionId = connection.id
                                navigation.focusContent()
                            }
                        },
                ) { onOpenSmb(connection.id) }
            }
        }
    }
}
