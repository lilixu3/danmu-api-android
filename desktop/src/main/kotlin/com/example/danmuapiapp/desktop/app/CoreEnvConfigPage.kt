package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.desktop.core.CoreEnvConfiguredFilter
import com.example.danmuapiapp.desktop.core.CoreEnvDefinition
import com.example.danmuapiapp.desktop.core.CoreEnvFilter
import com.example.danmuapiapp.desktop.core.CoreEnvSnapshot
import com.example.danmuapiapp.desktop.core.CoreEnvType
import com.example.danmuapiapp.desktop.core.CoreEnvValue
import com.example.danmuapiapp.desktop.core.CoreEnvValueSource
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import com.example.danmuapiapp.desktop.core.applyModeLabel
import com.example.danmuapiapp.desktop.core.categoryOptions
import com.example.danmuapiapp.desktop.core.coreEnvCategoryLabel
import com.example.danmuapiapp.desktop.core.filteredDefinitions
import com.example.danmuapiapp.desktop.core.groupedDefinitions
import com.example.danmuapiapp.desktop.core.label
import com.example.danmuapiapp.desktop.core.maskCoreEnvValue
import com.example.danmuapiapp.desktop.core.typeLabel
import com.example.danmuapiapp.desktop.core.typeOptions
import com.example.danmuapiapp.desktop.runtime.DesktopCoreEnvRepository
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoreEnvConfigPage(
    controller: DesktopRuntimeController,
    paths: DesktopPaths,
    state: ServiceUiState,
) {
    val runtime = controller.configuredRuntime()
    val variant = DesktopCoreVariant.fromKey(runtime.variant)
    val repository = remember(paths.runtimeDir.absolutePath) {
        DesktopCoreEnvRepository(java.io.File(paths.runtimeDir, "nodejs-project"))
    }
    val scope = rememberCoroutineScope()
    var snapshot by remember(variant, paths.runtimeDir.absolutePath) { mutableStateOf<CoreEnvSnapshot?>(null) }
    var error by remember(variant, paths.runtimeDir.absolutePath) { mutableStateOf<String?>(null) }
    var busy by remember(variant, paths.runtimeDir.absolutePath) { mutableStateOf(false) }
    var filter by remember(variant, paths.runtimeDir.absolutePath) { mutableStateOf(CoreEnvFilter()) }
    var selectedKey by remember(variant, paths.runtimeDir.absolutePath) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CoreEnvDefinition?>(null) }
    var narrowDetailVisible by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var showPath by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var typeMenuOpen by remember { mutableStateOf(false) }
    var configuredMenuOpen by remember { mutableStateOf(false) }

    fun reload() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { repository.readSnapshot(variant) }
                snapshot = loaded
                val visible = loaded.filteredDefinitions(filter)
                selectedKey = selectedKey?.takeIf { key -> visible.any { it.key == key } }
                    ?: visible.firstOrNull()?.key
            } catch (failure: Throwable) {
                snapshot = null
                selectedKey = null
                error = failure.message?.trim()?.takeIf { it.isNotEmpty() } ?: failure::class.java.simpleName
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(variant, paths.runtimeDir.absolutePath) { reload() }

    val current = snapshot
    val filtered = current?.filteredDefinitions(filter).orEmpty()
    val selected = current?.values?.get(selectedKey)?.takeIf { it.definition.key in filtered.map { definition -> definition.key } }
    val groups = current?.groupedDefinitions(filter).orEmpty()

    fun updateFilter(next: CoreEnvFilter) {
        filter = next
        val nextVisible = snapshot?.filteredDefinitions(next).orEmpty()
        selectedKey = selectedKey?.takeIf { key -> nextVisible.any { it.key == key } } ?: nextVisible.firstOrNull()?.key
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(DesktopTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
    ) {
        CoreEnvToolbar(
            variant = variant,
            snapshot = current,
            busy = busy,
            filter = filter,
            showPath = showPath,
            onShowPathChange = { showPath = it },
            onQueryChange = { updateFilter(filter.copy(query = it)) },
            onCategoryMenuChange = { categoryMenuOpen = it },
            onTypeMenuChange = { typeMenuOpen = it },
            onConfiguredMenuChange = { configuredMenuOpen = it },
            categoryMenuOpen = categoryMenuOpen,
            typeMenuOpen = typeMenuOpen,
            configuredMenuOpen = configuredMenuOpen,
            categories = current?.categoryOptions().orEmpty(),
            types = current?.typeOptions().orEmpty(),
            onCategoryChange = { updateFilter(filter.copy(category = it)); categoryMenuOpen = false },
            onTypeChange = { updateFilter(filter.copy(type = it)); typeMenuOpen = false },
            onConfiguredChange = { updateFilter(filter.copy(configured = it)); configuredMenuOpen = false },
            onReload = ::reload,
        )

        if (current == null) {
            DesktopSurface(modifier = Modifier.fillMaxSize()) {
                if (busy) {
                    Column(
                        Modifier.fillMaxWidth().padding(DesktopTokens.CardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Text("正在读取当前核心的 envs.js 和 config/.env…")
                    }
                } else {
                    DesktopEmptyState(
                        title = "当前核心配置不可用",
                        description = "${error ?: "请先在核心页面准备 ${variant.label}。"}\n配置页不会自动下载核心，也不会猜测变量。",
                        icon = DesktopIcons.Warning,
                        action = { DesktopActionButton("重新读取", ::reload, style = DesktopActionButtonStyle.Outlined, icon = DesktopIcons.Restart) },
                    )
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 900.dp
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
                    ) {
                        CoreEnvListPane(
                            snapshot = current,
                            groups = groups,
                            selectedKey = selectedKey,
                            resultCount = filtered.size,
                            onSelect = { selectedKey = it },
                            modifier = Modifier.weight(1.15f).fillMaxHeight(),
                        )
                        CoreEnvDetailPane(
                            value = selected,
                            onEdit = { selected?.let { editing = it.definition } },
                            modifier = Modifier.weight(0.85f).fillMaxHeight(),
                        )
                    }
                } else {
                    CoreEnvListPane(
                        snapshot = current,
                        groups = groups,
                        selectedKey = selectedKey,
                        resultCount = filtered.size,
                        onSelect = {
                            selectedKey = it
                            narrowDetailVisible = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }

    if (narrowDetailVisible && selected != null) {
        CoreEnvDetailDialog(
            value = selected,
            onDismiss = { narrowDetailVisible = false },
            onEdit = {
                narrowDetailVisible = false
                editing = selected.definition
            },
        )
    }

    editing?.let { definition ->
        val currentSnapshot = snapshot ?: return@let
        CoreEnvEditDialog(
            snapshot = currentSnapshot,
            definition = definition,
            repository = repository,
            serviceState = state,
            onDismiss = { editing = null },
            onSaved = { message ->
                editing = null
                feedback = message
                reload()
            },
        )
    }
}

@Composable
private fun CoreEnvToolbar(
    variant: DesktopCoreVariant,
    snapshot: CoreEnvSnapshot?,
    busy: Boolean,
    filter: CoreEnvFilter,
    showPath: Boolean,
    onShowPathChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    categoryMenuOpen: Boolean,
    typeMenuOpen: Boolean,
    configuredMenuOpen: Boolean,
    onCategoryMenuChange: (Boolean) -> Unit,
    onTypeMenuChange: (Boolean) -> Unit,
    onConfiguredMenuChange: (Boolean) -> Unit,
    categories: List<String>,
    types: List<CoreEnvType>,
    onCategoryChange: (String?) -> Unit,
    onTypeChange: (CoreEnvType?) -> Unit,
    onConfiguredChange: (CoreEnvConfiguredFilter) -> Unit,
    onReload: () -> Unit,
) {
    DesktopSurface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(DesktopTokens.CompactCardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("核心配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        DesktopStatusBadge(DesktopStatus.Info, variant.label, compact = true)
                    }
                    Text(
                        snapshot?.let { "动态读取 ${it.definitions.size} 个变量 · 已配置 ${it.configuredCount} 个" } ?: "等待读取当前核心变量定义…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DesktopActionButton(
                    label = if (busy) "读取中…" else "重新读取",
                    onClick = onReload,
                    enabled = !busy,
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Restart,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("搜索变量") },
                    placeholder = { Text("变量名、说明或分类") },
                )
                Box {
                    DesktopActionButton(
                        label = filter.category?.let(::coreEnvCategoryLabel) ?: "全部分类",
                        onClick = { onCategoryMenuChange(true) },
                        style = DesktopActionButtonStyle.Outlined,
                    )
                    DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { onCategoryMenuChange(false) }) {
                        DropdownMenuItem(text = { Text("全部分类") }, onClick = { onCategoryChange(null) })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(coreEnvCategoryLabel(category)) }, onClick = { onCategoryChange(category) })
                        }
                    }
                }
                Box {
                    DesktopActionButton(
                        label = filter.type?.label() ?: "全部类型",
                        onClick = { onTypeMenuChange(true) },
                        style = DesktopActionButtonStyle.Outlined,
                    )
                    DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { onTypeMenuChange(false) }) {
                        DropdownMenuItem(text = { Text("全部类型") }, onClick = { onTypeChange(null) })
                        types.forEach { type -> DropdownMenuItem(text = { Text(type.label()) }, onClick = { onTypeChange(type) }) }
                    }
                }
                Box {
                    DesktopActionButton(
                        label = filter.configured.label,
                        onClick = { onConfiguredMenuChange(true) },
                        style = DesktopActionButtonStyle.Outlined,
                    )
                    DropdownMenu(expanded = configuredMenuOpen, onDismissRequest = { onConfiguredMenuChange(false) }) {
                        CoreEnvConfiguredFilter.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { onConfiguredChange(option) })
                        }
                    }
                }
            }
            if (snapshot != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onShowPathChange(!showPath) }) {
                        Text(if (showPath) "隐藏配置路径" else "显示配置路径")
                    }
                    Text(
                        "匹配 ${snapshot.filteredDefinitions(filter).size} / ${snapshot.definitions.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showPath) {
                    Text(
                        snapshot.envFile.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreEnvListPane(
    snapshot: CoreEnvSnapshot,
    groups: List<com.example.danmuapiapp.desktop.core.CoreEnvGroup>,
    selectedKey: String?,
    resultCount: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSurface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("变量列表", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("$resultCount 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DesktopDivider()
            if (groups.isEmpty()) {
                DesktopEmptyState("没有匹配的变量", "调整搜索或筛选条件后重试。", icon = DesktopIcons.Empty, modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "header:${group.category}") {
                            Text(
                                coreEnvCategoryLabel(group.category),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(group.definitions, key = { it.key }) { definition ->
                            val value = snapshot.values.getValue(definition.key)
                            CoreEnvListItem(
                                value = value,
                                selected = definition.key == selectedKey,
                                onClick = { onSelect(definition.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreEnvListItem(value: CoreEnvValue, selected: Boolean, onClick: () -> Unit) {
    DesktopSurface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = DesktopTokens.ItemShape,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    value.definition.key,
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DesktopStatusBadge(
                    status = if (value.isConfigured) DesktopStatus.Success else DesktopStatus.Neutral,
                    label = if (value.isConfigured) "已配置" else "默认",
                    compact = true,
                )
            }
            Text(
                value.definition.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${value.definition.typeLabel()} · ${coreEnvCategoryLabel(value.definition.category)} · ${value.definition.applyModeLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CoreEnvDetailPane(value: CoreEnvValue?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    DesktopSurface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        if (value == null) {
            DesktopEmptyState(
                title = "选择一个变量",
                description = "左侧列表会显示当前核心声明的全部环境变量；选择后在此查看说明和编辑入口。",
                icon = DesktopIcons.Tools,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DesktopTokens.CardPadding),
                verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
            ) {
                CoreEnvDetailContent(value)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopActionButton("编辑变量", onEdit, icon = DesktopIcons.Tools)
                    if (value.isConfigured) {
                        Text("恢复默认值可在编辑窗口中执行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreEnvDetailContent(value: CoreEnvValue) {
    val definition = value.definition
    Text(definition.key, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    Text(definition.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DesktopDivider()
    DesktopInfoRow("分类", coreEnvCategoryLabel(definition.category))
    DesktopDivider()
    DesktopInfoRow("类型", definition.typeLabel())
    DesktopDivider()
    DesktopInfoRow("当前值", maskCoreEnvValue(value), monospace = !definition.sensitive)
    DesktopDivider()
    DesktopInfoRow("来源", value.source.label())
    DesktopDivider()
    DesktopInfoRow("核心默认", if (definition.sensitive) "已隐藏" else definition.defaultValue ?: "未声明")
    DesktopDivider()
    DesktopInfoRow("生效方式", definition.applyModeLabel())
    if (definition.options.isNotEmpty()) {
        DesktopDivider()
        DesktopInfoRow("可选值", definition.options.joinToString(", "))
    }
    if (definition.min != null || definition.max != null) {
        DesktopDivider()
        DesktopInfoRow("数值范围", listOfNotNull(definition.min?.let { "最小 $it" }, definition.max?.let { "最大 $it" }).joinToString(" · "))
    }
}

@Composable
private fun CoreEnvDetailDialog(value: CoreEnvValue, onDismiss: () -> Unit, onEdit: () -> Unit) {
    DesktopDialogFrame(
        spec = DesktopDialogSpec(
            title = value.definition.key,
            description = value.definition.description,
            tone = if (value.definition.sensitive) DesktopDialogTone.Warning else DesktopDialogTone.Info,
            dismissOnClickOutside = true,
        ),
        onDismissRequest = onDismiss,
        leadingIcon = if (value.definition.sensitive) DesktopIcons.Warning else DesktopIcons.Tools,
        content = { CoreEnvDetailContent(value) },
        actions = {
            DesktopDialogButton(DesktopDialogAction("关闭"), onDismiss)
            DesktopDialogButton(DesktopDialogAction("编辑", isPrimary = true), onClick = onEdit)
        },
    )
}

@Composable
private fun CoreEnvEditDialog(
    snapshot: CoreEnvSnapshot,
    definition: CoreEnvDefinition,
    repository: DesktopCoreEnvRepository,
    serviceState: ServiceUiState,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val existing = snapshot.values.getValue(definition.key)
    var input by remember(definition.key, existing.configuredValue, existing.effectiveValue) { mutableStateOf(existing.configuredValue ?: existing.effectiveValue.orEmpty()) }
    var visible by remember(definition.key) { mutableStateOf(false) }
    var saving by remember(definition.key) { mutableStateOf(false) }
    var dialogError by remember(definition.key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DesktopDialogFrame(
        spec = DesktopDialogSpec(
            title = definition.key,
            description = definition.description,
            tone = if (definition.sensitive) DesktopDialogTone.Warning else DesktopDialogTone.Info,
            dismissOnClickOutside = !saving,
            dismissOnEscape = !saving,
        ),
        onDismissRequest = onDismiss,
        leadingIcon = if (definition.sensitive) DesktopIcons.Warning else DesktopIcons.Tools,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DesktopInfoRow("类型", definition.typeLabel())
                DesktopDivider()
                DesktopInfoRow("核心默认值", if (definition.sensitive) "已隐藏" else definition.defaultValue ?: "未声明默认值")
                DesktopDivider()
                DesktopInfoRow("当前有效值", if (definition.sensitive) "已隐藏" else existing.effectiveValue ?: "未配置")
                DesktopDivider()
                DesktopInfoRow("生效方式", if (serviceState.phase == ServicePhase.Running) "已写入后：${definition.applyModeLabel()}" else "服务未运行，下次启动时读取")
                when (definition.type) {
                    CoreEnvType.Boolean -> SwitchField(input == "true") { input = it.toString() }
                    CoreEnvType.Select -> definition.options.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = input == option, onClick = { input = option })
                            Text(option)
                        }
                    }
                    CoreEnvType.MultiSelect -> definition.options.forEach { option ->
                        val selected = input.split(',').map { it.trim() }.contains(option)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = {
                                val values = input.split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                                if (it) values.add(option) else values.remove(option)
                                input = values.distinct().joinToString(",")
                            })
                            Text(option)
                        }
                    }
                    CoreEnvType.Number -> OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("数值") }, supportingText = { Text(listOfNotNull(definition.min?.let { "最小 $it" }, definition.max?.let { "最大 $it" }).joinToString(" · ")) })
                    else -> OutlinedTextField(input, { input = it }, Modifier.fillMaxWidth().height(150.dp), maxLines = 8, label = { Text("配置值") }, visualTransformation = if (definition.sensitive && !visible) PasswordVisualTransformation() else VisualTransformation.None)
                }
                if (definition.sensitive) TextButton({ visible = !visible }) { Text(if (visible) "隐藏敏感值" else "显示敏感值") }
                dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        actions = {
            DesktopDialogButton(DesktopDialogAction("取消"), onDismiss)
            DesktopDialogButton(DesktopDialogAction("恢复默认", tone = DesktopDialogTone.Warning), onClick = {
                if (saving) return@DesktopDialogButton
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.deleteValue(snapshot, definition.key) }
                        onSaved(if (serviceState.phase == ServicePhase.Running) "${definition.key} 已删除显式配置，核心将按热加载机制恢复默认值。" else "${definition.key} 已删除显式配置，下次启动时使用默认值。")
                    } catch (failure: Throwable) {
                        dialogError = failure.message ?: failure::class.java.simpleName
                        saving = false
                    }
                }
            })
            DesktopDialogButton(DesktopDialogAction("保存", isPrimary = true), onClick = {
                if (saving) return@DesktopDialogButton
                saving = true
                dialogError = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.updateValue(snapshot, definition.key, input) }
                        onSaved(if (serviceState.phase == ServicePhase.Running) "${definition.key} 已写入当前核心 .env，核心将按自身热加载机制刷新。" else "${definition.key} 已保存；服务未运行，下次启动时生效。")
                    } catch (failure: Throwable) {
                        dialogError = failure.message ?: failure::class.java.simpleName
                        saving = false
                    }
                }
            })
        },
    )
}

@Composable
private fun SwitchField(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(if (checked) "true" else "false", fontFamily = FontFamily.Monospace)
    }
}
