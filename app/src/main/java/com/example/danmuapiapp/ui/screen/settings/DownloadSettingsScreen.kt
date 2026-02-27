package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.DANMU_FILE_NAME_TEMPLATE_PRESETS
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import com.example.danmuapiapp.domain.model.renderFileNameTemplatePreview
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.SettingsValueItem
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadSettingsScreen(
    onBack: () -> Unit,
    viewModel: DownloadSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val throttlePreset = settings.throttle()
    val throttleConfig = settings.throttleConfig()
    var templateInput by rememberSaveable(settings.fileNameTemplate) {
        mutableStateOf(settings.fileNameTemplate)
    }
    var customBaseDelayInput by rememberSaveable(settings.customBaseDelayMs) {
        mutableStateOf(settings.customBaseDelayMs.toString())
    }
    var customJitterInput by rememberSaveable(settings.customJitterMaxMs) {
        mutableStateOf(settings.customJitterMaxMs.toString())
    }
    var customBatchSizeInput by rememberSaveable(settings.customBatchSize) {
        mutableStateOf(settings.customBatchSize.toString())
    }
    var customBatchRestSecInput by rememberSaveable(settings.customBatchRestMs) {
        mutableStateOf((settings.customBatchRestMs / 1000L).toString())
    }
    var customBackoffBaseSecInput by rememberSaveable(settings.customBackoffBaseMs) {
        mutableStateOf((settings.customBackoffBaseMs / 1000L).toString())
    }
    var customBackoffMaxSecInput by rememberSaveable(settings.customBackoffMaxMs) {
        mutableStateOf((settings.customBackoffMaxMs / 1000L).toString())
    }
    var showCustomEditor by rememberSaveable { mutableStateOf(false) }
    val previewText = renderFileNameTemplatePreview(
        template = templateInput,
        format = settings.format()
    )

    LaunchedEffect(throttlePreset) {
        if (throttlePreset != DownloadThrottlePreset.Custom) {
            showCustomEditor = false
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            viewModel.dismissMessage()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val display = RuntimePaths.resolveTreeUriToPath(uri) ?: uri.toString()
        viewModel.setSaveTree(uri.toString(), display)
    }

    LaunchedEffect(viewModel.operationMessage) {
        val msg = viewModel.operationMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "弹幕下载",
                subtitle = "下载路径、格式、命名规则、冲突策略与流控",
                onBack = onBack
            )

            SettingsGroup(title = "保存目录") {
                SettingsValueItem(
                    title = "当前目录",
                    value = settings.saveDirDisplayName.ifBlank { "未设置（必须先选择）" }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { picker.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = primaryActionButtonColors(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择下载目录")
                    }
                    OutlinedButton(
                        onClick = viewModel::clearSaveTree,
                        enabled = settings.saveTreeUri.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.ClearAll, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("清空目录设置")
                    }
                }
            }

            SettingsGroup(title = "默认下载") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("默认格式")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DanmuDownloadFormat.entries.forEach { format ->
                            FilterChip(
                                selected = settings.format() == format,
                                onClick = { viewModel.setDefaultFormat(format) },
                                colors = primarySelectionFilterChipColors(),
                                label = { Text(format.label) },
                                leadingIcon = if (settings.format() == format) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    Text("冲突策略")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DownloadConflictPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = settings.policy() == policy,
                                onClick = { viewModel.setConflictPolicy(policy) },
                                colors = primarySelectionFilterChipColors(),
                                label = { Text(policy.label) },
                                leadingIcon = if (settings.policy() == policy) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            SettingsGroup(title = "下载流控") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("流控预设")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DownloadThrottlePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = throttlePreset == preset,
                                onClick = { viewModel.setThrottlePreset(preset) },
                                colors = primarySelectionFilterChipColors(),
                                label = { Text(preset.label) },
                                leadingIcon = if (throttlePreset == preset) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "当前策略：${throttleConfig.label}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "请求间隔 ${throttleConfig.baseDelayMs}ms + 随机 ${throttleConfig.jitterMaxMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "每 ${throttleConfig.batchSize} 集休息 ${throttleConfig.batchRestMs / 1000}s，失败退避 ${throttleConfig.backoffBaseMs / 1000}s~${throttleConfig.backoffMaxMs / 1000}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (throttlePreset == DownloadThrottlePreset.Custom) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "自定义参数已启用",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    "参数编辑已折叠，点击下方按钮进入编辑弹窗。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { showCustomEditor = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("编辑自定义参数")
                                }
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "命名规则") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "预设模板",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DANMU_FILE_NAME_TEMPLATE_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = templateInput.trim() == preset.template,
                                onClick = { templateInput = preset.template },
                                colors = primarySelectionFilterChipColors(),
                                label = { Text(preset.name) },
                                leadingIcon = if (templateInput.trim() == preset.template) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    OutlinedTextField(
                        value = templateInput,
                        onValueChange = { templateInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        label = { Text("文件命名模板") },
                        supportingText = {
                            Text("占位符：{animeTitle} {episodeNo/2/3} {episodeTitle} {episodeId} {source} {ext} {date} {datetime}")
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setFileNameTemplate(templateInput) },
                            modifier = Modifier.weight(1f),
                            colors = primaryActionButtonColors(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("保存模板")
                        }
                        OutlinedButton(
                            onClick = {
                                templateInput = DanmuDownloadSettings().fileNameTemplate
                                viewModel.setFileNameTemplate(templateInput)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("恢复默认")
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "当前模板示例",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${settings.saveDirDisplayName.ifBlank { "已选目录" }}/凡人修仙传/$previewText",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (throttlePreset == DownloadThrottlePreset.Custom && showCustomEditor) {
        CustomThrottleEditorDialog(
            customBaseDelayInput = customBaseDelayInput,
            onCustomBaseDelayInputChange = { customBaseDelayInput = it },
            customJitterInput = customJitterInput,
            onCustomJitterInputChange = { customJitterInput = it },
            customBatchSizeInput = customBatchSizeInput,
            onCustomBatchSizeInputChange = { customBatchSizeInput = it },
            customBatchRestSecInput = customBatchRestSecInput,
            onCustomBatchRestSecInputChange = { customBatchRestSecInput = it },
            customBackoffBaseSecInput = customBackoffBaseSecInput,
            onCustomBackoffBaseSecInputChange = { customBackoffBaseSecInput = it },
            customBackoffMaxSecInput = customBackoffMaxSecInput,
            onCustomBackoffMaxSecInputChange = { customBackoffMaxSecInput = it },
            onDismiss = { showCustomEditor = false },
            onResetDefault = {
                customBaseDelayInput = DownloadThrottlePreset.Custom.baseDelayMs.toString()
                customJitterInput = DownloadThrottlePreset.Custom.jitterMaxMs.toString()
                customBatchSizeInput = DownloadThrottlePreset.Custom.batchSize.toString()
                customBatchRestSecInput = (DownloadThrottlePreset.Custom.batchRestMs / 1000L).toString()
                customBackoffBaseSecInput = (DownloadThrottlePreset.Custom.backoffBaseMs / 1000L).toString()
                customBackoffMaxSecInput = (DownloadThrottlePreset.Custom.backoffMaxMs / 1000L).toString()
            },
            onSave = {
                val baseDelay = customBaseDelayInput.toLongOrNull()
                if (baseDelay == null || baseDelay !in 100L..120_000L) {
                    showCustomEditor = false
                    scope.launch { snackbarHostState.showSnackbar("请求间隔需为 100~120000 ms") }
                    return@CustomThrottleEditorDialog
                }
                val jitter = customJitterInput.toLongOrNull()
                if (jitter == null || jitter !in 0L..20_000L) {
                    showCustomEditor = false
                    scope.launch { snackbarHostState.showSnackbar("随机抖动需为 0~20000 ms") }
                    return@CustomThrottleEditorDialog
                }
                val batchSize = customBatchSizeInput.toIntOrNull()
                if (batchSize == null || batchSize !in 1..500) {
                    showCustomEditor = false
                    scope.launch { snackbarHostState.showSnackbar("每批集数需为 1~500") }
                    return@CustomThrottleEditorDialog
                }
                val batchRestSec = customBatchRestSecInput.toLongOrNull()
                if (batchRestSec == null || batchRestSec !in 0L..900L) {
                    showCustomEditor = false
                    scope.launch { snackbarHostState.showSnackbar("休息秒数需为 0~900 秒") }
                    return@CustomThrottleEditorDialog
                }
                val backoffBaseSec = customBackoffBaseSecInput.toLongOrNull()
                if (backoffBaseSec == null || backoffBaseSec !in 1L..900L) {
                    showCustomEditor = false
                    scope.launch { snackbarHostState.showSnackbar("退避起始需为 1~900 秒") }
                    return@CustomThrottleEditorDialog
                }
                val backoffMaxSec = customBackoffMaxSecInput.toLongOrNull()
                if (backoffMaxSec == null || backoffMaxSec !in backoffBaseSec..1800L) {
                    showCustomEditor = false
                    scope.launch { snackbarHostState.showSnackbar("退避上限需大于等于起始，且不超过 1800 秒") }
                    return@CustomThrottleEditorDialog
                }
                viewModel.setCustomThrottleConfig(
                    baseDelayMs = baseDelay,
                    jitterMaxMs = jitter,
                    batchSize = batchSize,
                    batchRestMs = batchRestSec * 1000L,
                    backoffBaseMs = backoffBaseSec * 1000L,
                    backoffMaxMs = backoffMaxSec * 1000L
                )
                showCustomEditor = false
            }
        )
    }
}

@Composable
private fun ThrottleNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            onValueChange(next.filter { it.isDigit() })
        },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun CustomThrottleEditorDialog(
    customBaseDelayInput: String,
    onCustomBaseDelayInputChange: (String) -> Unit,
    customJitterInput: String,
    onCustomJitterInputChange: (String) -> Unit,
    customBatchSizeInput: String,
    onCustomBatchSizeInputChange: (String) -> Unit,
    customBatchRestSecInput: String,
    onCustomBatchRestSecInputChange: (String) -> Unit,
    customBackoffBaseSecInput: String,
    onCustomBackoffBaseSecInputChange: (String) -> Unit,
    customBackoffMaxSecInput: String,
    onCustomBackoffMaxSecInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onResetDefault: () -> Unit,
    onSave: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑自定义流控") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "建议先用小批量任务验证参数稳定性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThrottleNumberField(
                        label = "请求间隔(ms)",
                        value = customBaseDelayInput,
                        onValueChange = onCustomBaseDelayInputChange,
                        supportingText = "100~120000",
                        modifier = Modifier.weight(1f)
                    )
                    ThrottleNumberField(
                        label = "随机抖动(ms)",
                        value = customJitterInput,
                        onValueChange = onCustomJitterInputChange,
                        supportingText = "0~20000",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThrottleNumberField(
                        label = "每批集数",
                        value = customBatchSizeInput,
                        onValueChange = onCustomBatchSizeInputChange,
                        supportingText = "1~500",
                        modifier = Modifier.weight(1f)
                    )
                    ThrottleNumberField(
                        label = "休息秒数",
                        value = customBatchRestSecInput,
                        onValueChange = onCustomBatchRestSecInputChange,
                        supportingText = "0~900",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThrottleNumberField(
                        label = "退避起始(秒)",
                        value = customBackoffBaseSecInput,
                        onValueChange = onCustomBackoffBaseSecInputChange,
                        supportingText = "1~900",
                        modifier = Modifier.weight(1f)
                    )
                    ThrottleNumberField(
                        label = "退避上限(秒)",
                        value = customBackoffMaxSecInput,
                        onValueChange = onCustomBackoffMaxSecInputChange,
                        supportingText = ">=起始，且<=1800",
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = onResetDefault,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复默认值")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) { Text("取消") }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = primaryActionButtonColors(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存")
            }
        }
    )
}

@Composable
private fun primaryActionButtonColors() = appPrimaryButtonColors()

@Composable
private fun primarySelectionFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
)
