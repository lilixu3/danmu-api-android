package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
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

@Composable
internal fun EnvVarEditDialog(
    def: EnvVarDef,
    currentValue: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onGenerateBiliQr: suspend () -> Result<BilibiliQrGenerateResult>,
    onPollBiliQr: suspend (String) -> Result<BilibiliQrPollResult>,
    onVerifyBiliCookie: suspend (String) -> Result<BilibiliCookieVerifyResult>,
    onVerifyAiConnectivity: suspend (String) -> Result<AiConnectivityVerifyResult>,
) {
    var value by remember(def.key, currentValue) { mutableStateOf(currentValue) }
    var showPassword by remember(def.key) { mutableStateOf(false) }
    var showDeleteConfirm by remember(def.key) { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AppBottomSheetDialog(
            onDismissRequest = { showDeleteConfirm = false },
            style = AppBottomSheetStyle.Confirm,
            tone = AppBottomSheetTone.Danger,
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

    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Form,
        tone = AppBottomSheetTone.Brand,
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

                    KEY_AI_API_KEY -> {
                        AiApiKeyEditor(
                            value = value,
                            onValueChange = { value = it },
                            onVerifyAiConnectivity = onVerifyAiConnectivity
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
internal fun OrderedTokenEditor(
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
internal fun MergeSourcePairsEditor(
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

internal data class TitleMappingRow(
    val left: String = "",
    val right: String = "",
)

@Composable
internal fun TitleMappingTableEditor(
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

internal data class TitleOffsetRow(
    val title: String = "",
    val platformsRaw: String = "",
    val offset: String = "",
)

internal data class PlatformPickerState(
    val rowIndex: Int,
    val selected: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TitlePlatformOffsetTableEditor(
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
internal fun PlatformMultiSelectDialog(
    options: List<String>,
    initialSelected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember(options, initialSelected) {
        mutableStateOf(canonicalizePlatformSelection(initialSelected, options))
    }

    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Selection,
        tone = AppBottomSheetTone.Info,
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

