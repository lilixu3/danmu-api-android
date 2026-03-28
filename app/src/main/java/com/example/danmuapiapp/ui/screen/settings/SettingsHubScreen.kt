package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.data.service.HarmonyCompatDetector
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.SettingsSwitchItem

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
    onOpenHarmonyGuide: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val adminSessionState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val hideFromRecents by viewModel.hideFromRecents.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val runModeLabel = state.runMode.label
    val workDirPath = viewModel.workDirInfo.currentBaseDir.absolutePath
    val proxyLabel = viewModel.currentProxyLabel()
    val githubTokenConfigured = viewModel.githubTokenSummary() != "未配置"

    val runModeSummary = if (state.runMode == RunMode.Root) {
        "适合长期后台、自启和低端口监听"
    } else {
        "无需 Root，适合日常使用与快速调试"
    }
    val workDirSummary = if (state.runMode == RunMode.Root) {
        "Root 专用运行目录，适合自启与常驻"
    } else {
        "应用私有目录，由系统统一管理"
    }
    val serviceSummary = if (state.token.isBlank()) {
        "当前监听 ${state.port} 端口，尚未启用访问 Token"
    } else {
        "当前监听 ${state.port} 端口，已启用访问 Token"
    }
    val githubTokenSummary = if (githubTokenConfigured) {
        "已配置，用于提升 GitHub API 配额与下载稳定性"
    } else {
        "未配置，检查更新与下载时更容易触发限流"
    }
    val adminSummary = when {
        adminSessionState.isAdminMode -> "已开启，可进入高级能力与敏感配置"
        adminSessionState.hasAdminTokenConfigured -> "已配置 ADMIN_TOKEN，点击后可进入管理员模式"
        else -> "未配置 ADMIN_TOKEN，部分高级能力将保持隐藏"
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
                subtitle = "先看当前结论，再进入二级页调整细节"
            )

            SettingsOverviewCard(
                runModeLabel = runModeLabel,
                port = state.port,
                proxyLabel = proxyLabel,
                adminEnabled = adminSessionState.isAdminMode
            )

            SettingsGroup(title = "核心运行") {
                SettingsItem(
                    title = "运行模式",
                    subtitle = "$runModeSummary\n当前：$runModeLabel",
                    icon = Icons.Rounded.Tune,
                    onClick = onOpenRuntimeAndDir,
                    trailing = {
                        SettingsStatusBadge(
                            text = runModeLabel,
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "工作目录",
                    subtitle = "$workDirSummary\n$workDirPath",
                    icon = Icons.Rounded.Folder,
                    onClick = onOpenWorkDir,
                    trailing = {
                        SettingsStatusBadge(
                            text = if (state.runMode == RunMode.Root) "Root 目录" else "应用目录",
                            accent = MaterialTheme.colorScheme.secondary
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "服务配置",
                    subtitle = serviceSummary,
                    icon = Icons.Rounded.Lan,
                    onClick = onOpenServiceConfig,
                    trailing = {
                        SettingsStatusBadge(
                            text = "TCP ${state.port}",
                            accent = MaterialTheme.colorScheme.tertiary
                        )
                    }
                )
            }

            SettingsGroup(title = "网络与数据") {
                SettingsItem(
                    title = "GitHub 代理",
                    subtitle = "当前使用 $proxyLabel，用于检查更新与下载核心",
                    icon = Icons.Rounded.Public,
                    onClick = onOpenNetwork,
                    trailing = {
                        SettingsStatusBadge(
                            text = if (proxyLabel.contains("直连")) "直连" else "已配置",
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "GitHub Token",
                    subtitle = githubTokenSummary,
                    icon = Icons.Rounded.VpnKey,
                    onClick = onOpenGithubToken,
                    trailing = {
                        SettingsStatusBadge(
                            text = if (githubTokenConfigured) "已配置" else "未配置",
                            accent = if (githubTokenConfigured) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "备份与恢复",
                    subtitle = "导入导出 .env，可扫码同步到 TV，也可同步到 WebDAV",
                    icon = Icons.Rounded.CloudSync,
                    onClick = onOpenBackupRestore
                )
                SettingsDivider()
                SettingsItem(
                    title = "弹幕下载",
                    subtitle = "管理保存路径、格式、命名规则与冲突策略",
                    icon = Icons.Rounded.CloudDownload,
                    onClick = onOpenDanmuDownload
                )
            }

            SettingsGroup(title = "安全与权限") {
                SettingsItem(
                    title = "管理员权限",
                    subtitle = adminSummary,
                    icon = Icons.Rounded.AdminPanelSettings,
                    onClick = onOpenAdminMode,
                    trailing = {
                        SettingsStatusBadge(
                            text = when {
                                adminSessionState.isAdminMode -> "已开启"
                                adminSessionState.hasAdminTokenConfigured -> "待登录"
                                else -> "未配置"
                            },
                            accent = if (adminSessionState.isAdminMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                )
            }

            SettingsGroup(title = "偏好与信息") {
                SettingsItem(
                    title = "主题与显示",
                    subtitle = "主题模式、明暗跟随与界面缩放",
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
                    title = "鸿蒙后台权限引导",
                    subtitle = if (HarmonyCompatDetector.isLikelyHarmonyCompat()) {
                        "检测到疑似鸿蒙兼容环境，建议查看权限放行指引"
                    } else {
                        "手动查看鸿蒙/卓易通后台权限设置指引"
                    },
                    icon = Icons.Rounded.PhoneAndroid,
                    onClick = onOpenHarmonyGuide
                )
                SettingsDivider()
                SettingsItem(
                    title = "关于",
                    subtitle = "当前版本 v${BuildConfig.VERSION_NAME}，可查看更新、链接与使用指南",
                    icon = Icons.Rounded.Info,
                    onClick = onOpenAbout,
                    trailing = {
                        SettingsStatusBadge(
                            text = "v${BuildConfig.VERSION_NAME}",
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsOverviewCard(
    runModeLabel: String,
    port: Int,
    proxyLabel: String,
    adminEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "当前配置概览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "先看模式、端口、线路和权限状态，再进入二级页改细节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsOverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "运行模式",
                    value = runModeLabel,
                    accent = MaterialTheme.colorScheme.primary
                )
                SettingsOverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "监听端口",
                    value = "TCP $port",
                    accent = MaterialTheme.colorScheme.tertiary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsOverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "GitHub 线路",
                    value = proxyLabel,
                    accent = MaterialTheme.colorScheme.secondary
                )
                SettingsOverviewMetric(
                    modifier = Modifier.weight(1f),
                    label = "管理员",
                    value = if (adminEnabled) "已开启" else "未开启",
                    accent = if (adminEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsOverviewMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingsStatusBadge(
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
