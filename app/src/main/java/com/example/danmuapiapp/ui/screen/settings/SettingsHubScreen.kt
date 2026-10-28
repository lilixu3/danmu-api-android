package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppToast


import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.CompactOverviewCard
import com.example.danmuapiapp.ui.component.CompactOverviewItem
import com.example.danmuapiapp.ui.component.FloatingBottomBarContentSpacer
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsSwitchItem

@Composable
fun SettingsHubScreen(
    onOpenRuntimeAndDir: () -> Unit,
    onOpenCompatMode: () -> Unit,
    onOpenThemeDisplay: () -> Unit,
    onOpenWorkDir: () -> Unit,
    onOpenServiceConfig: () -> Unit,
    onOpenDanmuDownload: () -> Unit,
    onOpenDownloadUpdate: () -> Unit,
    onOpenVideoShellInjection: () -> Unit,
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
    val githubAccountStatus by viewModel.githubAccountStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val runModeLabel = state.runMode.label
    val workDirPath = viewModel.workDirInfo.currentBaseDir.absolutePath
    val selectedGithubProxyId by viewModel.selectedGithubProxyId.collectAsStateWithLifecycle()
    val proxyLabel = viewModel.proxyOptions.firstOrNull { it.id == selectedGithubProxyId }?.name ?: "官方线路"
    val githubTokenConfigured = githubAccountStatus.tokenConfigured

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
        "${state.listenMode.label} · ${state.port} 端口 · 尚未启用访问 Token"
    } else {
        "${state.listenMode.label} · ${state.port} 端口 · 已启用访问 Token"
    }
    val quickTileSummary = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> "当前系统版本不支持控制中心快捷按钮"
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
            "可在系统控制中心编辑页手动添加，用于一键启停当前模式"
        }
        else -> "添加到控制中心，用于一键启停普通/Root 模式"
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
            AppToast.show(message)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshWorkDirInfo()
    }

    Scaffold(
        containerColor = Color.Transparent,
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
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            CompactOverviewCard(
                title = "当前配置概览",
                subtitle = "${runModeLabel}模式 · ${state.listenMode.label}",
                icon = Icons.Rounded.Tune,
                status = if (state.token.isBlank()) "Token 未设" else "Token 已设",
                statusColor = if (state.token.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                items = listOf(
                    CompactOverviewItem("端口", state.port.toString(), onClick = onOpenServiceConfig),
                    CompactOverviewItem(
                        label = "下载",
                        value = if (proxyLabel.contains("直连")) "官方线路" else proxyLabel,
                        onClick = onOpenDownloadUpdate
                    ),
                    CompactOverviewItem(
                        label = "权限",
                        value = if (adminSessionState.isAdminMode) "管理员" else "普通",
                        accent = if (adminSessionState.isAdminMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = onOpenAdminMode
                    )
                )
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
                SettingsDivider()
                SettingsItem(
                    title = "控制中心按钮",
                    subtitle = quickTileSummary,
                    icon = Icons.Rounded.PowerSettingsNew,
                    onClick = {
                        viewModel.requestAddQuickSettingsTile(context.findActivity())
                    },
                    trailing = {
                        SettingsStatusBadge(
                            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                "一键添加"
                            } else {
                                "手动添加"
                            },
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "影视壳注入",
                    subtitle = "利用 LSPosed 注入 FongMi/ok影视播放页，实现自动推送弹幕和搜索按钮",
                    icon = Icons.Rounded.Search,
                    onClick = onOpenVideoShellInjection,
                    trailing = {
                        SettingsStatusBadge(
                            text = "LSPosed",
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            SettingsGroup(title = "网络与数据") {
                SettingsItem(
                    title = "网络设置",
                    subtitle = "来源线路、增强直连与 App 代理节点",
                    icon = Icons.Rounded.Public,
                    onClick = onOpenNetwork,
                    trailing = {
                        SettingsStatusBadge(
                            text = "按来源设置",
                            accent = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "下载与更新",
                    subtitle = "GitHub 连接、下载镜像与核心更新检查",
                    icon = Icons.Rounded.CloudDownload,
                    onClick = onOpenDownloadUpdate
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
                    icon = Icons.Rounded.Folder,
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
                    subtitle = "主题模式、访问入口形式与界面缩放",
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
                    title = "兼容模式",
                    subtitle = "界面选择、电视 / 盒子与遥控器操作",
                    icon = Icons.Rounded.Tv,
                    onClick = onOpenCompatMode
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
            FloatingBottomBarContentSpacer()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
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
