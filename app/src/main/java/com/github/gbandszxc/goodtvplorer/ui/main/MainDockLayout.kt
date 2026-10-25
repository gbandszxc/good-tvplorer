package com.github.gbandszxc.goodtvplorer.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.gbandszxc.goodtvplorer.data.SmbConnectionInfo
import com.github.gbandszxc.goodtvplorer.data.displayTarget
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionTone
import com.github.gbandszxc.goodtvplorer.ui.components.TvLabelMode
import com.github.gbandszxc.goodtvplorer.ui.components.TvRailAction
import com.github.gbandszxc.goodtvplorer.ui.components.tvClickable
import com.github.gbandszxc.goodtvplorer.ui.theme.LocalTvMotionEnabled
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvControlShape
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvMotion
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSizes
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import com.github.gbandszxc.goodtvplorer.viewmodel.BrowserViewMode

/** 网络中心连接行高度（规格要求 64dp）。 */
private val ConnectionRowHeight = 64.dp

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
    val networkHub = FocusRequester()
    val connections = FocusRequester()
    val settings = FocusRequester()

    var networkSelected by mutableStateOf(false)
    var contentAvailable by mutableStateOf(false)
    var contentInitialFocusAllowed by mutableStateOf(true)
        private set
    var contentFocused by mutableStateOf(false)
        private set
    private var lastMainRegion by mutableStateOf(MainFocusRegion.Content)
    private var lastToolbar by mutableStateOf(path)
    private var lastDock by mutableStateOf(view)

    /**
     * 浏览页顶栏来源动作的入口。来源按钮位于 [BrowserScreen][com.github.gbandszxc.goodtvplorer.ui.browser.BrowserScreen]
     * 的顶栏内，但浏览页公开签名保持不变，由 [MainDockLayout] 在这里注入真实回调。
     */
    var onSelectLocalSource: () -> Unit = {}
    var onSelectNetworkSource: () -> Unit = {}

    val selectedSource: FocusRequester
        get() = if (networkSelected) networkSource else localSource

    val toolbarTarget: FocusRequester
        get() = lastToolbar

    val contentTarget: FocusRequester
        get() = if (contentAvailable) content else FocusRequester.Cancel

    fun dockTarget(browserActionsVisible: Boolean): FocusRequester =
        if (!browserActionsVisible && lastDock !== networkHub && lastDock !== connections && lastDock !== settings) connections else lastDock

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
        contentFocused = false
        lastMainRegion = MainFocusRegion.Source
    }

    fun focusToolbar(target: FocusRequester) {
        contentInitialFocusAllowed = false
        contentFocused = false
        lastMainRegion = MainFocusRegion.Toolbar
        lastToolbar = target
    }

    fun focusContent() {
        contentInitialFocusAllowed = true
        contentFocused = true
        lastMainRegion = MainFocusRegion.Content
    }

    fun focusDock(target: FocusRequester, focused: Boolean) {
        if (focused) {
            contentInitialFocusAllowed = false
            contentFocused = false
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
    onNetworkHub: () -> Unit,
    onConnections: () -> Unit,
    onSettings: () -> Unit,
    browserViewMode: BrowserViewMode?,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSystemBack: (contentFocused: Boolean) -> Unit = {},
    content: @Composable (MainFocusNavigation) -> Unit,
) {
    val navigation = remember { MainFocusNavigation() }
    BackHandler { onSystemBack(navigation.contentFocused) }
    SideEffect {
        navigation.networkSelected = networkSelected
        navigation.onSelectLocalSource = onLocal
        navigation.onSelectNetworkSource = onNetwork
        if (showNetworkHub) navigation.contentAvailable = true
    }
    Row(
        Modifier.fillMaxSize().testTag("main-layout").background(TvColors.Background),
    ) {
        SideDock(
            onNetworkHub,
            onConnections,
            onSettings,
            browserViewMode,
            onToggleView,
            onRefresh,
            onBack,
            navigation,
            showNetworkHub,
        )
        Spacer(Modifier.width(TvSpacing.lg))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(top = TvSpacing.lg, end = TvSpacing.lg, bottom = TvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.lg),
        ) {
            // 浏览模式下来源与工具栏合并在浏览器自己的 56dp 顶栏里；
            // 网络中心没有浏览页，仍由这里提供来源切换条。
            if (showNetworkHub) {
                TopDock(networkSelected, navigation, onLocal, onNetwork)
            }
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
    navigation: MainFocusNavigation,
    onLocal: () -> Unit,
    onNetwork: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(TvSizes.toolbar)
            .testTag("source-bar")
            .focusRestorer(fallback = navigation.selectedSource)
            .focusGroup(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvActionButton(
            icon = TvIcons.HardDrive,
            label = "本机",
            onClick = onLocal,
            labelMode = TvLabelMode.Always,
            selected = !networkSelected,
            modifier = Modifier
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = navigation.contentTarget
                    left = FocusRequester.Cancel
                    right = navigation.networkSource
                }
                .focusRequester(navigation.localSource)
                .onFocusChanged { if (it.isFocused) navigation.focusSource() },
        )
        Spacer(Modifier.width(TvSpacing.sm))
        TvActionButton(
            icon = TvIcons.Server,
            label = "网络",
            onClick = onNetwork,
            labelMode = TvLabelMode.Always,
            selected = networkSelected,
            modifier = Modifier
                .focusProperties {
                    up = FocusRequester.Cancel
                    down = navigation.contentTarget
                    left = navigation.localSource
                    right = FocusRequester.Cancel
                }
                .focusRequester(navigation.networkSource)
                .onFocusChanged { if (it.isFocused) navigation.focusSource() },
        )
    }
}

@Composable
private fun SideDock(
    onNetworkHub: () -> Unit,
    onConnections: () -> Unit,
    onSettings: () -> Unit,
    browserViewMode: BrowserViewMode?,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    navigation: MainFocusNavigation,
    showNetworkHub: Boolean,
) {
    val browserActionsVisible = browserViewMode != null
    val dockRight = navigation.mainTarget(showNetworkHub)
    Column(
        Modifier.width(TvSizes.dock).fillMaxHeight().testTag("main-dock")
            // 聚焦时右侧浮出的标签药丸要绘制在内容区之上。
            .zIndex(2f)
            .background(TvColors.Panel).padding(vertical = TvSpacing.lg)
            .focusRestorer(fallback = navigation.dockTarget(browserActionsVisible))
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (browserActionsVisible) {
            // 聚焦药丸横向浮出 Dock，上部动作必须从顶栏下沿开始，避免遮挡来源按钮。
            Spacer(Modifier.height(TvSizes.toolbar + TvSpacing.lg))
            val viewLabel = if (browserViewMode == BrowserViewMode.Grid) "切换为列表视图" else "切换为网格视图"
            val viewIcon = if (browserViewMode == BrowserViewMode.Grid) TvIcons.List else TvIcons.Grid
            DockAction(viewIcon, viewLabel, navigation.view, FocusRequester.Cancel, navigation.refresh, dockRight, navigation, onToggleView)
            Spacer(Modifier.height(TvSpacing.sm))
            DockAction(TvIcons.RefreshCw, "刷新", navigation.refresh, navigation.view, navigation.back, dockRight, navigation, onRefresh)
            Spacer(Modifier.height(TvSpacing.sm))
            DockAction(TvIcons.ArrowLeft, "返回上级", navigation.back, navigation.refresh, navigation.connections, dockRight, navigation, onBack)
        }
        Spacer(Modifier.weight(1f))
        // 浏览页的来源入口在顶栏，Dock 不再重复“网络位置”；网络中心仍需要它。
        if (!browserActionsVisible) {
            DockAction(
                TvIcons.Link,
                "网络位置",
                navigation.networkHub,
                FocusRequester.Cancel,
                navigation.connections,
                dockRight,
                navigation,
                onNetworkHub,
            )
            Spacer(Modifier.height(TvSpacing.sm))
        }
        DockAction(
            TvIcons.Server,
            "连接管理",
            navigation.connections,
            if (browserActionsVisible) navigation.back else navigation.networkHub,
            navigation.settings,
            dockRight,
            navigation,
            onConnections,
        )
        Spacer(Modifier.height(TvSpacing.sm))
        DockAction(TvIcons.Settings, "设置", navigation.settings, navigation.connections, FocusRequester.Cancel, dockRight, navigation, onSettings)
    }
}

@Composable
private fun DockAction(
    icon: ImageVector,
    label: String,
    requester: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    right: FocusRequester,
    navigation: MainFocusNavigation,
    onClick: () -> Unit,
) {
    TvRailAction(
        icon = icon,
        label = label,
        onClick = {
            onClick()
            // 切换视图/刷新会重建内容区，动作后把焦点留在原 Dock 按钮上。
            requester.requestFocus()
        },
        modifier = Modifier
            .focusProperties {
                this.up = up
                this.down = down
                left = FocusRequester.Cancel
                this.right = right
            }
            .focusRequester(requester)
            .onFocusChanged { navigation.focusDock(requester, it.isFocused) },
    )
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TvSpacing.lg)) {
                Text("尚未配置 SMB / NAS", color = TvColors.TextPrimary, style = TvType.sectionTitle)
                Text("添加连接后即可浏览网络媒体库。", color = TvColors.TextSecondary, style = TvType.body)
                TvActionButton(
                    icon = TvIcons.Plus,
                    label = "去配置",
                    onClick = onConnections,
                    labelMode = TvLabelMode.Always,
                    tone = TvActionTone.Primary,
                    modifier = Modifier
                        .focusProperties {
                            up = navigation.selectedSource
                            down = FocusRequester.Cancel
                            left = navigation.dockTarget(browserActionsVisible = false)
                            right = FocusRequester.Cancel
                        }
                        .focusRequester(navigation.content)
                        .onFocusChanged { if (it.isFocused) navigation.focusContent() },
                )
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .focusRestorer(fallback = navigation.content)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.md),
        ) {
            Text("网络位置", color = TvColors.Accent, style = TvType.sectionTitle)
            connections.forEachIndexed { index, connection ->
                val entryModifier = if (connection.id == entryConnectionId) Modifier.focusRequester(navigation.content) else Modifier
                ConnectionRow(
                    connection = connection,
                    focusModifier = Modifier.fillMaxWidth()
                        .focusProperties {
                            if (index == 0) up = navigation.selectedSource
                            if (index == connections.lastIndex) down = FocusRequester.Cancel
                            left = navigation.dockTarget(browserActionsVisible = false)
                            right = FocusRequester.Cancel
                        }
                        .then(entryModifier),
                    onFocused = {
                        focusedConnectionId = connection.id
                        navigation.focusContent()
                    },
                    onClick = { onOpenSmb(connection.id) },
                )
            }
        }
    }
}

/**
 * 网络中心连接行：Server 图标 + 名称/目标，整行作为单一焦点目标。
 * 聚焦时琥珀底 + 2dp FocusRing，与浏览页列表行保持同一视觉语言。
 */
@Composable
private fun ConnectionRow(
    connection: SmbConnectionInfo,
    focusModifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val motionEnabled = LocalTvMotionEnabled.current
    val name = connection.name
    val target = connection.displayTarget()
    val background by animateColorAsState(
        targetValue = if (focused) TvColors.Focus else TvColors.Surface,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "connection-row-background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) TvColors.FocusRing else TvColors.OutlineVariant,
        animationSpec = if (motionEnabled) TvMotion.color() else tween(durationMillis = 0),
        label = "connection-row-border-color",
    )
    Row(
        modifier = focusModifier
            .height(ConnectionRowHeight)
            .clip(TvControlShape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, borderColor, TvControlShape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .tvClickable(onClick = onClick)
            .semantics { contentDescription = "$name $target" }
            .padding(horizontal = TvSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
    ) {
        Icon(
            imageVector = TvIcons.Server,
            contentDescription = null,
            tint = if (focused) TvColors.OnFocus else TvColors.Accent,
            modifier = Modifier.size(TvSizes.iconLarge),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.xs),
        ) {
            Text(
                text = name,
                color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                style = TvType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = target,
                color = if (focused) TvColors.OnFocusMuted else TvColors.TextSecondary,
                style = TvType.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
