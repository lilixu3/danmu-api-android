package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppSnackbarHost

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GithubTokenScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val status by viewModel.githubAccountStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tokenText by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshGithubAccount() }
    LaunchedEffect(status.isLoading, status.tokenValid) {
        if (!status.isLoading && status.tokenValid == true) {
            tokenText = ""
            showToken = false
        }
    }
    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { AppSnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "GitHub 凭据",
                subtitle = "验证身份并查看真实 REST API 小时额度",
                onBack = onBack
            )

            GithubStatusPanel(status = status, onRefresh = viewModel::refreshGithubAccount)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Personal Access Token", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "仅发往 GitHub 官方 API，验证成功后保存到本机凭据存储",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("Token") },
                        placeholder = {
                            if (status.tokenConfigured) Text("已配置；输入新 Token 以替换")
                        },
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    "切换 Token 可见状态"
                                )
                            }
                        },
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.saveGithubToken(tokenText) },
                            enabled = !status.isLoading &&
                                (tokenText.isNotBlank() || !status.tokenConfigured),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (status.isLoading) {
                                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(if (tokenText.isBlank()) "使用匿名额度" else "验证并保存")
                        }
                        OutlinedButton(
                            onClick = {
                                tokenText = ""
                                viewModel.clearGithubToken()
                            },
                            enabled = status.tokenConfigured || tokenText.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("清空") }
                    }
                }
            }

            Text(
                "不填写 Token 也会查询并显示当前网络出口的匿名额度。匿名请求通常每小时 60 次；有效 Token 通常每小时 5,000 次，最终以 GitHub 实时响应为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun GithubStatusPanel(status: GithubAccountStatus, onRefresh: () -> Unit) {
    val ratio = if (status.coreLimit != null && status.coreLimit > 0 && status.coreRemaining != null) {
        status.coreRemaining.toFloat() / status.coreLimit
    } else null
    val color = when {
        status.tokenValid == true -> Color(0xFF1F7A4D)
        status.tokenValid == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    when {
                        status.tokenValid == true -> Icons.Rounded.CheckCircle
                        status.tokenValid == false -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.Person
                    },
                    null,
                    Modifier.size(25.dp),
                    tint = color
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            status.isLoading -> "正在连接 GitHub"
                            status.tokenValid == true -> "身份验证成功"
                            status.tokenValid == false -> "Token 无效"
                            else -> "匿名访问"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        status.login?.let { "@$it" } ?: if (status.tokenConfigured) "尚未通过身份验证" else "未配置 Token",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh, enabled = !status.isLoading) {
                    if (status.isLoading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "刷新")
                }
            }

            if (status.coreRemaining != null && status.coreLimit != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${status.coreRemaining}", style = MaterialTheme.typography.headlineMedium, color = color)
                        Text("本小时剩余", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("/ ${status.coreLimit}", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(resetText(status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (ratio != null) {
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
            status.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun resetText(status: GithubAccountStatus): String {
    val epoch = status.coreResetEpochSeconds ?: return "重置时间未知"
    val formatted = runCatching {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochSecond(epoch))
    }.getOrNull() ?: return "重置时间未知"
    return "$formatted 重置"
}
