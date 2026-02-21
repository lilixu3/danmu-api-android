package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.RunMode
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.ui.component.*

@Composable
fun ServiceConfigScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var portText by remember(state.port) { mutableStateOf(state.port.toString()) }
    var tokenText by remember(state.token) { mutableStateOf(state.token) }
    var showTokenField by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf<String?>(null) }

    val hasChange = portText.trim() != state.port.toString() || tokenText != state.token

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
                title = "服务配置",
                subtitle = "端口与 Token 设置",
                onBack = onBack
            )

            // ── Port & Token ──
            SettingsGroup(title = "连接参数") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it; portError = null },
                        label = { Text("端口") },
                        leadingIcon = { Icon(Icons.Rounded.Lan, null) },
                        supportingText = {
                            val hint = if (state.runMode == RunMode.Normal) {
                                "普通模式支持 1024 – 65535（Root 支持 1 – 65535）"
                            } else {
                                "范围 1 – 65535"
                            }
                            Text(portError ?: hint)
                        },
                        isError = portError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("Token") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { showTokenField = !showTokenField }) {
                                Icon(
                                    if (showTokenField) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    "切换可见"
                                )
                            }
                        },
                        visualTransformation = if (showTokenField) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            GradientButton(
                text = "保存配置",
                onClick = {
                    val port = portText.trim().toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        portError = "请输入有效端口（1-65535）"
                        return@GradientButton
                    }
                    if (state.runMode == RunMode.Normal && port in 1..1023) {
                        portError = "普通模式仅支持 1024-65535，请切换 Root 模式后再使用低位端口"
                        return@GradientButton
                    }
                    viewModel.saveServiceConfig(port, tokenText.trim())
                },
                enabled = hasChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
