package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeConfigResolver
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.FirewallManager
import com.example.danmuapiapp.desktop.runtime.HealthReadState
import com.example.danmuapiapp.desktop.runtime.RuntimeHealthClient
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.NetworkInterface
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class FirewallCheckState {
    NotChecked,
    Checking,
    Allowed,
    NotAllowed,
    Failed,
}

/**
 * 服务控制台首页。运行时状态以 controller 为唯一控制入口，健康数据以 /__health 为准。
 *
 * The public signature is intentionally kept stable because DesktopShell calls this function
 * directly. Long-running or operating-system calls are dispatched away from the Compose UI.
 */
@Composable
fun OverviewPage(
    controller: DesktopRuntimeController,
    paths: DesktopPaths,
    state: ServiceUiState,
    isDark: Boolean,
    showHeader: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    refreshKey: Int = 0,
) {
    // Do not remember this value: settings and config/.env may change while the page is open.
    val configuredRuntime = controller.configuredRuntime()
    val scriptDir = File(paths.runtimeDir, "nodejs-project")
    val envValues = DesktopRuntimeConfigResolver.readEnv(scriptDir)
    val configuredToken = envValues["TOKEN"]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_API_TOKEN
    val localUrl = if (state.phase == ServicePhase.Running) {
        buildApiAddress("127.0.0.1", configuredRuntime.port, configuredToken)
    } else {
        null
    }
    val scope = rememberCoroutineScope()
    val healthClient = remember { RuntimeHealthClient() }
    var healthState by remember { mutableStateOf<HealthReadState>(HealthReadState.Idle) }
    var lastHealthySnapshot by remember { mutableStateOf<com.example.danmuapiapp.desktop.runtime.RuntimeHealthSnapshot?>(null) }
    var healthSnapshotReceivedAtMs by remember { mutableStateOf(0L) }
    var displayNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var lanAddresses by remember { mutableStateOf(DesktopLanAddresses()) }
    var lanCheckState by remember { mutableStateOf<AsyncCheckState>(AsyncCheckState.NotChecked) }
    var lanDiagnostic by remember { mutableStateOf<String?>(null) }
    var firewallState by remember { mutableStateOf(FirewallCheckState.NotChecked) }
    var firewallMessage by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }

    LaunchedEffect(state.phase, lastHealthySnapshot?.pid, lastHealthySnapshot?.runtimeIdentity) {
        if (state.phase != ServicePhase.Running || lastHealthySnapshot == null) {
            displayNowMs = System.currentTimeMillis()
            return@LaunchedEffect
        }
        while (isActive) {
            displayNowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(state.phase, configuredRuntime.listenHost, configuredRuntime.ipv6Enabled) {
        if (state.phase != ServicePhase.Running || !isWildcardHostValue(configuredRuntime.listenHost)) {
            lanAddresses = DesktopLanAddresses()
            lanDiagnostic = null
            lanCheckState = AsyncCheckState.NotChecked
            return@LaunchedEffect
        }
        lanCheckState = AsyncCheckState.Checking
        val result = withContext(Dispatchers.IO) { discoverLanAddresses() }
        result.fold(
            onSuccess = { addresses ->
                lanAddresses = addresses
                lanDiagnostic = when {
                    addresses.ipv4 == null && configuredRuntime.ipv6Enabled && addresses.ipv6 == null ->
                        "未检测到可用的局域网 IPv4 或 IPv6 地址"
                    addresses.ipv4 == null -> "未检测到局域网 IPv4 地址"
                    configuredRuntime.ipv6Enabled && addresses.ipv6 == null -> "当前网络未分配可用的 IPv6 地址"
                    else -> null
                }
                lanCheckState = AsyncCheckState.Ready
            },
            onFailure = { error ->
                lanAddresses = DesktopLanAddresses()
                lanDiagnostic = "读取局域网地址失败：${diagnostic(error)}"
                lanCheckState = AsyncCheckState.Failed
            },
        )
    }

    LaunchedEffect(configuredRuntime.port, configuredRuntime.listenHost, state.phase, refreshNonce, refreshKey) {
        if (state.phase != ServicePhase.Running) {
            healthState = HealthReadState.Idle
            lastHealthySnapshot = null
            isRefreshing = false
            return@LaunchedEffect
        }

        while (isActive) {
            val hasPreviousSnapshot = lastHealthySnapshot != null
            isRefreshing = true
            if (!hasPreviousSnapshot) healthState = HealthReadState.Loading
            val result = try {
                withContext(Dispatchers.IO) { healthClient.read(configuredRuntime.port) }
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.fold(
                onSuccess = { snapshot ->
                    lastHealthySnapshot = snapshot
                    healthSnapshotReceivedAtMs = System.currentTimeMillis()
                    displayNowMs = healthSnapshotReceivedAtMs
                    healthState = HealthReadState.Ready(snapshot)
                },
                onFailure = { error ->
                    healthState = HealthReadState.Unavailable(
                        reason = "读取 /__health 失败：${diagnostic(error)}",
                        cause = error,
                    )
                    // 健康检查只负责读取诊断；由控制器核验真实子进程，避免页面长期停留在 Running。
                    controller.reconcileLiveness()
                },
            )
            isRefreshing = false
            delay(5_000)
        }
    }

    fun launchDesktopAction(successMessage: String, operation: () -> Result<Unit>) {
        if (actionBusy) return
        actionBusy = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { operation() }
            } catch (error: Exception) {
                Result.failure(error)
            }
            actionNotice = result.fold(
                onSuccess = { successMessage },
                onFailure = { error -> "操作失败：${diagnostic(error)}" },
            )
            actionBusy = false
        }
    }

    val healthSnapshot = (healthState as? HealthReadState.Ready)?.snapshot ?: lastHealthySnapshot
    val healthFailure = healthFailureMessage(healthState)
    val healthStatus = healthStatus(state.phase, healthState)
    val metricStatus = if (healthFailure != null) DesktopStatus.Error else state.phase.toDesktopStatus()
    val palette = LocalDesktopThemePalette.current
    val heroColors = palette.colorsFor(state.phase.toDesktopStatus())
    val isWildcardHost = configuredRuntime.listenHost == "0.0.0.0" || configuredRuntime.listenHost == "::"
    val nodeExe = File(paths.runtimeDir, "node.exe")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DesktopTokens.PagePadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
    ) {
        if (showHeader) DesktopPageHeader(
            title = "服务控制台",
            subtitle = "管理服务状态、访问入口与运行诊断",
            status = state.phase.toDesktopStatus(),
            leadingContent = {
                DesktopIcon(
                    icon = DesktopIcons.Overview,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 22.sp,
                )
            },
            actions = {
                DesktopActionButton(
                    label = if (isRefreshing) "刷新中…" else "刷新状态",
                    onClick = { refreshNonce++; onRefresh?.invoke() },
                    enabled = state.phase == ServicePhase.Running,
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Restart,
                )
            },
        )

        DesktopSurface(
            modifier = Modifier.fillMaxWidth(),
            color = heroColors.container,
            contentColor = heroColors.onContainer,
            tonalElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(DesktopTokens.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DesktopIcon(
                    icon = when (state.phase) {
                        ServicePhase.Running -> DesktopIcons.Success
                        ServicePhase.CoreSetupRequired -> DesktopIcons.Downloads
                        ServicePhase.Failed -> DesktopIcons.Error
                        ServicePhase.Stopped -> DesktopIcons.Info
                        else -> DesktopIcons.Restart
                    },
                    tint = heroColors.content,
                    size = 30.sp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = heroTitle(state.phase),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DesktopStatusBadge(
                            status = state.phase.toDesktopStatus(),
                            label = phaseLabel(state.phase),
                            compact = true,
                        )
                    }
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = heroColors.onContainer.copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canStart) {
                        DesktopActionButton(
                            label = when (state.phase) {
                                ServicePhase.Failed -> "重新启动"
                                ServicePhase.CoreSetupRequired -> "重新检查核心"
                                else -> "启动服务"
                            },
                            onClick = { controller.start() },
                            enabled = !state.isBusy,
                            icon = if (state.phase == ServicePhase.Failed) DesktopIcons.Restart else DesktopIcons.Start,
                        )
                    }
                    if (state.canStop) {
                        DesktopActionButton(
                            label = "停止",
                            onClick = { controller.stop() },
                            enabled = !state.isBusy,
                            style = DesktopActionButtonStyle.Outlined,
                            icon = DesktopIcons.Stop,
                        )
                        DesktopActionButton(
                            label = "重启",
                            onClick = { controller.restart() },
                            enabled = !state.isBusy,
                            style = DesktopActionButtonStyle.Tonal,
                            icon = DesktopIcons.Restart,
                        )
                    }
                }
            }
        }

        when (state.phase) {
            ServicePhase.CoreSetupRequired -> DesktopRestartBanner(
                title = "需要先准备核心",
                message = state.message,
                actionLabel = "重新检查",
                onRestart = if (!state.isBusy) controller::start else null,
            )
            ServicePhase.Failed -> DesktopRestartBanner(
                title = "服务启动失败",
                message = state.message,
                actionLabel = "重新启动",
                onRestart = if (!state.isBusy) controller::restart else null,
            )
            else -> Unit
        }

        if (healthFailure != null) {
            DesktopSurface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DesktopIcon(DesktopIcons.Error, tint = MaterialTheme.colorScheme.error)
                    Column {
                        Text("健康检查诊断", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(healthFailure, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(
            text = "运行指标",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "运行时长",
                value = healthValue(
                    effectiveUptimeSeconds(healthSnapshot?.uptimeSec, healthSnapshotReceivedAtMs, displayNowMs),
                    state.phase,
                    healthState,
                    ::formatUptime,
                ),
                supportingText = "来自 /__health，按秒连续计时",
                modifier = Modifier.weight(1f),
                status = metricStatus,
                icon = DesktopIcons.Activity,
            )
            DesktopMetricCard(
                label = "请求总数",
                value = healthValue(healthSnapshot?.requestCount, state.phase, healthState),
                supportingText = "进程内累计请求",
                modifier = Modifier.weight(1f),
                status = metricStatus,
                icon = DesktopIcons.Link,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap), modifier = Modifier.fillMaxWidth()) {
            DesktopMetricCard(
                label = "进程 PID",
                value = healthValue(healthSnapshot?.pid, state.phase, healthState),
                supportingText = "Node 进程",
                modifier = Modifier.weight(1f),
                status = metricStatus,
                icon = DesktopIcons.Core,
            )
            DesktopMetricCard(
                label = "缓存探针",
                value = healthValue(
                    healthSnapshot?.cacheProbeWritable,
                    state.phase,
                    healthState,
                ) { if (it) "可写" else "不可写" },
                supportingText = "运行时缓存目录",
                modifier = Modifier.weight(1f),
                status = metricStatus,
                icon = DesktopIcons.Tools,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
            verticalAlignment = Alignment.Top,
        ) {
            DesktopSectionCard(
                title = "访问入口",
                supportingText = "本机访问不依赖局域网配置。",
                modifier = Modifier.weight(1f),
            ) {
                DesktopApiEndpointRow(
                    label = "本机 API",
                    value = localUrl ?: "尚未启动",
                    supportingText = if (localUrl != null) "复制或打开都会使用完整 API 地址（包含访问凭证）。" else null,
                    action = localUrl?.let { url ->
                        {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DesktopActionButton(
                                    label = "打开",
                                    onClick = { launchDesktopAction("已打开本机 API 地址") { openUrl(url) } },
                                    enabled = !actionBusy,
                                    style = DesktopActionButtonStyle.Tonal,
                                    icon = DesktopIcons.Link,
                                )
                                DesktopActionButton(
                                    label = "复制完整地址",
                                    onClick = { launchDesktopAction("已复制完整 API 地址") { copyToClipboard(url) } },
                                    enabled = !actionBusy,
                                    style = DesktopActionButtonStyle.Outlined,
                                    icon = DesktopIcons.Copy,
                                )
                            }
                        }
                    },
                )
                DesktopDivider()
                if (isWildcardHost) {
                    val lanApiUrl = lanAddresses.ipv4?.let {
                        buildApiAddress(it, configuredRuntime.port, configuredToken)
                    }
                    val lanIpv6ApiUrl = if (configuredRuntime.ipv6Enabled) {
                        lanAddresses.ipv6?.let { buildApiAddress(it, configuredRuntime.port, configuredToken) }
                    } else {
                        null
                    }
                    val lanValue = when (lanCheckState) {
                        AsyncCheckState.NotChecked -> "尚未启动"
                        AsyncCheckState.Checking -> "正在读取局域网地址…"
                        AsyncCheckState.Ready -> lanApiUrl ?: "未检测到局域网 IPv4 地址"
                        AsyncCheckState.Failed -> "读取失败"
                    }
                    DesktopApiEndpointRow(
                        label = "局域网 API（推荐）",
                        value = lanValue,
                        supportingText = when (lanCheckState) {
                            AsyncCheckState.Failed -> lanDiagnostic ?: "无法读取网卡信息"
                            AsyncCheckState.Ready -> if (lanApiUrl != null) "推荐局域网设备使用；地址已包含访问凭证，请勿公开分享。" else lanDiagnostic
                            else -> "服务启动后自动读取本机局域网 IPv4 地址。"
                        },
                        action = lanApiUrl?.let { url ->
                            {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DesktopActionButton(
                                        label = "打开",
                                        onClick = { launchDesktopAction("已打开局域网 API 地址") { openUrl(url) } },
                                        enabled = !actionBusy,
                                        style = DesktopActionButtonStyle.Tonal,
                                        icon = DesktopIcons.Link,
                                    )
                                    DesktopActionButton(
                                        label = "复制完整地址",
                                        onClick = { launchDesktopAction("已复制完整局域网 API 地址") { copyToClipboard(url) } },
                                        enabled = !actionBusy,
                                        style = DesktopActionButtonStyle.Outlined,
                                        icon = DesktopIcons.Copy,
                                    )
                                }
                            }
                        },
                    )
                    if (configuredRuntime.ipv6Enabled) {
                        DesktopDivider()
                        DesktopApiEndpointRow(
                            label = "IPv6 API",
                            value = when (lanCheckState) {
                                AsyncCheckState.NotChecked -> "尚未启动"
                                AsyncCheckState.Checking -> "正在读取 IPv6 地址…"
                                AsyncCheckState.Ready -> lanIpv6ApiUrl ?: "当前网络未分配可用的 IPv6 地址"
                                AsyncCheckState.Failed -> "读取失败"
                            },
                            supportingText = if (lanIpv6ApiUrl != null) {
                                "双栈监听已开启；IPv6 地址已使用方括号格式并包含访问凭证。"
                            } else {
                                lanDiagnostic ?: "当前网络未分配可供其他设备访问的 IPv6 地址，IPv4 仍可正常使用。"
                            },
                            action = lanIpv6ApiUrl?.let { url ->
                                {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        DesktopActionButton(
                                            label = "打开",
                                            onClick = { launchDesktopAction("已打开 IPv6 API 地址") { openUrl(url) } },
                                            enabled = !actionBusy,
                                            style = DesktopActionButtonStyle.Tonal,
                                            icon = DesktopIcons.Link,
                                        )
                                        DesktopActionButton(
                                            label = "复制完整地址",
                                            onClick = { launchDesktopAction("已复制完整 IPv6 API 地址") { copyToClipboard(url) } },
                                            enabled = !actionBusy,
                                            style = DesktopActionButtonStyle.Outlined,
                                            icon = DesktopIcons.Copy,
                                        )
                                    }
                                }
                            },
                        )
                    }
                    DesktopDivider()
                    val firewallValue = when (firewallState) {
                        FirewallCheckState.NotChecked -> "尚未检查"
                        FirewallCheckState.Checking -> "检查中…"
                        FirewallCheckState.Allowed -> "已放行"
                        FirewallCheckState.NotAllowed -> "尚未放行"
                        FirewallCheckState.Failed -> "检查失败"
                    }
                    DesktopInfoRow(
                        label = "防火墙",
                        value = firewallValue,
                        supportingText = firewallMessage
                            ?: "监听 ${configuredRuntime.listenHost} 时，需允许 node.exe 入站。",
                        action = {
                            val checkOrAdd = firewallState == FirewallCheckState.NotAllowed
                            DesktopActionButton(
                                label = when {
                                    firewallState == FirewallCheckState.Checking -> "处理中…"
                                    checkOrAdd -> "添加放行"
                                    else -> "检查防火墙"
                                },
                                onClick = {
                                    if (firewallState == FirewallCheckState.Checking) return@DesktopActionButton
                                    firewallState = FirewallCheckState.Checking
                                    firewallMessage = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            if (!nodeExe.isFile) {
                                                Result.failure<Unit>(
                                                    IllegalStateException("node.exe 不存在：${nodeExe.absolutePath}"),
                                                )
                                            } else {
                                                try {
                                                    if (checkOrAdd) {
                                                        val addError = FirewallManager.ensureInboundRule(
                                                            nodeExe.absolutePath,
                                                            "DanmuApi node.exe",
                                                        )
                                                        if (addError != null) {
                                                            Result.failure(IllegalStateException(addError))
                                                        } else if (!FirewallManager.hasInboundRule(nodeExe.absolutePath)) {
                                                            Result.failure(IllegalStateException("规则添加后仍未检测到 node.exe 入站放行"))
                                                        } else {
                                                            Result.success(Unit)
                                                        }
                                                    } else {
                                                        Result.success(
                                                            FirewallManager.hasInboundRule(nodeExe.absolutePath),
                                                        )
                                                    }
                                                } catch (error: Exception) {
                                                    Result.failure(error)
                                                }
                                            }
                                        }
                                        result.fold(
                                            onSuccess = { allowed ->
                                                if (allowed is Boolean) {
                                                    firewallState = if (allowed) {
                                                        FirewallCheckState.Allowed
                                                    } else {
                                                        FirewallCheckState.NotAllowed
                                                    }
                                                    firewallMessage = if (allowed) {
                                                        "已找到匹配的 node.exe 入站规则。"
                                                    } else {
                                                        "未找到匹配规则；再次点击可发起管理员授权添加。"
                                                    }
                                                } else {
                                                    firewallState = FirewallCheckState.Allowed
                                                    firewallMessage = "规则已添加并完成复核。"
                                                }
                                            },
                                            onFailure = {
                                                firewallState = FirewallCheckState.Failed
                                                firewallMessage = "防火墙操作失败：${diagnostic(it)}"
                                            },
                                        )
                                    }
                                },
                                enabled = firewallState != FirewallCheckState.Checking,
                                style = if (checkOrAdd) {
                                    DesktopActionButtonStyle.Tonal
                                } else {
                                    DesktopActionButtonStyle.Outlined
                                },
                                icon = DesktopIcons.Tools,
                            )
                        },
                    )
                } else {
                    DesktopInfoRow(
                        label = "局域网 API",
                        value = "未开放",
                        supportingText = "监听 host 为 ${configuredRuntime.listenHost}，局域网地址不会作为可访问入口展示。",
                    )
                }
                DesktopDivider()
                DesktopInfoRow(
                    label = "监听 host",
                    value = configuredRuntime.listenHost,
                    monospace = true,
                    supportingText = "端口 ${configuredRuntime.port}（来自当前运行配置）",
                )
                DesktopDivider()
                DesktopInfoRow(
                    label = "凭证说明",
                    value = "API 地址已包含访问凭证",
                    supportingText = "请复制完整 API 地址，不要单独传播 Token。默认凭证也不会单独展示。",
                )
            }

            DesktopSectionCard(
                title = "运行环境",
                supportingText = "核心与运行目录信息来自当前运行实例。",
                modifier = Modifier.weight(1f),
            ) {
                DesktopInfoRow(
                    label = "核心变体",
                    value = healthValue(healthSnapshot?.variantLabel, state.phase, healthState),
                    supportingText = "配置值：${configuredRuntime.variant}",
                )
                DesktopDivider()
                DesktopInfoRow(
                    label = "核心来源",
                    value = "${DesktopCoreInstaller.STABLE_REPO} · 在线安装",
                    supportingText = "核心不随桌面应用内置。",
                )
                DesktopDivider()
                DesktopInfoRow(
                    label = "Node",
                    value = healthValue(healthSnapshot?.node, state.phase, healthState),
                    monospace = true,
                )
                DesktopDivider()
                DesktopInfoRow(
                    label = "运行身份",
                    value = healthValue(healthSnapshot?.runtimeIdentity, state.phase, healthState),
                    monospace = true,
                )
                DesktopDivider()
                DesktopInfoRow(
                    label = "工作目录",
                    value = healthValue(healthSnapshot?.resolvedHome, state.phase, healthState),
                    monospace = true,
                )
                DesktopDivider()
                DesktopInfoRow(
                    label = "运行目录",
                    value = paths.root.absolutePath,
                    monospace = true,
                    action = {
                        DesktopActionButton(
                            label = "打开 Explorer",
                            onClick = {
                                launchDesktopAction("已打开运行目录") { openInExplorer(paths.root) }
                            },
                            enabled = !actionBusy,
                            style = DesktopActionButtonStyle.Outlined,
                            icon = DesktopIcons.Folder,
                        )
                    },
                )
            }
        }

        DesktopSectionCard(
            title = "活动摘要",
            supportingText = "显示核心 /__health 暴露的最近请求信息。",
            trailingContent = {
                DesktopStatusBadge(status = healthStatus, label = healthStatusLabel(healthStatus), compact = true)
            },
        ) {
            if (healthSnapshot == null) {
                DesktopEmptyState(
                    title = when {
                        state.phase != ServicePhase.Running -> "服务尚未运行"
                        healthState is HealthReadState.Loading -> "正在读取活动数据"
                        else -> "暂无可用活动数据"
                    },
                    description = healthFailure ?: "服务启动后将从 /__health 读取活动摘要。",
                    icon = if (healthFailure == null) DesktopIcons.Empty else DesktopIcons.Error,
                    action = if (state.phase != ServicePhase.Running && state.canStart) {
                        {
                            DesktopActionButton(
                                label = "启动服务",
                                onClick = { controller.start() },
                                enabled = !state.isBusy,
                                icon = DesktopIcons.Start,
                            )
                        }
                    } else {
                        null
                    },
                )
            } else {
                val hasActivity = (healthSnapshot.requestCount ?: 0L) > 0L ||
                    !healthSnapshot.lastRequestPath.isNullOrBlank()
                if (!hasActivity) {
                    DesktopEmptyState(
                        title = "暂无业务请求",
                        description = "健康检查已连接，等待本机或局域网客户端访问。",
                        icon = DesktopIcons.Activity,
                    )
                } else {
                    DesktopInfoRow(
                        label = "请求总数",
                        value = healthValue(healthSnapshot.requestCount, state.phase, healthState),
                    )
                    DesktopDivider()
                    DesktopInfoRow(
                        label = "最近路径",
                        value = healthValue(healthSnapshot.lastRequestPath, state.phase, healthState),
                        monospace = true,
                    )
                    DesktopDivider()
                    DesktopInfoRow(
                        label = "客户端地址",
                        value = healthValue(healthSnapshot.lastClientIp, state.phase, healthState),
                        monospace = true,
                    )
                    DesktopDivider()
                    DesktopInfoRow(
                        label = "最近请求",
                        value = healthValue(
                            healthSnapshot.lastRequestAt,
                            state.phase,
                            healthState,
                            ::formatTimestamp,
                        ),
                    )
                }
            }
        }

        if (!actionNotice.isNullOrBlank()) {
            Text(
                text = actionNotice ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = if (actionNotice?.startsWith("操作失败") == true) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

private enum class AsyncCheckState {
    NotChecked,
    Checking,
    Ready,
    Failed,
}

private fun isWildcardHostValue(host: String): Boolean = host == "0.0.0.0" || host == "::"

private fun heroTitle(phase: ServicePhase): String = when (phase) {
    ServicePhase.Stopped -> "服务已停止"
    ServicePhase.Preparing -> "正在准备运行时"
    ServicePhase.Starting -> "正在启动服务"
    ServicePhase.Running -> "服务运行中"
    ServicePhase.Stopping -> "正在停止服务"
    ServicePhase.CoreSetupRequired -> "等待准备核心"
    ServicePhase.Failed -> "启动失败"
}

private fun healthStatus(phase: ServicePhase, healthState: HealthReadState): DesktopStatus = when {
    phase != ServicePhase.Running -> phase.toDesktopStatus()
    healthState is HealthReadState.Loading -> DesktopStatus.Loading
    healthState is HealthReadState.Unavailable || healthState is HealthReadState.Failed -> DesktopStatus.Error
    healthState is HealthReadState.Ready -> DesktopStatus.Success
    else -> DesktopStatus.Neutral
}

private fun healthStatusLabel(status: DesktopStatus): String = when (status) {
    DesktopStatus.Success -> "健康"
    DesktopStatus.Loading -> "读取中"
    DesktopStatus.Error -> "读取失败"
    DesktopStatus.Neutral -> "尚未启动"
    DesktopStatus.Info -> "信息"
    DesktopStatus.Warning -> "注意"
}

private fun healthFailureMessage(state: HealthReadState): String? = when (state) {
    is HealthReadState.Unavailable -> state.reason
    is HealthReadState.Failed -> "读取 /__health 失败：${diagnostic(state.cause)}"
    else -> null
}

private fun <T> healthValue(
    value: T?,
    phase: ServicePhase,
    healthState: HealthReadState,
    format: (T) -> String = { it.toString() },
): String = when {
    phase != ServicePhase.Running -> "尚未启动"
    healthState is HealthReadState.Loading -> "读取中…"
    healthState is HealthReadState.Unavailable || healthState is HealthReadState.Failed -> "读取失败"
    value == null -> "不可用"
    else -> format(value)
}

private fun formatUptime(seconds: Long): String {
    if (seconds < 0) return "不可用"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return if (days > 0) {
        "${days}天 ${hours}小时"
    } else {
        "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    }
}

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0) return "暂无"
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
}

internal data class DesktopLanAddresses(
    val ipv4: String? = null,
    val ipv6: String? = null,
)

internal fun discoverLanAddresses(): Result<DesktopLanAddresses> {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
            ?: return Result.success(DesktopLanAddresses())
        var ipv4: String? = null
        var ipv6: String? = null
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                when {
                    ipv4 == null && address is java.net.Inet4Address &&
                        !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress && address.hostAddress != "0.0.0.0" -> {
                        ipv4 = address.hostAddress
                    }
                    ipv6 == null && address is java.net.Inet6Address &&
                        !address.isAnyLocalAddress && !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress && !address.isMulticastAddress -> {
                        ipv6 = address.hostAddress?.substringBefore('%')
                    }
                }
            }
        }
        Result.success(DesktopLanAddresses(ipv4 = ipv4, ipv6 = ipv6))
    } catch (error: Exception) {
        Result.failure(error)
    }
}

@Composable
private fun DesktopApiEndpointRow(
    label: String,
    value: String,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = DesktopTokens.RowVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (action != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                action()
            }
        }
    }
}

private fun copyToClipboard(text: String): Result<Unit> {
    return try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private fun openUrl(url: String): Result<Unit> {
    return try {
        if (!Desktop.isDesktopSupported()) {
            Result.failure(IllegalStateException("当前系统不支持打开浏览器"))
        } else {
            Desktop.getDesktop().browse(URI.create(url))
            Result.success(Unit)
        }
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private fun openInExplorer(dir: File): Result<Unit> {
    return try {
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.failure(IllegalStateException("无法创建运行目录：${dir.absolutePath}"))
        }
        if (!dir.isDirectory) {
            return Result.failure(IllegalStateException("运行目录不是文件夹：${dir.absolutePath}"))
        }
        if (!Desktop.isDesktopSupported()) {
            Result.failure(IllegalStateException("当前系统不支持打开 Explorer"))
        } else {
            Desktop.getDesktop().open(dir)
            Result.success(Unit)
        }
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private fun diagnostic(error: Throwable): String =
    error.message?.trim()?.takeIf { it.isNotEmpty() } ?: error::class.java.simpleName
