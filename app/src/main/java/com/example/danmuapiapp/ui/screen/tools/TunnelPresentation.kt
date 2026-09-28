package com.example.danmuapiapp.ui.screen.tools

import com.example.danmuapiapp.data.tunnel.*

internal enum class TunnelTone { Quiet, Active, Pending, Error }

internal data class TunnelStatusPresentation(
    val title: String,
    val description: String,
    val badge: String,
    val tone: TunnelTone
)

internal fun tunnelStatus(state: TunnelUiState): TunnelStatusPresentation = when {
    state.running -> when (state.linkState) {
        TunnelLinkState.Connected -> TunnelStatusPresentation("隧道已连接",
            if (state.serviceRunning) "远程链路已建立，弹幕服务触手可及。" else "远程链路已建立，等待本机弹幕服务启动。",
            "已连接", TunnelTone.Active)
        TunnelLinkState.Connecting -> TunnelStatusPresentation("正在建立连接", "frpc 已启动，正在连接你的服务器。", "连接中", TunnelTone.Pending)
        TunnelLinkState.Error -> TunnelStatusPresentation("连接需要关注", "frpc 仍在运行，请查看日志确认链路状态。", "链路异常", TunnelTone.Error)
        TunnelLinkState.Unknown -> TunnelStatusPresentation("隧道运行中", "frpc 已启动，等待链路状态确认。", "待确认", TunnelTone.Pending)
    }
    state.state == "starting" -> TunnelStatusPresentation("正在启动隧道", "正在准备连接，请稍候。", "启动中", TunnelTone.Pending)
    state.state == "retrying" -> TunnelStatusPresentation("正在重新连接", "连接暂时中断，正在自动尝试恢复。", "重连中", TunnelTone.Pending)
    state.state == "error" -> TunnelStatusPresentation("连接未能建立", "检查服务器配置，或展开日志查看原因。", "启动失败", TunnelTone.Error)
    !tunnelConfigured(state.settings) -> TunnelStatusPresentation("让弹幕走得更远", "连接你的 frp 服务器，从外网访问本机弹幕服务。", "待配置", TunnelTone.Quiet)
    !state.settings.enabled -> TunnelStatusPresentation("隧道尚未启用", "配置已就绪，在连接设置中启用内网穿透。", "未启用", TunnelTone.Quiet)
    else -> TunnelStatusPresentation("准备好连接了", "启动隧道，将本机弹幕服务连接到公网。", "待连接", TunnelTone.Quiet)
}

internal fun tunnelConfigured(settings: TunnelSettings): Boolean = when (settings.mode) {
    TunnelMode.Form -> settings.form.serverAddr.isNotBlank()
    TunnelMode.Paste -> settings.configText.isNotBlank()
}

/** Keep editable strings intact (including incomplete ports) until the user saves. */
internal data class TunnelDraft(
    val enabled: Boolean = false,
    val autoStart: Boolean = false,
    val mode: TunnelMode = TunnelMode.Form,
    val proxyType: TunnelProxyType = TunnelProxyType.Tcp,
    val serverAddr: String = "",
    val serverPort: String = "7000",
    val authToken: String = "",
    val remotePort: String = "",
    val customDomains: String = "",
    val subdomain: String = "",
    val proxyName: String = "danmu-api",
    val tlsEnabled: Boolean = true,
    val dnsServer: String = DEFAULT_DNS_SERVER,
    val autoFill: Boolean = true,
    val publicAddress: String = "",
    val pasteText: String = ""
) {
    fun form() = TunnelFormSettings(
        serverAddr = serverAddr.trim(), serverPort = serverPort.trim().toIntOrNull() ?: 0,
        authToken = authToken.trim(), proxyType = proxyType,
        remotePort = remotePort.trim().toIntOrNull() ?: 0,
        customDomains = customDomains.trim(), subdomain = subdomain.trim(),
        proxyName = proxyName.trim().ifBlank { "danmu-api" }, tlsEnabled = tlsEnabled,
        dnsServer = dnsServer.trim(), autoFillDefaults = autoFill
    )

    fun effectiveConfig(port: Int): String {
        if (mode == TunnelMode.Form) return buildFrpcToml(form(), port)
        val text = if (autoFill) injectFrpcDefaults(
            pasteText, dnsServer.trim().ifBlank { DEFAULT_DNS_SERVER }
        ) else pasteText
        return syncPastedLocalPort(text, port)
    }

    fun check(port: Int): TunnelCheck = if (mode == TunnelMode.Form) {
        TunnelCheck(errors = validateForm(form(), port))
    } else checkPastedConfig(effectiveConfig(port), port)

    fun settings() = TunnelSettings(
        enabled = enabled, autoStart = autoStart, mode = mode,
        publicAddress = publicAddress.trim(), form = form(),
        configText = pasteText, riskAcknowledged = true
    )

    companion object {
        fun from(settings: TunnelSettings) = with(settings.form) {
            TunnelDraft(
                enabled = settings.enabled, autoStart = settings.autoStart, mode = settings.mode,
                proxyType = proxyType, serverAddr = serverAddr,
                serverPort = serverPort.takeIf { it > 0 }?.toString().orEmpty(), authToken = authToken,
                remotePort = remotePort.takeIf { it > 0 }?.toString().orEmpty(),
                customDomains = customDomains, subdomain = subdomain, proxyName = proxyName,
                tlsEnabled = tlsEnabled, dnsServer = dnsServer, autoFill = autoFillDefaults,
                publicAddress = settings.publicAddress, pasteText = settings.configText
            )
        }
    }
}
