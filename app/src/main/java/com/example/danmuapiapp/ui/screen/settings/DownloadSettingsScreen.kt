package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.AppSnackbarHost
import com.example.danmuapiapp.ui.screen.tools.*

@Composable fun DownloadSettingsScreen(onBack: () -> Unit, viewModel: DownloadSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var path by rememberSaveable(settings.saveDirPath) { mutableStateOf(settings.saveDirPath.ifBlank { "/storage/emulated/0/Download/弹幕下载" }) }
    var template by rememberSaveable(settings.fileNameTemplate) { mutableStateOf(settings.fileNameTemplate) }
    var allFiles by remember { mutableStateOf(RuntimePaths.isAllFilesAccessGranted(context)) }
    var showDirectory by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
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
        }
    }
    LaunchedEffect(viewModel.operationMessage) { viewModel.operationMessage?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() } }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { AppSnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ToolHeader("下载设置", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolTitle("保存位置", Modifier.weight(1f))
                        TextButton({ if (allFiles) showDirectory = true else { pendingBrowse = true; grant() } }) { Text("选择目录", fontSize = 12.sp) }
                    }
                    ToolHint("当前：${settings.saveDirPath.ifBlank { settings.saveDirDisplayName.ifBlank { "未设置" } }}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolHint(if (allFiles) "已获得所有文件访问权限" else "未获得所有文件访问权限", Modifier.weight(1f))
                        TextButton({ if (allFiles) allFiles = RuntimePaths.isAllFilesAccessGranted(context) else grant() }) { Text(if (allFiles) "重新检查" else "去授权", fontSize = 12.sp) }
                    }
                    ToolInput(path, { path = it }, "保存目录真实路径")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("应用路径", { if (allFiles) viewModel.setSaveDirPath(path) else grant() }, outlined = true)
                        ToolButton("系统目录授权", { tree.launch(null) }, outlined = true)
                        ToolButton("同步文件", viewModel::syncExistingFiles, outlined = true, busy = viewModel.isSyncingDirectory)
                    }
                    ToolHint("选择真实路径需要所有文件访问权限；也可通过系统目录授权使用 SAF。")
                }
                ToolCard {
                    ToolTitle("导出格式")
                    Box {
                        ToolButton("${settings.format().label}（.${settings.format().extension}）", { showFormat = true }, Modifier.fillMaxWidth(), outlined = true)
                        DropdownMenu(showFormat, { showFormat = false }) {
                            DanmuDownloadFormat.entries.forEach { format -> DropdownMenuItem(text = { Text("${format.label}（.${format.extension}）", fontSize = 12.sp) },
                                onClick = { viewModel.setDefaultFormat(format); showFormat = false }) }
                        }
                    }
                    ToolTitle("文件名模板")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DANMU_FILE_NAME_TEMPLATE_PRESETS.forEach { preset -> ToolChip(preset.name, template == preset.template, {
                            template = preset.template; viewModel.setFileNameTemplate(template)
                        }) }
                    }
                    ToolInput(template, { template = it }, DanmuDownloadSettings().fileNameTemplate,
                        onSubmit = { viewModel.setFileNameTemplate(template) })
                    ToolHint("预览：${renderFileNameTemplatePreview(template, settings.format())}", maxLines = 2)
                    ToolHint("变量：{animeTitle} {episodeTitle} {episodeNo} {episodeNo2} {episodeId} {source} {format} {ext} {date}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("保存模板", { viewModel.setFileNameTemplate(template) }, outlined = true)
                        TextButton({ template = DanmuDownloadSettings().fileNameTemplate; viewModel.setFileNameTemplate(template) }) { Text("恢复默认", fontSize = 12.sp) }
                    }
                    ToolTitle("同名文件")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DownloadConflictPolicy.entries.forEach { policy -> ToolChip(policy.label, settings.policy() == policy, { viewModel.setConflictPolicy(policy) }) }
                    }
                }
                ToolCard {
                    ToolTitle("批量限速")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DownloadThrottlePreset.entries.forEach { preset -> ToolChip(preset.label, settings.throttle() == preset, { viewModel.setThrottlePreset(preset) }) }
                    }
                    val config = settings.throttleConfig()
                    ToolHint("${settings.throttle().label} · 间隔 ${config.baseDelayMs}ms · 每 ${config.batchSize} 集休息 ${config.batchRestMs / 1000}s")
                    if (settings.throttle() == DownloadThrottlePreset.Custom) ToolButton("编辑自定义参数", { showCustom = true }, outlined = true)
                    ToolTitle("匹配方式（刷新 / 找缺失集）")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolChip("自动匹配（match）", settings.matchMode == "match", { viewModel.setImportOptions(settings.autoImportToLocal, settings.deleteSourceAfterImport, "match") })
                        ToolChip("纯剧名搜索", settings.matchMode == "search", { viewModel.setImportOptions(settings.autoImportToLocal, settings.deleteSourceAfterImport, "search") })
                    }
                    ToolHint("按所选方式寻找原来源的剧集，失败后自动尝试另一种方式，最后使用记录里的动漫 ID 兜底。")
                }
                ToolCard {
                    ToolTitle("下载 → 本地弹幕联动")
                    ImportSettingRow("下载后自动导入本地弹幕", "每集下载完成后导入；缺少年份等信息会留下原因，稍后手动订正。",
                        settings.autoImportToLocal) { viewModel.setImportOptions(it, settings.deleteSourceAfterImport, settings.matchMode) }
                    ImportSettingRow("导入成功后删除源文件", "本地库与下载记录保留，只在导入成功后删除下载目录里的源文件。",
                        settings.deleteSourceAfterImport) { viewModel.setImportOptions(settings.autoImportToLocal, it, settings.matchMode) }
                    if (settings.autoImportToLocal) ToolHint("自动导入需要核心允许本地弹幕写入，或已配置管理员令牌。")
                }
            }
        }
    }
    if (showDirectory) ToolDirectoryPicker(settings.saveDirPath.ifBlank { "/storage/emulated/0/Download" }, { showDirectory = false }) {
        path = it; viewModel.setSaveDirPath(it); showDirectory = false
    }
    if (showCustom) CustomDownloadThrottleDialog(settings, { showCustom = false }) { base, jitter, batch, rest, backoff, max ->
        viewModel.setCustomThrottleConfig(base, jitter, batch, rest, backoff, max); showCustom = false
    }
}
@Composable private fun ImportSettingRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 13.sp); ToolHint(hint)
        }
        Switch(checked, onChange)
    }
}
@Composable private fun CustomDownloadThrottleDialog(settings: DanmuDownloadSettings, onDismiss: () -> Unit,
    onSave: (Long, Long, Int, Long, Long, Long) -> Unit) {
    val initial = settings.throttleConfig()
    var values by remember { mutableStateOf(listOf(initial.baseDelayMs.toString(), initial.jitterMaxMs.toString(),
        initial.batchSize.toString(), (initial.batchRestMs / 1000).toString(), (initial.backoffBaseMs / 1000).toString(), (initial.backoffMaxMs / 1000).toString())) }
    var error by remember { mutableStateOf<String?>(null) }
    val labels = listOf("请求间隔（ms）", "随机抖动（ms）", "每批集数", "休息秒数", "退避起始（秒）", "退避上限（秒）")
    AppDialog(onDismissRequest = onDismiss, title = { ToolTitle("编辑自定义限速", size = 16) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                ToolHint(label); ToolInput(values[i], { value -> values = values.toMutableList().apply { this[i] = value } }, label, number = true)
            }
            error?.let { ToolError(it) }
            TextButton({ val d = DownloadThrottlePreset.Custom; values = listOf(d.baseDelayMs.toString(), d.jitterMaxMs.toString(), d.batchSize.toString(),
                (d.batchRestMs / 1000).toString(), (d.backoffBaseMs / 1000).toString(), (d.backoffMaxMs / 1000).toString()) }) { Text("恢复默认") }
        } }, dismissButton = { TextButton(onDismiss) { Text("取消") } }, confirmButton = {
            ToolButton("保存", {
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
            })
        })
}
