package com.example.danmuapiapp.ui.screen.config

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.EnvType
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val KEY_SOURCE_ORDER = "SOURCE_ORDER"
private const val KEY_BILIBILI_COOKIE = "BILIBILI_COOKIE"
private const val KEY_MERGE_SOURCE_PAIRS = "MERGE_SOURCE_PAIRS"
private const val KEY_PLATFORM_ORDER = "PLATFORM_ORDER"
private const val KEY_TITLE_MAPPING_TABLE = "TITLE_MAPPING_TABLE"
private const val KEY_TITLE_PLATFORM_OFFSET_TABLE = "TITLE_PLATFORM_OFFSET_TABLE"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val envVars by viewModel.envVars.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsStateWithLifecycle()
    val rawContent by viewModel.rawContent.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val editingVar by viewModel.editingVar.collectAsStateWithLifecycle()
    val isRawMode by viewModel.isRawMode.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.reload()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredCatalog = remember(catalog, searchQuery, envVars) {
        val q = searchQuery.lowercase()
        val items = if (catalog.isNotEmpty()) catalog else {
            envVars.keys.map { key -> EnvVarDef(key, "other", EnvType.TEXT, key) }
        }
        if (q.isBlank()) items
        else items.filter {
            it.key.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    val grouped = remember(filteredCatalog) { filteredCatalog.groupBy { it.category } }
    val configuredCount = envVars.size
    val totalCount = filteredCatalog.size

    val categoryLabels = mapOf(
        "api" to "API 配置",
        "source" to "数据源",
        "match" to "匹配",
        "danmu" to "弹幕",
        "cache" to "缓存",
        "system" to "系统",
        "vod" to "VOD",
        "bilibili" to "Bilibili",
        "proxy" to "代理",
        "log" to "日志",
        "other" to "其他"
    )

    if (editingVar != null && adminState.isAdminMode) {
        EnvVarEditDialog(
            def = editingVar!!,
            currentValue = envVars[editingVar!!.key] ?: "",
            onSave = { value ->
                viewModel.setValue(editingVar!!.key, value)
                viewModel.closeEditor()
            },
            onDelete = {
                viewModel.deleteKey(editingVar!!.key)
                viewModel.closeEditor()
            },
            onDismiss = { viewModel.closeEditor() },
            onGenerateBiliQr = { viewModel.generateBilibiliQr() },
            onPollBiliQr = { key -> viewModel.pollBilibiliQr(key) },
            onVerifyBiliCookie = { cookie -> viewModel.verifyBilibiliCookie(cookie) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                }
                Column {
                    Text("配置管理", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "$totalCount 项配置 · $configuredCount 已设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = { viewModel.toggleRawMode() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isRawMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.Code,
                        if (isRawMode) "可视化模式" else "源码模式",
                        Modifier.size(18.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = { viewModel.reload() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!adminState.isAdminMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.AdminPanelSettings,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "当前为只读模式",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "请先开启管理员模式后再编辑敏感配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenAdminMode) {
                        Text("去开启")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (isRawMode) {
            RawEditMode(
                rawContent = rawContent,
                onSave = { viewModel.saveRawContent(it) },
                envFilePath = viewModel.getEnvFilePath(),
                editable = adminState.isAdminMode,
                modifier = Modifier.weight(1f)
            )
        } else {
            VisualEditMode(
                grouped = grouped,
                categoryLabels = categoryLabels,
                envVars = envVars,
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearch(it) },
                onEditVar = { viewModel.openEditor(it) },
                editable = adminState.isAdminMode,
                isCatalogLoading = isCatalogLoading,
                catalogEmpty = catalog.isEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VisualEditMode(
    grouped: Map<String, List<EnvVarDef>>,
    categoryLabels: Map<String, String>,
    envVars: Map<String, String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onEditVar: (EnvVarDef) -> Unit,
    editable: Boolean,
    isCatalogLoading: Boolean,
    catalogEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("搜索变量名、描述或分类") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isCatalogLoading) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "正在加载配置目录...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (catalogEmpty) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            "未找到 envs.js，显示 .env 中已有变量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        grouped.forEach { (category, defs) ->
            item {
                Text(
                    categoryLabels[category] ?: category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(defs, key = { it.key }) { def ->
                EnvVarCard(
                    def = def,
                    currentValue = envVars[def.key],
                    enabled = editable,
                    onClick = { onEditVar(def) }
                )
            }
        }
    }
}

@Composable
private fun RawEditMode(
    rawContent: String,
    onSave: (String) -> Unit,
    envFilePath: String,
    editable: Boolean,
    modifier: Modifier = Modifier
) {
    var text by remember(rawContent) { mutableStateOf(rawContent) }
    val hasChanges = text != rawContent

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            envFilePath,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            readOnly = !editable,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            )
        )

        FilledTonalButton(
            onClick = { onSave(text) },
            enabled = hasChanges && editable,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("保存")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun EnvVarCard(
    def: EnvVarDef,
    currentValue: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    def.key,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (def.description != def.key) {
                    Text(
                        def.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (currentValue != null) {
                    Text(
                        if (def.sensitive) "••••••••" else currentValue.take(60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        "未设置",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvVarEditDialog(
    def: EnvVarDef,
    currentValue: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onGenerateBiliQr: suspend () -> Result<BilibiliQrGenerateResult>,
    onPollBiliQr: suspend (String) -> Result<BilibiliQrPollResult>,
    onVerifyBiliCookie: suspend (String) -> Result<BilibiliCookieVerifyResult>,
) {
    var value by remember(def.key, currentValue) { mutableStateOf(currentValue) }
    var showPassword by remember(def.key) { mutableStateOf(false) }
    var showDeleteConfirm by remember(def.key) { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要从 .env 中删除 ${def.key} 吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
        return
    }

    val normalizedKey = remember(def.key) { def.key.trim().uppercase(Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(def.key, style = MaterialTheme.typography.titleMedium)
                if (def.description != def.key) {
                    Text(
                        def.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val handledSpecial = when (normalizedKey) {
                    KEY_SOURCE_ORDER -> {
                        OrderedTokenEditor(
                            value = value,
                            onValueChange = { value = it },
                            options = def.options,
                            title = "来源排序",
                            tokenLabel = "来源"
                        )
                        true
                    }

                    KEY_PLATFORM_ORDER -> {
                        OrderedTokenEditor(
                            value = value,
                            onValueChange = { value = it },
                            options = def.options,
                            title = "平台排序",
                            tokenLabel = "平台"
                        )
                        true
                    }

                    KEY_MERGE_SOURCE_PAIRS -> {
                        MergeSourcePairsEditor(
                            value = value,
                            onValueChange = { value = it },
                            options = def.options
                        )
                        true
                    }

                    KEY_TITLE_MAPPING_TABLE -> {
                        TitleMappingTableEditor(
                            value = value,
                            onValueChange = { value = it }
                        )
                        true
                    }

                    KEY_TITLE_PLATFORM_OFFSET_TABLE -> {
                        TitlePlatformOffsetTableEditor(
                            value = value,
                            onValueChange = { value = it },
                            options = def.options
                        )
                        true
                    }

                    KEY_BILIBILI_COOKIE -> {
                        BilibiliCookieEditor(
                            value = value,
                            onValueChange = { value = it },
                            onGenerateBiliQr = onGenerateBiliQr,
                            onPollBiliQr = onPollBiliQr,
                            onVerifyBiliCookie = onVerifyBiliCookie
                        )
                        true
                    }

                    else -> false
                }

                if (!handledSpecial) {
                    when (def.type) {
                        EnvType.BOOLEAN -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("启用", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = value.lowercase().let { it == "true" || it == "1" },
                                    onCheckedChange = { value = if (it) "true" else "false" }
                                )
                            }
                        }

                        EnvType.SELECT -> {
                            if (def.options.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    def.options.forEach { option ->
                                        FilterChip(
                                            selected = value == option,
                                            onClick = { value = option },
                                            label = { Text(option) }
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { value = it },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        EnvType.MULTI_SELECT -> {
                            val selected = remember(value) {
                                parseCsvTokens(value).toMutableStateList()
                            }
                            if (def.options.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    def.options.forEach { option ->
                                        val isSelected = option in selected
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected) selected.remove(option)
                                                else selected.add(option)
                                                value = selected.joinToString(",")
                                            },
                                            label = { Text(option) }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text("值（逗号分隔）") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        else -> {}
                    }

                    when (def.type) {
                        EnvType.NUMBER -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text("值") },
                                supportingText = {
                                    val hints = buildList {
                                        if (def.min != null) add("最小: ${def.min}")
                                        if (def.max != null) add("最大: ${def.max}")
                                    }
                                    if (hints.isNotEmpty()) Text(hints.joinToString("  "))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        EnvType.TEXT -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text("值") },
                                visualTransformation = if (def.sensitive && !showPassword) {
                                    PasswordVisualTransformation()
                                } else {
                                    VisualTransformation.None
                                },
                                trailingIcon = if (def.sensitive) {
                                    {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                "切换可见"
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = false,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        EnvType.MAP, EnvType.COLOR_LIST -> {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = { Text("值") },
                                singleLine = false,
                                minLines = 3,
                                maxLines = 8,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        else -> {}
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentValue.isNotEmpty()) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
                FilledTonalButton(onClick = { onSave(value) }) { Text("保存") }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderedTokenEditor(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    title: String,
    tokenLabel: String
) {
    val selected = remember(value) { parseCsvTokens(value) }
    val available = remember(options, selected) {
        options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { selected.contains(it) }
    }
    var customInput by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "顺序会直接影响匹配优先级。上移/下移可以快速调整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selected.isEmpty()) {
                Text(
                    "暂无已选 $tokenLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                selected.forEachIndexed { index, item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    modifier = Modifier.size(22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )

                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        onValueChange(
                                            selected.move(index, index - 1).joinToString(",")
                                        )
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "上移")
                            }
                            IconButton(
                                onClick = {
                                    if (index < selected.lastIndex) {
                                        onValueChange(
                                            selected.move(index, index + 1).joinToString(",")
                                        )
                                    }
                                },
                                enabled = index < selected.lastIndex
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "下移")
                            }
                            IconButton(
                                onClick = {
                                    onValueChange(selected.filterIndexed { i, _ -> i != index }.joinToString(","))
                                }
                            ) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }
                    }
                }
            }

            if (available.isNotEmpty()) {
                Text(
                    "可选项（点击添加）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    available.forEach { option ->
                        AssistChip(
                            onClick = { onValueChange((selected + option).joinToString(",")) },
                            label = { Text(option) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text("自定义 $tokenLabel") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        val token = customInput.trim()
                        if (token.isNotBlank() && !selected.contains(token)) {
                            onValueChange((selected + token).joinToString(","))
                            customInput = ""
                        }
                    }
                ) {
                    Text("添加")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MergeSourcePairsEditor(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>
) {
    val groups = remember(value) { parseCsvTokens(value) }
    val allOptions = remember(options) {
        options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    var staging by remember { mutableStateOf<List<String>>(emptyList()) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("源合并组配置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "规则格式：源1&源2。多个组使用逗号分隔。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (groups.isEmpty()) {
                Text(
                    "暂无合并组",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                groups.forEachIndexed { index, group ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                group,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        onValueChange(groups.move(index, index - 1).joinToString(","))
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "上移")
                            }
                            IconButton(
                                onClick = {
                                    if (index < groups.lastIndex) {
                                        onValueChange(groups.move(index, index + 1).joinToString(","))
                                    }
                                },
                                enabled = index < groups.lastIndex
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "下移")
                            }
                            IconButton(
                                onClick = {
                                    onValueChange(groups.filterIndexed { i, _ -> i != index }.joinToString(","))
                                }
                            ) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }
                    }
                }
            }

            Text(
                if (staging.isEmpty()) "暂存区：未选择" else "暂存区：${staging.joinToString(" & ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (staging.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    staging.forEach { token ->
                        FilterChip(
                            selected = true,
                            onClick = { staging = staging.filterNot { it == token } },
                            label = { Text("$token ×") }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (staging.isNotEmpty()) {
                            val group = staging.joinToString("&")
                            if (!groups.contains(group)) {
                                onValueChange((groups + group).joinToString(","))
                            }
                            staging = emptyList()
                        }
                    },
                    enabled = staging.isNotEmpty()
                ) {
                    Text("添加分组")
                }
                OutlinedButton(
                    onClick = { staging = emptyList() },
                    enabled = staging.isNotEmpty()
                ) {
                    Text("清空暂存")
                }
            }

            if (allOptions.isNotEmpty()) {
                Text(
                    "可选项（点击加入暂存区）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allOptions.forEach { option ->
                        AssistChip(
                            onClick = {
                                if (!staging.contains(option)) {
                                    staging = staging + option
                                }
                            },
                            label = { Text(option) },
                            enabled = !staging.contains(option)
                        )
                    }
                }
            } else {
                Text(
                    "未读取到可选项，可直接在原始值里手工输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class TitleMappingRow(
    val left: String = "",
    val right: String = "",
)

@Composable
private fun TitleMappingTableEditor(
    value: String,
    onValueChange: (String) -> Unit
) {
    var rows by remember { mutableStateOf(parseTitleMappingRows(value).ifEmpty { listOf(TitleMappingRow()) }) }

    fun syncRows(next: List<TitleMappingRow>) {
        rows = next
        onValueChange(serializeTitleMappingRows(next))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("剧名映射表", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "格式：原始剧名 -> 映射剧名，保存时自动转为 left->right;left->right。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "映射 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) syncRows(rows.move(index, index - 1))
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Rounded.KeyboardArrowUp, "上移") }
                            IconButton(
                                onClick = {
                                    if (index < rows.lastIndex) syncRows(rows.move(index, index + 1))
                                },
                                enabled = index < rows.lastIndex
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, "下移") }
                            IconButton(
                                onClick = {
                                    val next = rows.filterIndexed { i, _ -> i != index }
                                    syncRows(if (next.isEmpty()) listOf(TitleMappingRow()) else next)
                                }
                            ) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                        }

                        OutlinedTextField(
                            value = row.left,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(left = text)))
                            },
                            label = { Text("原始剧名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = row.right,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(right = text)))
                            },
                            label = { Text("映射剧名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { syncRows(rows + TitleMappingRow()) }) { Text("新增映射") }
                OutlinedButton(
                    onClick = {
                        val parsed = parseTitleMappingRows(value)
                        rows = if (parsed.isEmpty()) listOf(TitleMappingRow()) else parsed
                    }
                ) {
                    Text("从原始值解析")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class TitleOffsetRow(
    val title: String = "",
    val platformsRaw: String = "",
    val offset: String = "",
)

private data class PlatformPickerState(
    val rowIndex: Int,
    val selected: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitlePlatformOffsetTableEditor(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>
) {
    val platformOptions = remember(options) { normalizePlatformOptions(options) }
    var rows by remember { mutableStateOf(parseTitleOffsetRows(value).ifEmpty { listOf(TitleOffsetRow()) }) }
    var platformPicker by remember { mutableStateOf<PlatformPickerState?>(null) }

    fun syncRows(next: List<TitleOffsetRow>) {
        rows = next
        onValueChange(serializeTitleOffsetRows(next))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("剧名平台偏移表", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "格式：剧名@平台1&平台2@偏移秒，多个规则使用分号分隔。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
                val selectedPlatforms = parsePlatformTokens(row.platformsRaw)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "规则 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) syncRows(rows.move(index, index - 1))
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Rounded.KeyboardArrowUp, "上移") }
                            IconButton(
                                onClick = {
                                    if (index < rows.lastIndex) syncRows(rows.move(index, index + 1))
                                },
                                enabled = index < rows.lastIndex
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, "下移") }
                            IconButton(
                                onClick = {
                                    val next = rows.filterIndexed { i, _ -> i != index }
                                    syncRows(if (next.isEmpty()) listOf(TitleOffsetRow()) else next)
                                }
                            ) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                        }

                        OutlinedTextField(
                            value = row.title,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(title = text)))
                            },
                            label = { Text("剧名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = row.offset,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(offset = text)))
                            },
                            label = { Text("偏移秒（可负数）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (platformOptions.isNotEmpty()) {
                            val selectedSummary = formatPlatformSummary(selectedPlatforms)
                            Text(
                                "平台（可多选，all 表示全部）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "已选平台：$selectedSummary",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (selectedPlatforms.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            selectedPlatforms.forEach { platform ->
                                                AssistChip(
                                                    onClick = {},
                                                    label = {
                                                        Text(if (platform.equals("all", true)) "全部" else platform)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        platformPicker = PlatformPickerState(
                                            rowIndex = index,
                                            selected = selectedPlatforms
                                        )
                                    }
                                ) {
                                    Text("选择平台")
                                }
                                OutlinedButton(
                                    onClick = {
                                        syncRows(rows.replace(index, row.copy(platformsRaw = "")))
                                    },
                                    enabled = selectedPlatforms.isNotEmpty()
                                ) {
                                    Text("清空")
                                }
                            }

                            Text(
                                "提示：选择“全部”后会自动清空其它平台，避免配置冲突。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedTextField(
                                value = row.platformsRaw,
                                onValueChange = { text ->
                                    syncRows(rows.replace(index, row.copy(platformsRaw = text)))
                                },
                                label = { Text("平台（使用 & 分隔）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { syncRows(rows + TitleOffsetRow()) }) { Text("新增规则") }
                OutlinedButton(
                    onClick = {
                        val parsed = parseTitleOffsetRows(value)
                        rows = if (parsed.isEmpty()) listOf(TitleOffsetRow()) else parsed
                    }
                ) {
                    Text("从原始值解析")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val picker = platformPicker
    if (picker != null) {
        PlatformMultiSelectDialog(
            options = platformOptions,
            initialSelected = picker.selected,
            onDismiss = { platformPicker = null },
            onConfirm = { selected ->
                val rowIndex = picker.rowIndex
                if (rowIndex !in rows.indices) {
                    platformPicker = null
                    return@PlatformMultiSelectDialog
                }
                val normalized = canonicalizePlatformSelection(selected, platformOptions)
                val platformsRaw = normalizePlatforms(normalized.joinToString("&"))
                syncRows(rows.replace(rowIndex, rows[rowIndex].copy(platformsRaw = platformsRaw)))
                platformPicker = null
            }
        )
    }
}

@Composable
private fun PlatformMultiSelectDialog(
    options: List<String>,
    initialSelected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember(options, initialSelected) {
        mutableStateOf(canonicalizePlatformSelection(initialSelected, options))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择平台") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "可多选。若选择“全部”，其它平台将自动取消。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                options.forEach { option ->
                    val normalizedOption = option.trim().ifBlank { option }
                    val isAll = normalizedOption.equals("all", true)
                    val isSelected = selected.any { it.equals(normalizedOption, true) }
                    val hasAll = selected.any { it.equals("all", true) }
                    val canSelect = !hasAll || isAll || isSelected

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canSelect) {
                                selected = togglePlatformSelection(selected, normalizedOption, options)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    selected = togglePlatformSelection(selected, normalizedOption, options)
                                },
                                enabled = canSelect
                            )
                            Text(
                                text = if (isAll) "全部" else normalizedOption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (isAll) {
                                Text(
                                    "all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Text(
                    "当前选择：${formatPlatformSummary(selected)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(selected) }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun BilibiliCookieEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onGenerateBiliQr: suspend () -> Result<BilibiliQrGenerateResult>,
    onPollBiliQr: suspend (String) -> Result<BilibiliQrPollResult>,
    onVerifyBiliCookie: suspend (String) -> Result<BilibiliCookieVerifyResult>,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var showCookie by remember { mutableStateOf(false) }

    var verifyLoading by remember { mutableStateOf(false) }
    var verifyResult by remember { mutableStateOf<BilibiliCookieVerifyResult?>(null) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var fallbackExpiresAtMs by remember { mutableStateOf<Long?>(null) }

    var qrVisible by remember { mutableStateOf(false) }
    var qrLoading by remember { mutableStateOf(false) }
    var qrStatus by remember { mutableStateOf("准备生成二维码...") }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrPollingJob by remember { mutableStateOf<Job?>(null) }
    var qrKey by remember { mutableStateOf<String?>(null) }

    val cookieSnapshot = remember(value) { parseCookieSnapshot(value) }
    val displayExpire = verifyResult?.expiresAtMs ?: fallbackExpiresAtMs ?: inferCookieExpiryMs(value)
    val statusTitle = when {
        value.isBlank() -> "未配置"
        verifyLoading -> "检测中"
        verifyResult?.isValid == true -> "Cookie 有效"
        verifyResult != null -> "Cookie 无效"
        cookieSnapshot.hasRequired -> "待校验"
        else -> "字段不完整"
    }
    val statusSubtitle = when {
        value.isBlank() -> "请扫码登录或粘贴完整 Cookie"
        verifyLoading -> "正在校验 Cookie 状态..."
        verifyResult?.isValid == true -> verifyResult?.message.ifNullOrBlank("账号可用")
        verifyResult != null -> verifyResult?.message.ifNullOrBlank("Cookie 已失效或不完整")
        cookieSnapshot.hasRequired -> "已检测到 SESSDATA / bili_jct，建议点“校验状态”确认"
        else -> "缺少必要字段（SESSDATA 或 bili_jct）"
    }

    fun closeQrDialog() {
        qrVisible = false
        qrPollingJob?.cancel()
        qrPollingJob = null
    }

    fun verifyNow(targetCookie: String = value) {
        val cookie = targetCookie.trim()
        if (cookie.isBlank()) {
            verifyResult = null
            verifyError = "请先输入 Cookie"
            return
        }
        verifyLoading = true
        verifyError = null
        scope.launch {
            val result = onVerifyBiliCookie(cookie)
            verifyLoading = false
            result.onSuccess {
                verifyResult = it
                if (!it.isValid) {
                    verifyError = it.message.ifBlank { "Cookie 无效" }
                }
            }.onFailure {
                verifyResult = null
                verifyError = it.message ?: "校验失败"
            }
        }
    }

    fun startQrFlow() {
        qrVisible = true
        qrLoading = true
        qrStatus = "正在生成二维码..."
        qrBitmap = null
        qrKey = null
        qrPollingJob?.cancel()
        qrPollingJob = scope.launch {
            val generate = onGenerateBiliQr()
            if (generate.isFailure) {
                qrLoading = false
                qrStatus = "生成失败：${generate.exceptionOrNull()?.message ?: "未知错误"}"
                return@launch
            }

            val data = generate.getOrNull()
            if (data == null || data.qrUrl.isBlank() || data.qrcodeKey.isBlank()) {
                qrLoading = false
                qrStatus = "生成失败：返回内容为空"
                return@launch
            }

            qrKey = data.qrcodeKey
            val px = with(density) { 220.dp.toPx().toInt().coerceAtLeast(220) }
            val bitmap = runCatching {
                withContext(Dispatchers.Default) { buildQrBitmap(data.qrUrl, px) }
            }.getOrElse {
                qrLoading = false
                qrStatus = "二维码渲染失败"
                return@launch
            }

            qrBitmap = bitmap
            qrLoading = false
            qrStatus = "请使用 B 站 App 扫码，并在手机确认登录"

            while (isActive) {
                delay(2000)
                val key = qrKey ?: break
                val pollResult = onPollBiliQr(key)
                if (pollResult.isFailure) {
                    qrStatus = "轮询失败，正在重试..."
                    continue
                }
                val poll = pollResult.getOrNull() ?: continue
                when (poll.code) {
                    86101 -> qrStatus = "等待扫码..."
                    86090 -> qrStatus = "已扫码，请在手机端确认"
                    86038 -> {
                        qrStatus = "二维码已过期，请刷新"
                        break
                    }

                    0 -> {
                        val mergedCookie = buildCookieWithRefreshToken(poll.cookie, poll.refreshToken)
                        if (mergedCookie.isBlank()) {
                            qrStatus = "登录成功但未返回 Cookie，请刷新后重试"
                            break
                        }
                        fallbackExpiresAtMs = poll.expiresAtMs
                        onValueChange(mergedCookie)
                        qrStatus = "登录成功，Cookie 已自动填入"
                        verifyNow(mergedCookie)
                        delay(600)
                        closeQrDialog()
                        break
                    }

                    else -> {
                        qrStatus = poll.message.ifBlank { "状态异常（${poll.code}）" }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (value.isNotBlank()) {
            verifyNow(value)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            qrPollingJob?.cancel()
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    value.isBlank() -> MaterialTheme.colorScheme.surfaceVariant
                    verifyLoading -> MaterialTheme.colorScheme.secondaryContainer
                    verifyResult?.isValid == true || cookieSnapshot.hasRequired -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        statusTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isValid == true || cookieSnapshot.hasRequired -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        statusSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            value.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                            verifyLoading -> MaterialTheme.colorScheme.onSecondaryContainer
                            verifyResult?.isValid == true || cookieSnapshot.hasRequired -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        }
                    )
                    Text(
                        "用户名：${verifyResult?.uname ?: "--"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "到期时间：${formatEpochMs(displayExpire)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                "检测到的字段：${cookieSnapshot.keys.joinToString("、").ifBlank { "无" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { startQrFlow() }) {
                    Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("扫码获取")
                }
                OutlinedButton(
                    onClick = { verifyNow(value) },
                    enabled = value.isNotBlank() && !verifyLoading
                ) {
                    if (verifyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("校验状态")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    verifyError = null
                },
                label = { Text("Bilibili Cookie") },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showCookie = !showCookie }) {
                        Icon(
                            if (showCookie) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "显示/隐藏"
                        )
                    }
                },
                singleLine = false,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "建议使用扫码登录自动获取。手动粘贴时请确保至少包含 SESSDATA 与 bili_jct。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!verifyError.isNullOrBlank()) {
                Text(
                    verifyError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (qrVisible) {
        AlertDialog(
            onDismissRequest = { closeQrDialog() },
            title = { Text("扫码登录 Bilibili") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (qrLoading) {
                        CircularProgressIndicator()
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!,
                            contentDescription = "Bilibili 登录二维码",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Text(
                        qrStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { startQrFlow() }) { Text("刷新二维码") }
            },
            dismissButton = {
                TextButton(onClick = { closeQrDialog() }) { Text("关闭") }
            }
        )
    }
}

private data class CookieSnapshot(
    val keys: List<String>,
    val hasRequired: Boolean
)

private fun parseCookieSnapshot(rawCookie: String): CookieSnapshot {
    val map = LinkedHashMap<String, String>()
    rawCookie.split(';').forEach { segment ->
        val part = segment.trim()
        val idx = part.indexOf('=')
        if (idx > 0) {
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                map[key] = value
            }
        }
    }
    val hasSessdata = map.keys.any { it.equals("SESSDATA", true) }
    val hasBiliJct = map.keys.any { it.equals("bili_jct", true) }
    return CookieSnapshot(
        keys = map.keys.toList(),
        hasRequired = hasSessdata && hasBiliJct
    )
}

private fun buildCookieWithRefreshToken(cookie: String?, refreshToken: String?): String {
    val base = cookie?.trim().orEmpty()
    if (base.isBlank()) return ""
    val refresh = refreshToken?.trim().orEmpty()
    if (refresh.isBlank()) return base
    if (base.contains("refresh_token=")) return base
    return if (base.endsWith(";")) "$base refresh_token=$refresh" else "$base; refresh_token=$refresh"
}

private fun inferCookieExpiryMs(cookie: String): Long? {
    if (cookie.isBlank()) return null

    val sessRaw = Regex("(^|;\\s*)SESSDATA=([^;]+)", RegexOption.IGNORE_CASE)
        .find(cookie)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()

    if (!sessRaw.isNullOrBlank()) {
        val decoded = runCatching { URLDecoder.decode(sessRaw, "UTF-8") }.getOrElse { sessRaw }
        val parts = decoded.split(',')
        if (parts.size >= 2) {
            val ts = parts[1].trim().toLongOrNull()
            val ms = ts?.let {
                when {
                    it < 10_000_000_000L -> it * 1000L
                    it > 10_000_000_000_000L -> it / 1000L
                    else -> it
                }
            }
            if (ms != null && ms > 0L) return ms
        }
    }

    val expires = Regex("(?i)Expires=([^;]+)")
        .find(cookie)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!expires.isNullOrBlank()) {
        return runCatching {
            val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            parser.parse(expires)?.time
        }.getOrNull()
    }
    return null
}

private fun formatEpochMs(epochMs: Long?): String {
    val value = epochMs ?: return "未知"
    if (value <= 0L) return "未知"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateText = runCatching { sdf.format(Date(value)) }.getOrElse { "未知" }
    val delta = value - System.currentTimeMillis()
    if (delta <= 0L) return "$dateText（已过期）"

    val days = delta / (24 * 60 * 60 * 1000L)
    val hours = (delta / (60 * 60 * 1000L)) % 24
    val tail = when {
        days >= 1 -> "（约 ${days} 天 ${hours} 小时后）"
        hours >= 1 -> "（约 ${hours} 小时后）"
        else -> "（不到 1 小时）"
    }
    return dateText + tail
}

private fun buildQrBitmap(content: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}

private fun parseCsvTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    raw.split(',').forEach { token ->
        val t = token.trim()
        if (t.isNotBlank() && seen.add(t)) {
            out += t
        }
    }
    return out
}

private fun parseTitleMappingRows(raw: String): List<TitleMappingRow> {
    return raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { segment ->
            val idx = segment.indexOf("->")
            if (idx < 0) return@mapNotNull null
            val left = segment.substring(0, idx).trim()
            val right = segment.substring(idx + 2).trim()
            if (left.isBlank() && right.isBlank()) null else TitleMappingRow(left, right)
        }
}

private fun serializeTitleMappingRows(rows: List<TitleMappingRow>): String {
    return rows.mapNotNull { row ->
        val left = row.left.trim()
        val right = row.right.trim()
        if (left.isBlank() || right.isBlank()) null else "$left->$right"
    }.joinToString(";")
}

private fun parseTitleOffsetRows(raw: String): List<TitleOffsetRow> {
    return raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { segment ->
            val parts = segment.split('@').map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            val offset = parts.last()
            val platforms = parts[parts.size - 2]
            val title = parts.dropLast(2).joinToString("@").trim()
            if (title.isBlank() && platforms.isBlank() && offset.isBlank()) null
            else TitleOffsetRow(title = title, platformsRaw = platforms, offset = offset)
        }
}

private fun serializeTitleOffsetRows(rows: List<TitleOffsetRow>): String {
    return rows.mapNotNull { row ->
        val title = row.title.trim()
        val platforms = normalizePlatforms(row.platformsRaw)
        val offset = row.offset.trim()
        if (title.isBlank() || platforms.isBlank() || offset.isBlank()) null
        else "$title@$platforms@$offset"
    }.joinToString(";")
}

private fun parsePlatformTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    raw.split(Regex("[&,]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val normalized = if (token == "*") "all" else token
            val key = normalized.lowercase(Locale.getDefault())
            if (seen.add(key)) out += normalized
        }
    return out
}

private fun normalizePlatforms(raw: String): String {
    val tokens = parsePlatformTokens(raw)
    if (tokens.isEmpty()) return ""
    return if (tokens.any { it.equals("all", true) }) "all" else tokens.joinToString("&")
}

private fun normalizePlatformOptions(options: List<String>): List<String> {
    val normalized = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    options.map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { option ->
            val normalizedOption = if (option == "*") "all" else option
            val key = normalizedOption.lowercase(Locale.getDefault())
            if (seen.add(key)) normalized += normalizedOption
        }

    val withAll = if (normalized.any { it.equals("all", true) }) normalized else listOf("all") + normalized
    val all = withAll.firstOrNull { it.equals("all", true) } ?: "all"
    val others = withAll.filterNot { it.equals("all", true) }
    return listOf(all) + others
}

private fun canonicalizePlatformSelection(selected: List<String>, options: List<String>): List<String> {
    if (selected.isEmpty()) return emptyList()
    if (selected.any { it.equals("all", true) || it == "*" }) return listOf("all")

    val optionOrder = options.filterNot { it.equals("all", true) }
    val selectedMap = linkedMapOf<String, String>()
    selected.forEach { token ->
        val normalized = token.trim()
        if (normalized.isBlank() || normalized.equals("all", true)) return@forEach
        val key = normalized.lowercase(Locale.getDefault())
        if (!selectedMap.containsKey(key)) selectedMap[key] = normalized
    }

    val ordered = mutableListOf<String>()
    optionOrder.forEach { option ->
        val key = option.lowercase(Locale.getDefault())
        if (selectedMap.containsKey(key)) {
            ordered += option
            selectedMap.remove(key)
        }
    }
    ordered += selectedMap.values
    return ordered
}

private fun togglePlatformSelection(current: List<String>, option: String, options: List<String>): List<String> {
    val normalizedOption = if (option == "*") "all" else option
    val normalizedCurrent = canonicalizePlatformSelection(current, options)

    if (normalizedOption.equals("all", true)) {
        return if (normalizedCurrent.any { it.equals("all", true) }) {
            emptyList()
        } else {
            listOf("all")
        }
    }

    val next = normalizedCurrent.filterNot { it.equals("all", true) }.toMutableList()
    val index = next.indexOfFirst { it.equals(normalizedOption, true) }
    if (index >= 0) {
        next.removeAt(index)
    } else {
        next += normalizedOption
    }
    return canonicalizePlatformSelection(next, options)
}

private fun formatPlatformSummary(platforms: List<String>): String {
    if (platforms.isEmpty()) return "未选择"
    if (platforms.any { it.equals("all", true) }) return "全部"
    return platforms.joinToString("、")
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val list = toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    return list
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> {
    if (index !in indices) return this
    val list = toMutableList()
    list[index] = value
    return list
}

private fun String?.ifNullOrBlank(fallback: String): String {
    val value = this?.trim().orEmpty()
    return if (value.isBlank()) fallback else value
}
