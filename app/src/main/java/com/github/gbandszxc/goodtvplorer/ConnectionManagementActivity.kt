package com.github.gbandszxc.goodtvplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
) {
    var editing by remember { mutableStateOf<SmbConnectionInfo?>(null) }
    var adding by remember { mutableStateOf(false) }
    var choosingProtocol by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SmbConnectionInfo?>(null) }
    val groupShape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "连接管理",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            TvButton("添加网络地址", onClick = { choosingProtocol = true })
        }
        if (connections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "当前暂无网络连接",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                connections.forEach { connection ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 84.dp)
                            .background(MaterialTheme.colorScheme.surface, groupShape)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), groupShape)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                connection.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${connection.host}:${connection.port}/${connection.share}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 18.sp,
                            )
                        }
                        TvButton(
                            "编辑",
                            modifier = Modifier.width(112.dp),
                            contentAlignment = Alignment.Center,
                            onClick = { editing = connection },
                        )
                        TvButton(
                            "删除",
                            modifier = Modifier.width(112.dp),
                            contentAlignment = Alignment.Center,
                            destructive = true,
                            onClick = { deleting = connection },
                        )
                    }
                }
            }
        }
    }
    if (choosingProtocol) {
        ConnectionDialog(
            onDismissRequest = { choosingProtocol = false },
            title = "添加网络地址",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "选择网络协议。当前仅支持 SMB。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                    )
                    TvButton(
                        "SMB",
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        choosingProtocol = false
                        adding = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TvButton(
                    "取消",
                    modifier = Modifier.width(112.dp),
                    contentAlignment = Alignment.Center,
                ) { choosingProtocol = false }
            },
        )
    }
    if (adding || editing != null) {
        SmbConnectionDialog(editing, onDismiss = { adding = false; editing = null }) {
            onSave(it)
            adding = false
            editing = null
        }
    }
    deleting?.let { connection ->
        ConnectionDialog(
            onDismissRequest = { deleting = null },
            title = "删除连接",
            content = {
                Text(
                    "将删除“${connection.name}”，此操作无法撤销。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
            },
            confirmButton = {
                TvButton(
                    "删除",
                    modifier = Modifier.width(112.dp),
                    contentAlignment = Alignment.Center,
                    destructive = true,
                ) {
                    onDelete(connection.id)
                    deleting = null
                }
            },
            dismissButton = {
                TvButton(
                    "取消",
                    modifier = Modifier.width(112.dp),
                    contentAlignment = Alignment.Center,
                ) { deleting = null }
            },
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
    val scope = rememberCoroutineScope()
    val fieldRequesters = remember { List(7) { BringIntoViewRequester() } }
    fun Modifier.keepVisible(index: Int) = bringIntoViewRequester(fieldRequesters[index]).onFocusChanged {
        if (it.isFocused) scope.launch { fieldRequesters[index].bringIntoView() }
    }
    ConnectionDialog(
        onDismissRequest = onDismiss,
        title = if (existing == null) "新增 SMB" else "编辑 SMB",
        content = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (existing == null) {
                        "填写 SMB 服务器信息，主机和共享目录为必填项。"
                    } else {
                        "修改已保存的网络连接信息。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
                SmbTextField(
                    name,
                    { name = it },
                    "名称",
                    Modifier.fillMaxWidth().keepVisible(0),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmbTextField(
                        host,
                        { host = it },
                        "主机",
                        Modifier.weight(1f).keepVisible(1),
                    )
                    SmbTextField(
                        port,
                        { port = it },
                        "端口",
                        Modifier.width(128.dp).keepVisible(2),
                    )
                }
                SmbTextField(
                    share,
                    { share = it },
                    "共享目录",
                    Modifier.fillMaxWidth().keepVisible(3),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmbTextField(
                        user,
                        { user = it },
                        "用户名",
                        Modifier.weight(1f).keepVisible(4),
                    )
                    SmbTextField(
                        password,
                        { password = it },
                        "密码",
                        Modifier.weight(1f).keepVisible(5),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                SmbTextField(
                    domain,
                    { domain = it },
                    "域（可选）",
                    Modifier.fillMaxWidth().keepVisible(6),
                )
            }
        },
        confirmButton = {
            TvButton(
                "保存",
                modifier = Modifier.width(112.dp),
                enabled = host.isNotBlank() && share.isNotBlank(),
                contentAlignment = Alignment.Center,
            ) {
                onSave(
                    SmbConnectionInfo(
                        existing?.id ?: UUID.randomUUID().toString(),
                        name.ifBlank { host },
                        host,
                        port.toIntOrNull() ?: 445,
                        share,
                        user,
                        password,
                        domain.ifBlank { null },
                    ),
                )
            }
        },
        dismissButton = {
            TvButton(
                "取消",
                modifier = Modifier.width(112.dp),
                contentAlignment = Alignment.Center,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ConnectionDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape,
        ),
        title = {
            Text(
                title,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun SmbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, fontSize = 16.sp) },
        singleLine = true,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(fontSize = 20.sp),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.secondary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}
