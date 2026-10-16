package com.example.danmuapiapp.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton
import com.example.danmuapiapp.ui.screen.tools.ToolDirectoryPicker

@Composable
fun DownloadSettingsScreen(onBack: () -> Unit, viewModel: DownloadSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var path by rememberSaveable(settings.saveDirPath) { mutableStateOf(settings.saveDirPath.ifBlank { "/storage/emulated/0/Download/弹幕下载" }) }
    var template by rememberSaveable(settings.fileNameTemplate) { mutableStateOf(settings.fileNameTemplate) }
    var allFiles by remember { mutableStateOf(RuntimePaths.isAllFilesAccessGranted(context)) }
    var showStorage by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showTemplate by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var showConflict by remember { mutableStateOf(false) }
    var showThrottle by remember { mutableStateOf(false) }
    var showMatchMode by remember { mutableStateOf(false) }
    var pendingBrowse by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allFiles = RuntimePaths.isAllFilesAccessGranted(context)
        if (allFiles && pendingBrowse) showDirectory = true
        pendingBrowse = false
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allFiles = RuntimePaths.isAllFilesAccessGranted(context)
        if (allFiles && pendingBrowse) showDirectory = true
        pendingBrowse = false
    }
    fun grant() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            runCatching { permission.launch(intent) }.getOrElse { permission.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else legacyPermission.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            viewModel.setSaveTree(uri.toString(), RuntimePaths.resolveTreeUriToPath(uri) ?: uri.toString())
            showStorage = false
        }
    }
    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(containerColor = Color.Transparent, snackbarHost = { AppSnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsPageHeader("弹幕下载", "保存位置、文件输出与下载后处理", onBack)

            SettingsGroup(title = "保存位置") {
                SettingsItem(title = "下载目录", icon = Icons.Rounded.Folder,
                    subtitle = settings.saveDirPath.ifBlank { settings.saveDirDisplayName.ifBlank { "尚未选择保存目录" } },
                    onClick = { showStorage = true })
                SettingsDivider()
                SettingsItem(title = "文件访问权限", icon = Icons.Rounded.FolderOpen,
                    subtitle = if (allFiles) "已授权，可通过真实路径保存文件" else "可授权访问文件，或单独授权一个系统目录",
                    onClick = { if (allFiles) allFiles = RuntimePaths.isAllFilesAccessGranted(context) else grant() },
                    trailing = { Text(if (allFiles) "已授权" else "去授权", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) })
                SettingsDivider()
                SettingsItem(title = "同步已有文件", icon = Icons.Rounded.Sync,
                    subtitle = if (viewModel.isSyncingDirectory) "正在扫描目录并补充下载记录" else "扫描下载目录，将已有弹幕文件加入下载记录",
                    onClick = if (viewModel.isSyncingDirectory) null else viewModel::syncExistingFiles,
                    trailing = {
                        if (viewModel.isSyncingDirectory) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, "同步文件", tint = MaterialTheme.colorScheme.primary)
                    })
            }

            SettingsGroup(title = "文件输出") {
                SettingsValueItem(title = "导出格式", value = settings.format().label, icon = Icons.Rounded.Description,
                    onClick = { showFormat = true })
                SettingsDivider()
                SettingsItem(title = "文件命名", subtitle = renderFileNameTemplatePreview(settings.fileNameTemplate, settings.format()),
                    icon = Icons.Rounded.Edit, onClick = { template = settings.fileNameTemplate; showTemplate = true })
                SettingsDivider()
                SettingsValueItem(title = "同名文件", value = settings.policy().label, icon = Icons.Rounded.FileCopy,
                    onClick = { showConflict = true })
            }

            SettingsGroup(title = "批量下载") {
                val config = settings.throttleConfig()
                SettingsItem(title = "下载节奏", icon = Icons.Rounded.Speed,
                    subtitle = "${settings.throttle().label} · 间隔 ${config.baseDelayMs} ms · 每 ${config.batchSize} 集休息 ${config.batchRestMs / 1000} 秒",
                    onClick = { showThrottle = true })
                if (settings.throttle() == DownloadThrottlePreset.Custom) {
                    SettingsDivider()
                    SettingsItem(title = "自定义限速参数", subtitle = "请求间隔、随机抖动、批次休息与失败退避",
                        icon = Icons.Rounded.Tune, onClick = { showCustom = true })
                }
                SettingsDivider()
                SettingsItem(title = "刷新与补集匹配", icon = Icons.Rounded.Search,
                    subtitle = if (settings.matchMode == "match") "自动匹配来源和剧集" else "使用纯剧名搜索",
                    onClick = { showMatchMode = true })
            }

            SettingsGroup(title = "本地弹幕联动") {
                SettingsSwitchItem(title = "下载后自动导入", subtitle = "每集下载完成后导入本地弹幕库", icon = Icons.Rounded.LibraryAdd,
                    checked = settings.autoImportToLocal,
                    onCheckedChange = { viewModel.setImportOptions(it, settings.deleteSourceAfterImport, settings.matchMode) })
                SettingsDivider()
                SettingsSwitchItem(title = "导入成功后删除源文件", subtitle = "保留本地库和下载记录，只删除已成功导入的下载文件",
                    icon = Icons.Rounded.DeleteOutline, checked = settings.deleteSourceAfterImport,
                    onCheckedChange = { viewModel.setImportOptions(settings.autoImportToLocal, it, settings.matchMode) })
            }
            if (settings.autoImportToLocal) SettingsHintCard("自动导入需要核心允许本地弹幕写入，或已配置管理员令牌。缺少年份等信息时，可稍后在本地弹幕中订正。")
            FloatingBottomBarContentSpacer()
        }
    }

    if (showStorage) AppDialog(onDismissRequest = { showStorage = false }, title = { Text("下载目录") },
        supportingText = { Text("选择目录或填写真实路径，保存后自动扫描已有弹幕文件") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsItem(title = "浏览文件目录", subtitle = "需要所有文件访问权限", icon = Icons.Rounded.FolderOpen,
                    onClick = {
                        showStorage = false
                        if (allFiles) showDirectory = true else { pendingBrowse = true; grant() }
                    })
                SettingsItem(title = "系统目录授权", subtitle = "只授权所选目录，无需所有文件访问权限", icon = Icons.Rounded.Folder,
                    onClick = { tree.launch(null) })
                OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("真实路径") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = {
            AppGlassPrimaryButton(enabled = path.isNotBlank(), onClick = {
                if (allFiles) { viewModel.setSaveDirPath(path); showStorage = false } else grant()
            }) { Text(if (allFiles) "保存路径" else "授权后使用路径") }
        }, dismissButton = { AppGlassButton(onClick = { showStorage = false }) { Text("取消") } })

    if (showDirectory) ToolDirectoryPicker(settings.saveDirPath.ifBlank { "/storage/emulated/0/Download" }, { showDirectory = false }) {
        path = it; viewModel.setSaveDirPath(it); showDirectory = false
    }
    if (showTemplate) AppDialog(onDismissRequest = { showTemplate = false }, title = { Text("文件命名") },
        supportingText = { Text("先选择常用模板，再按需要调整") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DANMU_FILE_NAME_TEMPLATE_PRESETS.forEach { preset ->
                        AppGlassFilterChip(selected = template == preset.template, onClick = { template = preset.template }, label = { Text(preset.name) })
                    }
                }
                OutlinedTextField(value = template, onValueChange = { template = it }, label = { Text("文件名模板") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                SettingsHintCard("预览：${renderFileNameTemplatePreview(template, settings.format())}")
                Text("可用变量：{animeTitle} {episodeTitle} {episodeNo} {episodeNo2} {episodeId} {source} {format} {ext} {date}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { template = DanmuDownloadSettings().fileNameTemplate }) { Text("恢复默认模板") }
            }
        }, confirmButton = {
            AppGlassPrimaryButton(onClick = { viewModel.setFileNameTemplate(template); showTemplate = false }) { Text("保存模板") }
        }, dismissButton = { AppGlassButton(onClick = { showTemplate = false }) { Text("取消") } })

    if (showFormat) DownloadChoiceDialog("导出格式", "选择默认保存的弹幕文件格式",
        DanmuDownloadFormat.entries.map { it.value to "${it.label}（.${it.extension}）" }, settings.defaultFormat, { showFormat = false }) {
        viewModel.setDefaultFormat(DanmuDownloadFormat.fromValue(it)); showFormat = false
    }
    if (showConflict) DownloadChoiceDialog("同名文件", "遇到同名弹幕文件时如何处理",
        DownloadConflictPolicy.entries.map { it.key to it.label }, settings.conflictPolicy, { showConflict = false }) {
        viewModel.setConflictPolicy(DownloadConflictPolicy.fromKey(it)); showConflict = false
    }
    if (showThrottle) DownloadChoiceDialog("下载节奏", "选择批量下载时的请求速度和休息策略",
        DownloadThrottlePreset.entries.map { it.key to it.label }, settings.throttlePreset, { showThrottle = false }) {
        val preset = DownloadThrottlePreset.fromKey(it)
        showThrottle = false
        if (preset == DownloadThrottlePreset.Custom) showCustom = true else viewModel.setThrottlePreset(preset)
    }
    if (showMatchMode) DownloadChoiceDialog("刷新与补集匹配", "优先使用所选方式，失败后尝试另一种方式，最后使用记录中的动漫 ID",
        listOf("match" to "自动匹配（match）", "search" to "纯剧名搜索"), settings.matchMode, { showMatchMode = false }) {
        viewModel.setImportOptions(settings.autoImportToLocal, settings.deleteSourceAfterImport, it); showMatchMode = false
    }
    if (showCustom) CustomDownloadThrottleDialog(settings, { showCustom = false }) { base, jitter, batch, rest, backoff, max ->
        viewModel.setCustomThrottleConfig(base, jitter, batch, rest, backoff, max); showCustom = false
    }
}

@Composable
private fun DownloadChoiceDialog(title: String, subtitle: String, options: List<Pair<String, String>>, selected: String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AppDialog(onDismissRequest = onDismiss, style = AppDialogStyle.Selection, title = { Text(title) },
        supportingText = { Text(subtitle) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (key, label) ->
                    AppDialogOption(selected = key == selected, onClick = { onSelect(key) }) {
                        RadioButton(selected = key == selected, onClick = null)
                        Text(label)
                    }
                }
            }
        }, dismissButton = { AppGlassButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun CustomDownloadThrottleDialog(settings: DanmuDownloadSettings, onDismiss: () -> Unit,
    onSave: (Long, Long, Int, Long, Long, Long) -> Unit) {
    val initial = settings.throttleConfig()
    var values by remember { mutableStateOf(listOf(initial.baseDelayMs.toString(), initial.jitterMaxMs.toString(),
        initial.batchSize.toString(), (initial.batchRestMs / 1000).toString(), (initial.backoffBaseMs / 1000).toString(), (initial.backoffMaxMs / 1000).toString())) }
    var error by remember { mutableStateOf<String?>(null) }
    val labels = listOf("请求间隔（ms）", "随机抖动（ms）", "每批集数", "休息秒数", "退避起始（秒）", "退避上限（秒）")
    AppDialog(onDismissRequest = onDismiss, title = { Text("自定义下载节奏") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { i, label ->
                OutlinedTextField(value = values[i], onValueChange = { value -> values = values.toMutableList().apply { this[i] = value }; error = null },
                    label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {
                val d = DownloadThrottlePreset.Custom
                values = listOf(d.baseDelayMs.toString(), d.jitterMaxMs.toString(), d.batchSize.toString(),
                    (d.batchRestMs / 1000).toString(), (d.backoffBaseMs / 1000).toString(), (d.backoffMaxMs / 1000).toString())
                error = null
            }) { Text("恢复默认") }
        }
    }, dismissButton = { AppGlassButton(onClick = onDismiss) { Text("取消") } }, confirmButton = {
        AppGlassPrimaryButton(onClick = {
            val v = values.map { it.toLongOrNull() }
            error = when {
                v.any { it == null } -> "请填写完整的整数参数"
                v[0]!! !in 100..120000 -> "请求间隔需为 100–120000 ms"
                v[1]!! !in 0..20000 -> "随机抖动需为 0–20000 ms"
                v[2]!! !in 1..500 -> "每批集数需为 1–500"
                v[3]!! !in 0..900 -> "休息需为 0–900 秒"
                v[4]!! !in 1..900 -> "退避起始需为 1–900 秒"
                v[5]!! !in v[4]!!..1800 -> "退避上限需大于起始且不超过 1800 秒"
                else -> null
            }
            if (error == null) onSave(v[0]!!, v[1]!!, v[2]!!.toInt(), v[3]!! * 1000, v[4]!! * 1000, v[5]!! * 1000)
        }) { Text("保存") }
    })
}
