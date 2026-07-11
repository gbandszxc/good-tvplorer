package com.goodtvplorer.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodtvplorer.R
import com.goodtvplorer.data.SmbConnectionInfo
import com.goodtvplorer.ui.components.TvButton
import com.goodtvplorer.ui.components.tvOkClick

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
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Color(0xFF0B121A))) {
        SideDock(onConnections, onSettings)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            TopDock(networkSelected, onLocal, onNetwork)
            Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp)) {
                if (showNetworkHub) {
                    NetworkHub(connections, onOpenSmb, onConnections)
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun TopDock(networkSelected: Boolean, onLocal: () -> Unit, onNetwork: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).background(Color(0xFF101A26)).padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvButton(if (networkSelected) "本机" else "本机 · 当前", modifier = Modifier.width(190.dp), onClick = onLocal)
        Spacer(Modifier.width(14.dp))
        TvButton(if (networkSelected) "网络 / SMB · 当前" else "网络 / SMB", modifier = Modifier.width(220.dp), onClick = onNetwork)
    }
}

@Composable
private fun SideDock(onConnections: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.width(104.dp).fillMaxHeight().background(Color(0xFF101A26)).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("G", color = Color(0xFF7CC7D8), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        DockIconButton(R.drawable.ic_connections, "连接管理", onConnections)
        Spacer(Modifier.height(12.dp))
        DockIconButton(R.drawable.ic_settings, "设置", onSettings)
    }
}

@Composable
private fun DockIconButton(icon: Int, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(if (focused) Color(0xFFFFC857) else Color.Transparent, label = "dock-background")
    val tint by animateColorAsState(if (focused) Color(0xFF151007) else Color(0xFFF3F7FA), label = "dock-tint")
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(background)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFFE3A1) else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().tvOkClick(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(34.dp))
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
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("网络位置", color = Color(0xFF7CC7D8), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            connections.forEach { connection ->
                TvButton("${connection.name}   ${connection.host}/${connection.share}", modifier = Modifier.fillMaxWidth()) { onOpenSmb(connection.id) }
            }
        }
    }
}
