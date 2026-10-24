package com.github.gbandszxc.goodtvplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.goodtvplorer.data.SmbConnectionInfo
import com.github.gbandszxc.goodtvplorer.data.displayTarget
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.data.isAnonymousCredentials
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
                val displayScale = fontScale.coerceIn(0.8f, 1.2f)
                val density = LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density * displayScale,
                        fontScale = effectiveFontScale(displayScale) / displayScale,
                    ),
                ) {
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
                                connection.displayTarget(includePort = true),
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
    var anonymous by remember(existing) { mutableStateOf(existing?.isAnonymousCredentials ?: false) }
    val scope = rememberCoroutineScope()
    // 焦点链节点：0 名称 1 主机 2 端口 3 共享目录 4 匿名访问 5 用户名 6 密码 7 域；7 下方固定进入取消，取消右侧为保存
    val fieldRequesters = remember { List(8) { BringIntoViewRequester() } }
    val focusRequesters = remember { List(8) { FocusRequester() } }
    val cancelActionFocus = remember { FocusRequester() }
    val saveActionFocus = remember { FocusRequester() }
    val visibleNodes = buildList {
        addAll(0..4)
        if (!anonymous) addAll(5..6)
        add(7)
    }
    var initialFocusRequested by remember { mutableStateOf(false) }
    fun Modifier.keepVisible(node: Int) = bringIntoViewRequester(fieldRequesters[node]).onFocusChanged {
        if (it.isFocused) scope.launch { fieldRequesters[node].bringIntoView() }
    }
    fun Modifier.formField(node: Int, downOverride: FocusRequester? = null): Modifier {
        val position = visibleNodes.indexOf(node)
        return focusRequester(focusRequesters[node])
            .focusProperties {
                if (position > 0) up = focusRequesters[visibleNodes[position - 1]]
                when {
                    downOverride != null -> down = downOverride
                    position < visibleNodes.lastIndex -> down = focusRequesters[visibleNodes[position + 1]]
                }
            }
            .keepVisible(node)
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
                        "主机必填；共享目录留空时展示服务器全部共享；用户名和密码均留空时匿名连接。"
                    } else {
                        "修改连接信息；共享目录留空时展示服务器全部共享；用户名和密码均留空时匿名连接。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp,
                )
                SmbTextField(
                    name,
                    { name = it },
                    "名称",
                    Modifier
                        .fillMaxWidth()
                        .formField(0)
                        .onGloballyPositioned {
                            if (!initialFocusRequested) {
                                initialFocusRequested = true
                                focusRequesters[0].requestFocus()
                            }
                        },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmbTextField(
                        host,
                        { host = it },
                        "主机",
                        Modifier.weight(1f).formField(1),
                    )
                    SmbTextField(
                        port,
                        { port = it },
                        "端口",
                        Modifier.width(128.dp).formField(2),
                        keyboardType = KeyboardType.Number,
                    )
                }
                SmbTextField(
                    share,
                    { share = it },
                    "共享目录（可选）",
                    Modifier.fillMaxWidth().formField(3),
                )
                SmbAnonymousToggle(
                    checked = anonymous,
                    onCheckedChange = { anonymous = it },
                    modifier = Modifier.fillMaxWidth().formField(4),
                )
                if (!anonymous) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmbTextField(
                            user,
                            { user = it },
                            "用户名",
                            Modifier.weight(1f).formField(5),
                        )
                        SmbTextField(
                            password,
                            { password = it },
                            "密码",
                            Modifier.weight(1f).formField(6),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardType = KeyboardType.Password,
                        )
                    }
                }
                SmbTextField(
                    domain,
                    { domain = it },
                    "域（可选）",
                    Modifier.fillMaxWidth().formField(7, downOverride = cancelActionFocus),
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TvButton(
                "保存",
                modifier = Modifier
                    .width(112.dp)
                    .focusRequester(saveActionFocus)
                    .focusProperties {
                        up = focusRequesters[7]
                        left = cancelActionFocus
                        right = FocusRequester.Cancel
                    },
                enabled = host.isNotBlank(),
                contentAlignment = Alignment.Center,
            ) {
                onSave(
                    SmbConnectionInfo(
                        existing?.id ?: UUID.randomUUID().toString(),
                        name.ifBlank { host },
                        host,
                        port.toIntOrNull() ?: 445,
                        share,
                        if (anonymous) "" else user,
                        if (anonymous) "" else password,
                        domain.ifBlank { null },
                    ),
                )
            }
        },
        dismissButton = {
            TvButton(
                "取消",
                modifier = Modifier
                    .width(112.dp)
                    .focusRequester(cancelActionFocus)
                    .focusProperties {
                        up = focusRequesters[7]
                        left = FocusRequester.Cancel
                        right = saveActionFocus
                    },
                contentAlignment = Alignment.Center,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SmbAnonymousToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(160),
        label = "anonymous-toggle-bg",
    )
    val border by animateDpAsState(if (focused) 3.dp else 1.dp, animationSpec = tween(160), label = "anonymous-toggle-border")
    val borderColor by animateColorAsState(
        if (focused) Color(0xFFFFE3A1) else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(160),
        label = "anonymous-toggle-border-color",
    )
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(BorderStroke(border, borderColor), RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        Key.DirectionUp -> {
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onCheckedChange(!checked)
                            true
                        }
                        else -> false
                    }
                }
            }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .onFocusChanged { focused = it.isFocused }
            .testTag("smb-anonymous-toggle")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.secondary,
                checkmarkColor = MaterialTheme.colorScheme.surface,
                uncheckedColor = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
            ),
        )
        Text(
            "匿名访问",
            color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides density) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .testTag("connection-dialog")
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape,
                        ),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                            content()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dismissButton()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyUp) {
                false
            } else {
                when (event.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            }
        },
        label = { Text(label, fontSize = 16.sp) },
        singleLine = true,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(fontSize = 20.sp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = {
                keyboardController?.hide()
                focusManager.moveFocus(FocusDirection.Down)
            },
        ),
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
