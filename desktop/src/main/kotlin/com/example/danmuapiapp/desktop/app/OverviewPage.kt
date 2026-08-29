package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.FirewallManager
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import java.awt.Desktop
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.io.File

@Composable
fun OverviewPage(
    controller: DesktopRuntimeController,
    paths: DesktopPaths,
    state: ServiceUiState,
    isDark: Boolean,
) {
    val configuredListenHost = remember {
        runCatching { controller.configuredRuntime().listenHost }.getOrDefault("0.0.0.0")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "服务概览",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            StatusChip(state, isDark)
            Spacer(Modifier.weight(1f))
        }

        HeroStatusCard(controller, state, isDark)

        if (state.phase == ServicePhase.Failed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        SectionCard(title = "连接") {
            val listening = state.phase == ServicePhase.Running
            InfoRow(
                label = "本机访问",
                value = state.port?.let { "http://127.0.0.1:$it" } ?: "—",
                monospace = true,
                action = if (listening) {
                    { CopyAction("http://127.0.0.1:${state.port}") }
                } else {
                    null
                },
            )
            InfoDivider()
            val lanIp = rememberLanAddress()
            InfoRow(
                label = "局域网访问",
                value = if (listening) {
                    lanIp?.let { ip -> "http://$ip:${state.port}" } ?: "未检测到局域网地址"
                } else {
                    "—"
                },
                monospace = true,
                supporting = if (listening) {
                    "监听 $configuredListenHost；防火墙已放行 node.exe 入站"
                } else {
                    "启动后按监听地址开放局域网访问"
                },
            )
            if (listening && configuredListenHost == "0.0.0.0") {
                val nodeExe = File(paths.runtimeDir, "node.exe")
                var firewallState by androidx.compose.runtime.remember {
                    mutableStateOf(
                        runCatching {
                            FirewallManager.hasInboundRule(nodeExe.absolutePath)
                        }.getOrDefault(true),
                    )
                }
                if (!firewallState) {
                    InfoRow(
                        label = "防火墙",
                        value = "尚未放行局域网入站",
                        supporting = "点击按钮提权添加放行规则（允许 node.exe 入站）",
                        action = {
                            TextButton(onClick = {
                                firewallState = runCatching {
                                    FirewallManager.ensureInboundRule(
                                        nodeExe.absolutePath,
                                        "DanmuApi node.exe",
                                    ) == null
                                }.getOrDefault(false)
                            }) { Text("添加放行") }
                        },
                    )
                }
            }
            InfoDivider()
            InfoRow(
                label = "访问 Token",
                value = DEFAULT_TOKEN_HINT,
                monospace = true,
                action = { CopyAction(DEFAULT_TOKEN_HINT) },
                supporting = "核心默认值；后续可在配置页修改",
            )
        }

        SectionCard(title = "核心与运行时") {
            InfoRow(label = "核心变体", value = "stable（稳定版）")
            InfoDivider()
            InfoRow(
                label = "核心来源",
                value = "${DesktopCoreInstaller.STABLE_REPO} · 在线安装（不随包内置）",
                supporting = "与 Android 端一致；无法直连 GitHub 时可在 设置 → GitHub 线路 选择加速镜像",
            )
            InfoDivider()
            InfoRow(label = "Node 运行时", value = "v24.19.0（win-x64，随包内置）")
            InfoDivider()
            InfoRow(
                label = "安装身份",
                value = state.runtimeIdentity ?: "服务启动后分配",
                monospace = true,
            )
        }

        SectionCard(title = "目录") {
            InfoRow(
                label = "运行目录",
                value = paths.root.absolutePath,
                monospace = true,
                supporting = "运行时、配置（.env）、缓存（.cache）、日志与下载都在这里；卸载默认保留",
                action = {
                    TextButton(onClick = { openInExplorer(paths.root) }) { Text("打开文件夹") }
                },
            )
            InfoDivider()
            InfoRow(
                label = "运行目录自定义",
                value = "设置 → 运行目录",
                supporting = "支持更改到任意可写目录，更改后重启应用生效",
            )
        }
    }
}

@Composable
private fun HeroStatusCard(controller: DesktopRuntimeController, state: ServiceUiState, isDark: Boolean) {
    val container = when (state.phase) {
        ServicePhase.Running -> if (isDark) Color(0xFF12351F) else Color(0xFFE4F3E9)
        ServicePhase.Failed -> MaterialTheme.colorScheme.errorContainer
        ServicePhase.Preparing, ServicePhase.Starting, ServicePhase.Stopping ->
            MaterialTheme.colorScheme.primaryContainer
        ServicePhase.Stopped -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    Card(
        shape = RoundedCornerShape(Dimens.CardCorner),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(statusColor(state.phase, isDark), CircleShape),
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = heroTitle(state),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.width(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.canStart) {
                    Button(onClick = { controller.start() }, enabled = !state.isBusy) {
                        Text(if (state.phase == ServicePhase.Failed) "重新启动" else "启动服务")
                    }
                }
                if (state.canStop) {
                    OutlinedButton(onClick = { controller.stop() }, enabled = !state.isBusy) {
                        Text("停止")
                    }
                    FilledTonalButton(onClick = { controller.restart() }, enabled = !state.isBusy) {
                        Text("重启")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    monospace: Boolean = false,
    supporting: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
private fun InfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun CopyAction(text: String) {
    TextButton(onClick = { copyToClipboard(text) }) { Text("复制") }
}

@Composable
private fun rememberLanAddress(): String? {
    return androidx.compose.runtime.remember { DesktopTray.lanAddress() }
}

private fun heroTitle(state: ServiceUiState): String = when (state.phase) {
    ServicePhase.Stopped -> "服务已停止"
    ServicePhase.Preparing -> "正在准备运行时"
    ServicePhase.Starting -> "正在启动服务"
    ServicePhase.Running -> "服务运行中"
    ServicePhase.Stopping -> "正在停止服务"
    ServicePhase.Failed -> "启动失败"
}

@Composable
private fun StatusChip(state: ServiceUiState, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = statusColor(state.phase, isDark).copy(alpha = 0.14f),
        contentColor = statusColor(state.phase, isDark),
    ) {
        Text(
            text = phaseLabel(state.phase),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** 核心默认 Token（与 Android TokenDefaults.FALLBACK_DEFAULT_TOKEN 一致）。 */
private const val DEFAULT_TOKEN_HINT = "87654321"

private fun copyToClipboard(text: String) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

private fun openInExplorer(dir: File) {
    runCatching {
        dir.mkdirs()
        Desktop.getDesktop().open(dir)
    }
}
