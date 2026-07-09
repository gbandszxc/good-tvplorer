package com.goodtvplorer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
) {
    var showDialog by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101418)).padding(56.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text("Good TVplorer", color = Color.White, fontSize = 38.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TvButton("本地文件", onClick = onLocal)
            TvButton("添加 SMB", onClick = { showDialog = true })
        }
        Spacer(Modifier.height(10.dp))
        Text("SMB 服务器", color = Color(0xFFCBD5E1), fontSize = 24.sp)
        if (connections.isEmpty()) {
            Text("暂无连接", color = Color(0xFF94A3B8), fontSize = 20.sp)
        }
        connections.forEach { info ->
            TvButton("${info.name}  ${info.host}/${info.share}", onClick = { onOpenSmb(info.id) })
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
