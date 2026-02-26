package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.BuildConfig
import androidx.compose.ui.graphics.Color

@Composable
fun SettingsHubScreen(
    onOpenRuntimeAndDir: () -> Unit,
    onOpenThemeDisplay: () -> Unit,
    onOpenWorkDir: () -> Unit,
    onOpenServiceConfig: () -> Unit,
    onOpenDanmuDownload: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenGithubToken: () -> Unit,
    onOpenAdminMode: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val adminSessionState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val hideFromRecents by viewModel.hideFromRecents.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val runModeLabel = state.runMode.label
    val workDirPath = viewModel.workDirInfo.currentBaseDir.absolutePath
    val adminSummary = remember(adminSessionState) { viewModel.adminModeSummary() }

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
                SettingsDivider()
                SettingsItem(
                    title = "弹幕下载",
                    subtitle = "保存路径、格式、命名规则与冲突策略",
                    icon = Icons.Rounded.CloudDownload,
                    onClick = onOpenDanmuDownload
                )
            }

            SettingsGroup(title = "安全与权限") {
                SettingsItem(
                    title = "管理员权限",
                    subtitle = adminSummary,
                    icon = Icons.Rounded.AdminPanelSettings,
                    onClick = onOpenAdminMode
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
                SettingsSwitchItem(
                    title = "隐藏最近任务",
                    subtitle = if (hideFromRecents) {
                        "最近任务中已隐藏本应用"
                    } else {
                        "最近任务中显示本应用"
                    },
                    icon = Icons.Rounded.VisibilityOff,
                    checked = hideFromRecents,
                    onCheckedChange = viewModel::setHideFromRecents
                )
                SettingsDivider()
                SettingsItem(
                    title = "关于",
                    subtitle = "v${BuildConfig.VERSION_NAME} · 更新、链接与使用指南",
                    icon = Icons.Rounded.Info,
                    onClick = onOpenAbout
                )
            }
        }
    }
}
