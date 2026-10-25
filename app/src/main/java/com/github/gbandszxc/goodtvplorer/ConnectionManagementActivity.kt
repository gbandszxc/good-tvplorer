package com.github.gbandszxc.goodtvplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.github.gbandszxc.goodtvplorer.data.SmbConnectionInfo
import com.github.gbandszxc.goodtvplorer.data.displayTarget
import com.github.gbandszxc.goodtvplorer.data.effectiveFontScale
import com.github.gbandszxc.goodtvplorer.data.isAnonymousCredentials
import com.github.gbandszxc.goodtvplorer.data.persistence.DisplaySettingsRepository
import com.github.gbandszxc.goodtvplorer.data.persistence.SmbConnectionRepository
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionButton
import com.github.gbandszxc.goodtvplorer.ui.components.TvActionTone
import com.github.gbandszxc.goodtvplorer.ui.components.TvDialog
import com.github.gbandszxc.goodtvplorer.ui.components.TvLabelMode
import com.github.gbandszxc.goodtvplorer.ui.components.TvTextField
import com.github.gbandszxc.goodtvplorer.ui.components.TvToggle
import com.github.gbandszxc.goodtvplorer.ui.theme.TvColors
import com.github.gbandszxc.goodtvplorer.ui.theme.TvIcons
import com.github.gbandszxc.goodtvplorer.ui.theme.TvRadius
import com.github.gbandszxc.goodtvplorer.ui.theme.TvSpacing
import com.github.gbandszxc.goodtvplorer.ui.theme.TvTheme
import com.github.gbandszxc.goodtvplorer.ui.theme.TvType
import java.util.UUID
import kotlinx.coroutines.launch

/** 连接行最小高度（规格要求 84dp）。 */
private val ConnectionRowMinHeight = 84.dp

/** 连接行左侧服务器图标尺寸（规格要求 28dp）。 */
private val ConnectionIconSize = 28.dp

/** 连接目标文字样式：18sp 常规字重。 */
private val ConnectionTargetStyle = TvType.body.copy(fontSize = 18.sp)

/**
 * SMB 表单状态提升到 Dialog 之外，使共享 [TvDialog] 的 actions 能读取并保存字段。
 * 每次进入新增/编辑时由 Screen 的 `remember(editing, adding)` 重建。
 */
@Stable
private class SmbFormState(existing: SmbConnectionInfo?) {
    var name by mutableStateOf(existing?.name.orEmpty())
    var host by mutableStateOf(existing?.host.orEmpty())
    var port by mutableStateOf(existing?.port?.toString() ?: "445")
    var share by mutableStateOf(existing?.share.orEmpty())
    var user by mutableStateOf(existing?.username.orEmpty())
    var password by mutableStateOf(existing?.password.orEmpty())
    var domain by mutableStateOf(existing?.domain.orEmpty())
    var anonymous by mutableStateOf(existing?.isAnonymousCredentials ?: false)

    /** 是否为新建（非编辑）连接，用于切换说明文案。 */
    val isNew: Boolean = existing == null

    /** 主机必填。 */
    val canSave: Boolean get() = host.isNotBlank()

    /** 构造连接信息，逻辑与重构前完全一致。 */
    fun toConnection(existing: SmbConnectionInfo?): SmbConnectionInfo =
        SmbConnectionInfo(
            existing?.id ?: UUID.randomUUID().toString(),
            name.ifBlank { host },
            host,
            port.toIntOrNull() ?: 445,
            share,
            if (anonymous) "" else user,
            if (anonymous) "" else password,
            domain.ifBlank { null },
        )
}

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
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density * displayScale,
                        fontScale = effectiveFontScale(displayScale) / displayScale,
                    ),
                ) {
                    BackHandler { finish() }
                    ConnectionManagementScreen(
                        connections = connections,
                        onBack = ::finish,
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
    onBack: () -> Unit,
    onSave: (SmbConnectionInfo) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<SmbConnectionInfo?>(null) }
    var adding by remember { mutableStateOf(false) }
    var choosingProtocol by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SmbConnectionInfo?>(null) }
    val protocolSmbFocus = remember { FocusRequester() }
    val protocolCancelFocus = remember { FocusRequester() }
    val cancelActionFocus = remember { FocusRequester() }
    val saveActionFocus = remember { FocusRequester() }
    val lastFieldFocus = remember { FocusRequester() }
    // 协议选择与 SMB 表单共用一个 Dialog 窗口：只切换内容而不重建窗口，
    // 避免同一帧销毁旧窗口/创建新窗口导致新窗口拿不到按键焦点。
    val formState = remember(editing, adding) {
        if (adding || editing != null) SmbFormState(editing) else null
    }
    val showingForm = adding || editing != null
    val rowShape = RoundedCornerShape(TvRadius.md)

    Column(
        Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.xl),
    ) {
        // 顶栏：返回动作在左，标题紧随其后；整行仍从 36dp 横向内边距开始。
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
        ) {
            TvActionButton(
                icon = TvIcons.ArrowLeft,
                label = "返回",
                onClick = onBack,
            )
            Text(
                text = "连接管理",
                color = TvColors.TextPrimary,
                style = TvType.pageTitle,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            TvActionButton(
                icon = TvIcons.Plus,
                label = "添加网络地址",
                onClick = { choosingProtocol = true },
                tone = TvActionTone.Primary,
                labelMode = TvLabelMode.Always,
            )
        }
        if (connections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "当前暂无网络连接",
                    color = TvColors.TextSecondary,
                    style = TvType.sectionTitle,
                )
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.md),
            ) {
                connections.forEach { connection ->
                    ConnectionRow(
                        connection = connection,
                        shape = rowShape,
                        onEdit = { editing = connection },
                        onDelete = { deleting = connection },
                    )
                }
            }
        }
    }
    if (choosingProtocol || showingForm) {
        TvDialog(
            visible = true,
            onDismissRequest = {
                choosingProtocol = false
                adding = false
                editing = null
            },
            title = when {
                choosingProtocol -> "添加网络地址"
                editing == null -> "新增 SMB"
                else -> "编辑 SMB"
            },
            icon = if (choosingProtocol) TvIcons.Link else TvIcons.Server,
            width = if (choosingProtocol) 520.dp else 560.dp,
            modifier = if (choosingProtocol) Modifier else Modifier.testTag("connection-dialog"),
            content = {
                if (choosingProtocol) {
                    // 内容在独立子组合中布局，等一帧后再请求焦点，确保节点已挂载。
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        runCatching { protocolSmbFocus.requestFocus() }
                    }
                    Text(
                        text = "选择网络协议。当前仅支持 SMB。",
                        color = TvColors.TextSecondary,
                        style = TvType.body,
                    )
                    Spacer(Modifier.height(TvSpacing.md))
                    TvActionButton(
                        icon = TvIcons.Server,
                        label = "SMB",
                        onClick = {
                            choosingProtocol = false
                            adding = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(protocolSmbFocus)
                            // 当前只有 SMB 一种协议：向下保持当前焦点（Cancel 终止搜索），向右进入取消，
                            // 不依赖几何猜测，也满足既有测试“向下仍聚焦 SMB”的断言。
                            .focusProperties {
                                down = FocusRequester.Cancel
                                right = protocolCancelFocus
                            },
                    )
                } else {
                    formState?.let { state ->
                        SmbConnectionForm(
                            state = state,
                            cancelActionFocus = cancelActionFocus,
                            lastFieldFocus = lastFieldFocus,
                        )
                    }
                }
            },
            actions = {
                if (choosingProtocol) {
                    TvActionButton(
                        icon = TvIcons.X,
                        label = "取消",
                        onClick = { choosingProtocol = false },
                        modifier = Modifier
                            .focusRequester(protocolCancelFocus)
                            .focusProperties {
                                up = protocolSmbFocus
                                left = protocolSmbFocus
                            },
                    )
                } else {
                    formState?.let { state ->
                        TvActionButton(
                            icon = TvIcons.X,
                            label = "取消",
                            onClick = {
                                adding = false
                                editing = null
                            },
                            modifier = Modifier
                                .focusRequester(cancelActionFocus)
                                .focusProperties {
                                    up = lastFieldFocus
                                    left = FocusRequester.Cancel
                                    right = saveActionFocus
                                },
                        )
                        TvActionButton(
                            icon = TvIcons.Save,
                            label = "保存",
                            onClick = {
                                onSave(state.toConnection(editing))
                                adding = false
                                editing = null
                            },
                            modifier = Modifier
                                .focusRequester(saveActionFocus)
                                .focusProperties {
                                    up = lastFieldFocus
                                    left = cancelActionFocus
                                    right = FocusRequester.Cancel
                                },
                            enabled = state.canSave,
                            tone = TvActionTone.Primary,
                        )
                    }
                }
            },
        )
    }
    deleting?.let { connection ->
        TvDialog(
            visible = true,
            onDismissRequest = { deleting = null },
            title = "删除连接",
            icon = TvIcons.Trash2,
            width = 520.dp,
            content = {
                Text(
                    text = "将删除“${connection.name}”，此操作无法撤销。",
                    color = TvColors.TextSecondary,
                    style = TvType.body,
                )
            },
            actions = {
                TvActionButton(
                    icon = TvIcons.X,
                    label = "取消",
                    onClick = { deleting = null },
                )
                TvActionButton(
                    icon = TvIcons.Trash2,
                    label = "删除",
                    onClick = {
                        onDelete(connection.id)
                        deleting = null
                    },
                    tone = TvActionTone.Destructive,
                )
            },
        )
    }
}

@Composable
private fun ConnectionRow(
    connection: SmbConnectionInfo,
    shape: RoundedCornerShape,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ConnectionRowMinHeight)
            .clip(shape)
            .background(TvColors.Surface)
            .border(1.dp, TvColors.OutlineVariant, shape)
            .padding(horizontal = TvSpacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.md),
    ) {
        Icon(
            imageVector = TvIcons.Server,
            contentDescription = null,
            tint = TvColors.Accent,
            modifier = Modifier.size(ConnectionIconSize),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = connection.name,
                color = TvColors.TextPrimary,
                style = TvType.rowTitle,
                maxLines = 1,
            )
            Text(
                text = connection.displayTarget(includePort = true),
                color = TvColors.TextSecondary,
                style = ConnectionTargetStyle,
                maxLines = 1,
            )
        }
        TvActionButton(
            icon = TvIcons.Pencil,
            label = "编辑",
            onClick = onEdit,
        )
        TvActionButton(
            icon = TvIcons.Trash2,
            label = "删除",
            onClick = onDelete,
            tone = TvActionTone.Destructive,
        )
    }
}

@Composable
private fun SmbConnectionForm(
    state: SmbFormState,
    cancelActionFocus: FocusRequester,
    lastFieldFocus: FocusRequester,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // 焦点链节点：0 名称 1 主机 2 端口 3 共享目录 4 匿名访问 5 用户名 6 密码 7 域；7 下方固定进入取消
    val fieldRequesters = remember { List(8) { BringIntoViewRequester() } }
    val focusRequesters = remember { List(8) { FocusRequester() } }
    val visibleNodes = buildList {
        addAll(0..4)
        if (!state.anonymous) addAll(5..6)
        add(7)
    }
    // 域字段的 requester 提升到 Screen，供 actions 的 up 指向它。
    fun requesterFor(node: Int): FocusRequester =
        if (node == 7) lastFieldFocus else focusRequesters[node]
    // 协议与表单共用同一个 Dialog 窗口，窗口此时已存在，首次请求通常立即成功；
    // 仍保留 View.postDelayed 重试覆盖窗口装饰调整，避免焦点被重置后不再回来。
    val view = LocalView.current
    var initialFocusSettled by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val action = object : Runnable {
            private var attempts = 0
            override fun run() {
                if (initialFocusSettled) return
                if (runCatching { focusRequesters[0].requestFocus() }.getOrDefault(false)) {
                    initialFocusSettled = true
                    return
                }
                if (++attempts < 20) view.postDelayed(this, 32)
            }
        }
        view.post(action)
        onDispose { view.removeCallbacks(action) }
    }
    // 只负责焦点链：作用在 TvTextField 的 modifier（BasicTextField）。
    fun Modifier.formFocus(node: Int, downOverride: FocusRequester? = null): Modifier {
        val position = visibleNodes.indexOf(node)
        return focusRequester(requesterFor(node))
            .focusProperties {
                if (position > 0) up = requesterFor(visibleNodes[position - 1])
                when {
                    downOverride != null -> down = downOverride
                    position < visibleNodes.lastIndex -> down = requesterFor(visibleNodes[position + 1])
                }
            }
            .bringIntoViewRequester(fieldRequesters[node])
            .onFocusChanged {
                if (it.isFocused) scope.launch { fieldRequesters[node].bringIntoView() }
            }
    }

    // 负责方向键：作用在 TvTextField 的 containerModifier（焦点节点的祖先）。
    // BasicTextField 的真实焦点节点在内侧，挂在字段 modifier 上的 preview handler
    // 收不到按键，因此必须由外层容器在 preview 阶段消费。
    fun Modifier.formKeys(): Modifier = onPreviewKeyEvent { event ->
        when (event.key) {
            Key.DirectionUp, Key.DirectionDown -> {
                // KeyDown 一并消费，阻止系统默认几何焦点搜索先移动一次；
                // KeyUp 只按 focusProperties 链显式移动一次。
                if (event.type == KeyEventType.KeyUp) {
                    focusManager.moveFocus(
                        if (event.key == Key.DirectionUp) FocusDirection.Up else FocusDirection.Down,
                    )
                }
                true
            }
            else -> false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (state.isNew) {
                "主机必填；共享目录留空时展示服务器全部共享；用户名和密码均留空时匿名连接。"
            } else {
                "修改连接信息；共享目录留空时展示服务器全部共享；用户名和密码均留空时匿名连接。"
            },
            color = TvColors.TextSecondary,
            style = TvType.caption,
        )
        TvTextField(
            value = state.name,
            onValueChange = { state.name = it },
            label = "名称",
            containerModifier = Modifier.fillMaxWidth().formKeys(),
            modifier = Modifier.formFocus(0),
            inlineLabel = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            TvTextField(
                value = state.host,
                onValueChange = { state.host = it },
                label = "主机",
                containerModifier = Modifier.weight(1f).formKeys(),
                modifier = Modifier.formFocus(1),
                inlineLabel = true,
            )
            TvTextField(
                value = state.port,
                onValueChange = { state.port = it },
                label = "端口",
                containerModifier = Modifier.width(128.dp).formKeys(),
                modifier = Modifier.formFocus(2),
                keyboardType = KeyboardType.Number,
                inlineLabel = true,
            )
        }
        TvTextField(
            value = state.share,
            onValueChange = { state.share = it },
            label = "共享目录（可选）",
            containerModifier = Modifier.fillMaxWidth().formKeys(),
            modifier = Modifier.formFocus(3),
            inlineLabel = true,
        )
        TvToggle(
            checked = state.anonymous,
            onCheckedChange = { state.anonymous = it },
            label = "匿名访问",
            modifier = Modifier
                .fillMaxWidth()
                .formFocus(4)
                .formKeys()
                .testTag("smb-anonymous-toggle"),
        )
        if (!state.anonymous) {
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                TvTextField(
                    value = state.user,
                    onValueChange = { state.user = it },
                    label = "用户名",
                    containerModifier = Modifier.weight(1f).formKeys(),
                    modifier = Modifier.formFocus(5),
                    inlineLabel = true,
                )
                TvTextField(
                    value = state.password,
                    onValueChange = { state.password = it },
                    label = "密码",
                    containerModifier = Modifier.weight(1f).formKeys(),
                    modifier = Modifier.formFocus(6),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    inlineLabel = true,
                )
            }
        }
        TvTextField(
            value = state.domain,
            onValueChange = { state.domain = it },
            label = "域（可选）",
            containerModifier = Modifier.fillMaxWidth().formKeys(),
            modifier = Modifier.formFocus(7, downOverride = cancelActionFocus),
            imeAction = ImeAction.Done,
            inlineLabel = true,
        )
    }
}
