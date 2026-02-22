package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsPageHeader

@Composable
fun AdminModeScreen(
    onBack: () -> Unit,
    viewModel: AdminModeViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                title = "管理员权限",
                subtitle = "输入 ADMIN_TOKEN 后开启高级管理能力",
                onBack = onBack
            )

            SettingsGroup(title = "当前状态") {
                SettingsItem(
                    title = if (state.isAdminMode) "管理员模式已开启" else "管理员模式未开启",
                    subtitle = if (state.hasAdminTokenConfigured) {
                        "已配置 ADMIN_TOKEN：${state.tokenHint}"
                    } else {
                        "当前尚未配置 ADMIN_TOKEN"
                    },
                    icon = if (state.isAdminMode) Icons.Rounded.CheckCircle else Icons.Rounded.AdminPanelSettings
                )
                if (!state.isAdminMode && state.hasAdminTokenConfigured) {
                    SettingsItem(
                        title = "说明",
                        subtitle = "开启后可查看真实设备 IP，并允许编辑敏感配置项",
                        icon = Icons.Rounded.Info
                    )
                }
            }

            if (!state.isAdminMode) {
                val actionText = if (state.hasAdminTokenConfigured) "进入管理员模式" else "保存并进入管理员模式"
                SettingsGroup(title = if (state.hasAdminTokenConfigured) "输入令牌" else "首次配置") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        OutlinedTextField(
                            value = viewModel.tokenInput,
                            onValueChange = viewModel::updateTokenInput,
                            label = { Text("ADMIN_TOKEN") },
                            leadingIcon = { Icon(Icons.Rounded.Security, null) },
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleTokenVisible) {
                                    Icon(
                                        if (viewModel.showToken) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = "切换可见"
                                    )
                                }
                            },
                            visualTransformation = if (viewModel.showToken) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::submit,
                            enabled = !viewModel.isOperating && viewModel.tokenInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.LockOpen, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(actionText)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (state.hasAdminTokenConfigured) {
                                "若输入错误会保持普通模式，不会更改当前配置。"
                            } else {
                                "首次设置会写入当前运行模式对应的 config/.env。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                SettingsGroup(title = "会话控制") {
                    SettingsItem(
                        title = "退出管理员模式",
                        subtitle = "退出后仅保留普通访问能力",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = viewModel::logout
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "提示：切换运行模式或更换核心目录后，建议重新确认管理员状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (viewModel.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("知道了")
                }
            }
        )
    }
}
