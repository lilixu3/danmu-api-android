package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppSnackbarHost

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.data.service.CoreUpdateCheckPolicy
import com.example.danmuapiapp.ui.component.*

@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val updateCheckIntervalMinutes by
        viewModel.coreUpdateCheckIntervalMinutes.collectAsStateWithLifecycle()
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
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
                title = "网络设置",
                subtitle = "GitHub 线路与核心更新检查",
                onBack = onBack
            )

            SettingsGroup(title = "代理线路") {
                SettingsValueItem(
                    title = "当前线路",
                    value = viewModel.currentProxyLabel(),
                    icon = Icons.Rounded.Public
                )
                SettingsDivider()
                SettingsItem(
                    title = "测速并选择线路",
                    subtitle = "并发测速，选择最快的代理节点",
                    icon = Icons.Rounded.Speed,
                    onClick = viewModel::openProxyPicker
                )
            }

            SettingsGroup(title = "更新检查") {
                SettingsItem(
                    title = "核心自动检查",
                    subtitle = "进入前台时判断，距上次自动检查满 $updateCheckIntervalMinutes 分钟才请求",
                    icon = Icons.Rounded.Schedule,
                    onClick = { showUpdateIntervalDialog = true },
                    trailing = { Text("$updateCheckIntervalMinutes 分钟") }
                )
            }
        }
    }

    if (viewModel.showProxyPickerDialog) {
        GithubProxyPickerDialog(
            title = "选择 GitHub 线路",
            subtitle = "测速会并发进行，先完成的线路会先显示延迟",
            options = viewModel.proxyOptions,
            selectedId = viewModel.proxySelectedId,
            testingIds = viewModel.proxyTestingIds,
            resultMap = viewModel.proxyLatencyMap,
            onSelect = viewModel::selectProxy,
            onRetest = viewModel::retestProxySpeed,
            onConfirm = viewModel::confirmProxySelection,
            onDismiss = viewModel::dismissProxyPickerDialog,
            confirmText = "保存线路"
        )
    }

    if (showUpdateIntervalDialog) {
        AppDialog(
            onDismissRequest = { showUpdateIntervalDialog = false },
            style = AppDialogStyle.Selection,
            tone = AppDialogTone.Info,
            icon = { Icon(Icons.Rounded.Schedule, null) },
            title = { Text("核心自动检查间隔") },
            supportingText = { Text("仅在应用进入前台时检查是否已到间隔") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CoreUpdateCheckPolicy.intervalOptionsMinutes.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCoreUpdateCheckIntervalMinutes(minutes)
                                    showUpdateIntervalDialog = false
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = updateCheckIntervalMinutes == minutes,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$minutes 分钟")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateIntervalDialog = false }) { Text("取消") }
            }
        )
    }
}
