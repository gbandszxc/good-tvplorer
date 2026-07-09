package com.goodtvplorer.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodtvplorer.data.SmbConnectionInfo
import com.goodtvplorer.ui.components.TvButton
import java.util.UUID

@Composable
fun HomeScreen(
    connections: List<SmbConnectionInfo>,
    onLocal: () -> Unit,
    onOpenSmb: (String) -> Unit,
    onAddSmb: (SmbConnectionInfo) -> Unit,
    onDisplaySettings: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxSize().background(Color(0xFF0B121A)).padding(40.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(310.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Good TVplorer", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("为电视遥控器和 NAS 媒体库准备的文件管理器。", color = Color(0xFFA8B8C7), fontSize = 21.sp, lineHeight = 29.sp)
            Spacer(Modifier.height(16.dp))
            TvButton("本地文件", modifier = Modifier.fillMaxWidth(), onClick = onLocal)
            TvButton("添加 SMB", modifier = Modifier.fillMaxWidth(), onClick = { showDialog = true })
            TvButton("显示设置", modifier = Modifier.fillMaxWidth(), onClick = onDisplaySettings)
            Spacer(Modifier.weight(1f))
            Text("OK 打开  ·  Back 返回", color = Color(0xFF728397), fontSize = 17.sp)
        }

        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0xFF101A26)).padding(24.dp)) {
            Text("SMB / NAS", color = Color(0xFF7CC7D8), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            if (connections.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text("暂无服务器。添加 SMB 后可浏览图片、音频、视频并生成预览。", color = Color(0xFFA8B8C7), fontSize = 24.sp, lineHeight = 34.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    connections.forEach { info ->
                        TvButton("${info.name}   ${info.host}/${info.share}", modifier = Modifier.fillMaxWidth(), onClick = { onOpenSmb(info.id) })
                    }
                }
            }
        }
    }
    if (showDialog) {
        SmbDialog(
            onDismiss = { showDialog = false },
            onSave = {
                onAddSmb(it)
                showDialog = false
            },
        )
    }
}

@Composable
private fun SmbDialog(onDismiss: () -> Unit, onSave: (SmbConnectionInfo) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("445") }
    var share by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 SMB") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true)
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, singleLine = true)
                OutlinedTextField(share, { share = it }, label = { Text("Share") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(domain, { domain = it }, label = { Text("Domain，可选") }, singleLine = true)
            }
        },
        confirmButton = {
            TvButton("保存") {
                if (host.isNotBlank() && share.isNotBlank()) {
                    onSave(SmbConnectionInfo(UUID.randomUUID().toString(), name.ifBlank { host }, host, port.toIntOrNull() ?: 445, share, user, pass, domain.ifBlank { null }))
                }
            }
        },
        dismissButton = { TvButton("取消", onClick = onDismiss) },
    )
}
