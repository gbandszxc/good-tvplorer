package com.github.gbandszxc.goodtvplorer.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

@Composable
fun MainDockLayout(
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
    content: @Composable (contentAutoFocusEnabled: Boolean) -> Unit,
) {
    var browserActionFocused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(Color(0xFF0B121A))) {
        SideDock(
            onConnections,
            onSettings,
            browserViewMode,
            onToggleView,
            onRefresh,
            onBack,
            onBrowserActionFocusChange = { browserActionFocused = it },
        )
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopDock(networkSelected, onLocal, onNetwork)
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 16.dp)) {
                if (showNetworkHub) {
                    NetworkHub(connections, onOpenSmb, onConnections)
                } else {
                    content(!browserActionFocused)
                }
            }
        }
    }
}

@Composable
private fun TopDock(networkSelected: Boolean, onLocal: () -> Unit, onNetwork: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        SourceTab(label = "本机", selected = !networkSelected, onClick = onLocal)
        Spacer(Modifier.width(18.dp))
        SourceTab(label = "网络", selected = networkSelected, onClick = onNetwork)
    }
}

@Composable
private fun SourceTab(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .shadow(if (selected && !focused) 8.dp else 0.dp, shape)
            .clip(shape)
            .background(background)
            .border(if (focused) 3.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvOkClick(onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 24.dp, vertical = 5.dp),
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
    onBrowserActionFocusChange: (Boolean) -> Unit,
) {
    Column(
        Modifier.width(60.dp).fillMaxHeight().background(Color(0xFF101A26)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (browserViewMode != null) {
            val viewLabel = if (browserViewMode == BrowserViewMode.Grid) "切换为列表视图" else "切换为网格视图"
            val viewIcon = if (browserViewMode == BrowserViewMode.Grid) R.drawable.ic_view_list else R.drawable.ic_view_grid
            DockIconButton(viewIcon, viewLabel, onToggleView, onBrowserActionFocusChange)
            Spacer(Modifier.height(8.dp))
            DockIconButton(R.drawable.ic_refresh, "刷新", onRefresh, onBrowserActionFocusChange)
            Spacer(Modifier.height(8.dp))
            DockIconButton(R.drawable.ic_back, "返回上级", onBack, onBrowserActionFocusChange)
        }
        Spacer(Modifier.weight(1f))
        DockIconButton(R.drawable.ic_connections, "连接管理", onConnections)
        Spacer(Modifier.height(8.dp))
        DockIconButton(R.drawable.ic_settings, "设置", onSettings)
    }
}

@Composable
private fun DockIconButton(icon: Int, label: String, onClick: () -> Unit, onFocusChange: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val background by animateColorAsState(if (focused) Color(0xFFFFC857) else Color.Transparent, label = "dock-background")
    val tint by animateColorAsState(if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), label = "dock-tint")
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(background)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color.Transparent, RoundedCornerShape(12.dp))
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused || !confirming) {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
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
private fun NetworkHub(connections: List<SmbConnectionInfo>, onOpenSmb: (String) -> Unit, onConnections: () -> Unit) {
    if (connections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("尚未配置 SMB / NAS", color = Color(0xFFF3F7FA), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("添加连接后即可浏览网络媒体库。", color = Color(0xFFA8B8C7), fontSize = 20.sp)
                TvButton("去配置", onClick = onConnections)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("网络位置", color = Color(0xFF7CC7D8), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            connections.forEach { connection ->
                TvButton("${connection.name}   ${connection.host}/${connection.share}", modifier = Modifier.fillMaxWidth()) { onOpenSmb(connection.id) }
            }
        }
    }
}
