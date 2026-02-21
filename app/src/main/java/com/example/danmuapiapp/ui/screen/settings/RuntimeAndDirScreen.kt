package com.example.danmuapiapp.ui.screen.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.screen.home.NormalModeKeepAliveGuideNavigator
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.ui.component.SettingsSwitchItem

@Composable
fun RuntimeAndDirScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val keepAliveEnabled by viewModel.keepAlive.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isModeSwitching = state.status == ServiceStatus.Starting ||
        state.status == ServiceStatus.Stopping ||
        viewModel.isRunModeSwitching

    var showEnableRootAutoStartDialog by remember { mutableStateOf(false) }
    var showDisableRootAutoStartDialog by remember { mutableStateOf(false) }
    var showKeepAliveGuideDialog by remember { mutableStateOf(false) }
    var isBatteryWhitelisted by remember {
        mutableStateOf(NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context))
    }
    val keepAliveManufacturer = remember { NormalModeKeepAliveGuideNavigator.manufacturerName() }
    val recentsLockHint = remember { NormalModeKeepAliveGuideNavigator.recentsLockHint() }
    val autoStartHint = remember { NormalModeKeepAliveGuideNavigator.autoStartHint() }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.runMode) {
        viewModel.refreshRuntimeRelatedStates()
        if (state.runMode == RunMode.Normal) {
            isBatteryWhitelisted = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context)
        }
    }

    DisposableEffect(lifecycleOwner, state.runMode, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRuntimeRelatedStates()
                if (state.runMode == RunMode.Normal) {
                    isBatteryWhitelisted = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
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
                title = "运行模式",
                subtitle = "模式切换与开机自启方案",
                onBack = onBack
            )

            SettingsGroup(title = "运行模式") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        RunMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.runMode == mode,
                                onClick = { viewModel.updateRunMode(mode) },
                                enabled = !isModeSwitching,
                                shape = SegmentedButtonDefaults.itemShape(index, RunMode.entries.size)
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            SettingsGroup(title = "开机自启方案") {
                if (state.runMode == RunMode.Normal) {
                    SettingsSwitchItem(
                        title = "开机自启",
                        subtitle = "普通模式：重启手机后自动启动前台服务",
                        icon = Icons.Rounded.PowerSettingsNew,
                        checked = viewModel.normalBootAutoStartEnabled,
                        onCheckedChange = viewModel::setNormalBootAutoStart
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        title = "无障碍保活",
                        subtitle = if (viewModel.a11yEnabled) {
                            "已检测到无障碍服务，异常退出可自动拉起"
                        } else {
                            "未启用系统无障碍，建议先前往系统开启"
                        },
                        icon = Icons.Rounded.AccessibilityNew,
                        checked = keepAliveEnabled,
                        onCheckedChange = {
                            viewModel.setKeepAliveEnabled(it)
                            viewModel.refreshRuntimeRelatedStates()
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "系统无障碍设置",
                        subtitle = if (viewModel.a11yEnabled) "已启用，点击可管理" else "未启用，点击前往开启",
                        icon = Icons.Rounded.SettingsAccessibility,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }.onFailure {
                                viewModel.postMessage("无法打开无障碍设置")
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = "保活建议",
                        subtitle = if (isBatteryWhitelisted) {
                            "已放行电池策略，可查看上锁/自启动等可选建议"
                        } else {
                            "建议先设置电池不受限制，再按需配置上锁与自启动"
                        },
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = { showKeepAliveGuideDialog = true }
                    )
                } else if (state.runMode == RunMode.Root) {
                    SettingsSwitchItem(
                        title = "开机自启（模块）",
                        subtitle = if (viewModel.rootBootAutoStartEnabled) {
                            "已开启：开机后以 Root 触发一次启动"
                        } else {
                            "关闭：不开机自动启动"
                        },
                        icon = Icons.Rounded.Verified,
                        checked = viewModel.rootBootAutoStartEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showEnableRootAutoStartDialog = true
                            else showDisableRootAutoStartDialog = true
                        }
                    )
                    if (viewModel.isRootAutoStartOperating) {
                        SettingsDivider(startIndent = 16.dp)
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = "正在处理 Root 模块...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (showEnableRootAutoStartDialog) {
        AlertDialog(
            onDismissRequest = { showEnableRootAutoStartDialog = false },
            title = { Text("开启开机自启") },
            text = {
                Text("将安装 Magisk/KernelSU 模块，开机后以 Root 触发一次启动，不轮询保活，更省电。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showEnableRootAutoStartDialog = false
                    viewModel.enableRootBootAutoStart()
                }) { Text("安装并开启") }
            },
            dismissButton = {
                TextButton(onClick = { showEnableRootAutoStartDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDisableRootAutoStartDialog) {
        AlertDialog(
            onDismissRequest = { showDisableRootAutoStartDialog = false },
            title = { Text("关闭开机自启") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("你可以仅关闭自启（保留模块），或直接卸载模块。")
                    TextButton(onClick = {
                        showDisableRootAutoStartDialog = false
                        viewModel.disableRootBootAutoStart(uninstallModule = true)
                    }) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("卸载模块并关闭")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisableRootAutoStartDialog = false
                    viewModel.disableRootBootAutoStart(uninstallModule = false)
                }) { Text("仅关闭") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableRootAutoStartDialog = false }) { Text("取消") }
            }
        )
    }

    if (showKeepAliveGuideDialog && state.runMode == RunMode.Normal) {
        AlertDialog(
            onDismissRequest = { showKeepAliveGuideDialog = false },
            title = { Text("保活建议") },
            text = {
                val guideButtonColors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                )
                val guideButtonBorder = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "当前厂商：$keepAliveManufacturer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val opened = NormalModeKeepAliveGuideNavigator.requestIgnoreBatteryOptimization(context) ||
                                NormalModeKeepAliveGuideNavigator.openAppBatterySettings(context)
                            if (!opened) {
                                viewModel.postMessage("无法打开电池设置，请手动进入应用信息将电池改为不受限制")
                            } else {
                                isBatteryWhitelisted =
                                    NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = guideButtonColors,
                        border = guideButtonBorder
                    ) {
                        Text("电池不受限制")
                    }
                    OutlinedButton(
                        onClick = { viewModel.postMessage(recentsLockHint) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = guideButtonColors,
                        border = guideButtonBorder
                    ) {
                        Text("最近任务上锁（可选）")
                    }
                    OutlinedButton(
                        onClick = {
                            val opened = NormalModeKeepAliveGuideNavigator.openAutoStartSettings(context)
                            if (!opened) {
                                viewModel.postMessage("未找到自启动管理页，请在系统设置中手动允许本应用自启动")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = guideButtonColors,
                        border = guideButtonBorder
                    ) {
                        Text("自启动权限（可选）")
                    }
                    OutlinedButton(
                        onClick = {
                            val opened = NormalModeKeepAliveGuideNavigator.openVendorGuide(context)
                            if (!opened) {
                                viewModel.postMessage("无法打开浏览器，请手动访问 dontkillmyapp.com 查看厂商教程")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = guideButtonColors,
                        border = guideButtonBorder
                    ) {
                        Text("查看厂商后台教程")
                    }
                    Text(
                        autoStartHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeepAliveGuideDialog = false }) { Text("完成") }
            }
        )
    }
}
