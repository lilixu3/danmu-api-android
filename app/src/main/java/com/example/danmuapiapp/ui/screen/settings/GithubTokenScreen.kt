package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.ui.component.SettingsValueItem

@Composable
fun GithubTokenScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var tokenText by remember(githubToken) { mutableStateOf(githubToken) }
    var showToken by remember { mutableStateOf(false) }
    val trimmed = tokenText.trim()
    val hasChanged = trimmed != githubToken

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
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
                title = "GitHub Token",
                subtitle = "用于提升 GitHub API 检查额度",
                onBack = onBack
            )

            SettingsGroup(title = "Token 配置") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("Personal Access Token") },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "切换可见"
                                )
                            }
                        },
                        visualTransformation = if (showToken) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.saveGithubToken(trimmed) },
                            enabled = hasChanged,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("保存 Token")
                        }
                        OutlinedButton(
                            onClick = {
                                tokenText = ""
                                viewModel.clearGithubToken()
                            },
                            enabled = githubToken.isNotBlank() || tokenText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空")
                        }
                    }
                }
            }

            SettingsGroup(title = "当前状态") {
                SettingsValueItem(
                    title = "状态",
                    value = if (githubToken.isBlank()) "未配置" else "已配置",
                    icon = Icons.Rounded.Key
                )
            }
        }
    }
}
