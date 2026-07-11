package com.github.gbandszxc.goodtvplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.goodtvplorer.data.SmbConnectionInfo
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import com.github.gbandszxc.goodtvplorer.data.persistence.SmbConnectionRepository
import com.github.gbandszxc.goodtvplorer.ui.components.TvButton
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import java.util.UUID
import kotlinx.coroutines.launch

class ConnectionManagementActivity : ComponentActivity() {
    private val connectionsStore by lazy { SmbConnectionRepository(applicationContext) }
    private val displaySettings by lazy { DisplaySettingsRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvTheme {
                val connections by connectionsStore.all.collectAsState(initial = emptyList())
                val fontScale by displaySettings.fontScale.collectAsState(initial = 1f)
                val density = LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, effectiveFontScale(fontScale))) {
                    BackHandler { finish() }
                    ConnectionManagementScreen(
                        connections = connections,
                        onSave = { info -> lifecycleScope.launch { connectionsStore.save(info) } },
                        onDelete = { id -> lifecycleScope.launch { connectionsStore.delete(id) } },
                        onClose = ::finish,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionManagementScreen(
    connections: List<SmbConnectionInfo>,
    onSave: (SmbConnectionInfo) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf<SmbConnectionInfo?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SmbConnectionInfo?>(null) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("连接管理", modifier = Modifier.weight(1f), color = Color(0xFFF3F7FA), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            TvButton("新增 SMB", onClick = { adding = true })
            Spacer(Modifier.width(14.dp))
            TvButton("返回", onClick = onClose)
        }
        if (connections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("暂无 SMB / NAS 连接", color = Color(0xFFF3F7FA), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    TvButton("新增 SMB", onClick = { adding = true })
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                connections.forEach { connection ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(connection.name, color = Color(0xFFF3F7FA), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Text("${connection.host}:${connection.port}/${connection.share}", color = Color(0xFFA8B8C7), fontSize = 18.sp)
                        }
                        TvButton("编辑", onClick = { editing = connection })
                        TvButton("删除", onClick = { deleting = connection })
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        SmbConnectionDialog(editing, onDismiss = { adding = false; editing = null }) {
            onSave(it)
            adding = false
            editing = null
        }
    }
    deleting?.let { connection ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除连接") },
            text = { Text("确定删除“${connection.name}”吗？") },
            confirmButton = { TvButton("删除") { onDelete(connection.id); deleting = null } },
            dismissButton = { TvButton("取消") { deleting = null } },
        )
    }
}

@Composable
private fun SmbConnectionDialog(existing: SmbConnectionInfo?, onDismiss: () -> Unit, onSave: (SmbConnectionInfo) -> Unit) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var host by remember(existing) { mutableStateOf(existing?.host.orEmpty()) }
    var port by remember(existing) { mutableStateOf(existing?.port?.toString() ?: "445") }
    var share by remember(existing) { mutableStateOf(existing?.share.orEmpty()) }
    var user by remember(existing) { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember(existing) { mutableStateOf(existing?.password.orEmpty()) }
    var domain by remember(existing) { mutableStateOf(existing?.domain.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增 SMB" else "编辑 SMB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true)
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true)
                OutlinedTextField(share, { share = it }, label = { Text("Share") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(domain, { domain = it }, label = { Text("Domain，可选") }, singleLine = true)
            }
        },
        confirmButton = {
            TvButton("保存") {
                if (host.isNotBlank() && share.isNotBlank()) {
                    onSave(SmbConnectionInfo(existing?.id ?: UUID.randomUUID().toString(), name.ifBlank { host }, host, port.toIntOrNull() ?: 445, share, user, password, domain.ifBlank { null }))
                }
            }
        },
        dismissButton = { TvButton("取消", onClick = onDismiss) },
    )
}
