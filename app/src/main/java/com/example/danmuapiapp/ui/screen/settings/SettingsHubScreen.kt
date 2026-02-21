package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.BuildConfig
import androidx.compose.ui.graphics.Color
import java.util.Locale

@Composable
fun SettingsHubScreen(
    onOpenRuntimeAndDir: () -> Unit,
    onOpenThemeDisplay: () -> Unit,
    onOpenWorkDir: () -> Unit,
    onOpenServiceConfig: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenGithubToken: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }
    val runModeLabel = state.runMode.label
    val hasInAppDownload = viewModel.appUpdateDownloadUrls.isNotEmpty() &&
        !viewModel.appUpdateLatestVersion.isNullOrBlank()
    val workDirPath = viewModel.workDirInfo.currentBaseDir.absolutePath
    val updateSubtitle = when {
        viewModel.isCheckingAppUpdate -> "正在检查新版本..."
        viewModel.isDownloadingAppUpdate -> "正在下载：${viewModel.appUpdateDownloadDetail}"
        viewModel.appUpdateLatestVersion == null -> "当前 v${viewModel.appUpdateCurrentVersion} · 点击检查"
        viewModel.appUpdateHasUpdate -> "发现新版本 v${viewModel.appUpdateLatestVersion}"
        else -> "已是最新版本 v${viewModel.appUpdateCurrentVersion}"
    }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshWorkDirInfo()
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
                title = "设置",
                subtitle = "服务、网络、备份与偏好管理"
            )

            SettingsGroup(title = "核心运行") {
                SettingsItem(
                    title = "运行模式",
                    subtitle = "$runModeLabel · 普通/Root 自启方案",
                    icon = Icons.Rounded.Tune,
                    onClick = onOpenRuntimeAndDir
                )
                SettingsDivider()
                SettingsItem(
                    title = "工作目录",
                    subtitle = workDirPath,
                    icon = Icons.Rounded.Folder,
                    onClick = onOpenWorkDir
                )
                SettingsDivider()
                SettingsItem(
                    title = "服务配置",
                    subtitle = "端口 ${state.port} · Token 与重启策略",
                    icon = Icons.Rounded.Lan,
                    onClick = onOpenServiceConfig
                )
            }

            SettingsGroup(title = "网络与数据") {
                SettingsItem(
                    title = "GitHub 代理",
                    subtitle = "当前线路：${viewModel.currentProxyLabel()}",
                    icon = Icons.Rounded.Public,
                    onClick = onOpenNetwork
                )
                SettingsDivider()
                SettingsItem(
                    title = "GitHub Token",
                    subtitle = viewModel.githubTokenSummary(),
                    icon = Icons.Rounded.VpnKey,
                    onClick = onOpenGithubToken
                )
                SettingsDivider()
                SettingsItem(
                    title = "备份与恢复",
                    subtitle = ".env 导入导出 · WebDAV 同步",
                    icon = Icons.Rounded.CloudSync,
                    onClick = onOpenBackupRestore
                )
            }

            SettingsGroup(title = "偏好与信息") {
                SettingsItem(
                    title = "主题与显示",
                    subtitle = "界面主题与应用 DPI 缩放",
                    icon = Icons.Rounded.Palette,
                    onClick = onOpenThemeDisplay
                )
                SettingsDivider()
                SettingsValueItem(
                    title = "版本",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    icon = Icons.Rounded.Info
                )
                SettingsDivider()
                SettingsItem(
                    title = "检查应用更新",
                    subtitle = updateSubtitle,
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = viewModel::checkAppUpdate,
                    trailing = {
                        if (viewModel.isCheckingAppUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                    }
                )
            }
        }
    }

    if (viewModel.showAppUpdateAvailableDialog && viewModel.appUpdateLatestVersion != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAppUpdateAvailableDialog,
            title = { Text("发现新版本 v${viewModel.appUpdateLatestVersion}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "当前版本：v${viewModel.appUpdateCurrentVersion}\n最新版本：v${viewModel.appUpdateLatestVersion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (hasInAppDownload) {
                            "可选择应用内下载（含进度显示）或跳转浏览器下载。"
                        } else {
                            "未找到可安装 APK，建议跳转浏览器下载。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val preview = viewModel.appUpdateReleaseNotes.trim().take(320)
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::openAppUpdateMethodDialog) {
                    Text("立即更新")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAppUpdateAvailableDialog) {
                    Text("稍后")
                }
            }
        )
    }

    if (viewModel.showAppUpdateMethodDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAppUpdateMethodDialog,
            title = { Text("选择更新方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = viewModel::startInAppUpdateDownload,
                        enabled = hasInAppDownload && !viewModel.isDownloadingAppUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("应用内下载")
                    }
                    OutlinedButton(
                        onClick = {
                            val alive = activity
                            if (alive == null) {
                                viewModel.postMessage("当前页面无法打开浏览器")
                            } else {
                                viewModel.openBrowserDownload(alive)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("浏览器下载")
                    }
                    if (!hasInAppDownload) {
                        Text(
                            text = "当前版本未找到可安装 APK，应用内下载暂不可用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "首次安装新版本可能需要“安装未知应用”权限，授权后返回 App 会自动继续安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAppUpdateMethodDialog) {
                    Text("取消")
                }
            }
        )
    }

    if (viewModel.isDownloadingAppUpdate) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val progress = viewModel.appUpdateDownloadPercent
                    if (progress in 0..100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "$progress% · ${viewModel.appUpdateDownloadDetail}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = viewModel.appUpdateDownloadDetail,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "下载完成后会弹出安装提示。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (viewModel.showInstallAppUpdateDialog && viewModel.downloadedAppUpdate != null) {
        val apk = viewModel.downloadedAppUpdate!!
        AlertDialog(
            onDismissRequest = viewModel::dismissInstallAppUpdateDialog,
            title = { Text("下载完成") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("文件：${apk.displayName}")
                    Text("位置：${apk.displayPath}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "大小：${formatBytes(apk.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        val alive = activity
                        if (alive == null) {
                            viewModel.postMessage("当前页面无法打开下载列表")
                        } else {
                            viewModel.openDownloadsApp(alive)
                        }
                    }) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("打开系统下载")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val alive = activity
                    if (alive == null) {
                        viewModel.postMessage("当前页面无法拉起安装器")
                    } else {
                        viewModel.installDownloadedAppUpdate(alive)
                    }
                }) { Text("立即安装") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissInstallAppUpdateDialog) { Text("稍后") }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun formatBytes(v: Long): String {
    if (v <= 0) return "未知"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        v >= gb -> String.format(Locale.getDefault(), "%.2fGB", v / gb)
        v >= mb -> String.format(Locale.getDefault(), "%.2fMB", v / mb)
        v >= kb -> String.format(Locale.getDefault(), "%.1fKB", v / kb)
        else -> "${v}B"
    }
}
