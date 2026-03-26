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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.component.*

@Composable
fun NetworkSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val announcementBaseUrl by viewModel.announcementBaseUrl.collectAsStateWithLifecycle()
    var editableAnnouncementUrl by remember(announcementBaseUrl) {
        mutableStateOf(announcementBaseUrl)
    }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
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
                title = "网络设置",
                subtitle = "GitHub 代理线路管理",
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

            SettingsGroup(title = "公告服务") {
                OutlinedTextField(
                    value = editableAnnouncementUrl,
                    onValueChange = { editableAnnouncementUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("公告服务地址") },
                    placeholder = { Text("http://117.72.165.47:18086") }
                )
                Text(
                    "用于拉取公告弹窗内容，默认已指向当前 VPS。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = { editableAnnouncementUrl = announcementBaseUrl }) {
                        Text("重置")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.saveAnnouncementBaseUrl(editableAnnouncementUrl) }
                    ) {
                        Text("保存地址")
                    }
                }
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
}
