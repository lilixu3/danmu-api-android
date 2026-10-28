package com.example.danmuapiapp.ui.screen.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.NormalAutoStartPrefs
import com.example.danmuapiapp.data.service.NormalNotificationBehaviorPrefs
import com.example.danmuapiapp.data.service.RootRuntimeNotificationManager
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.SystemHeartbeatScheduler
import com.example.danmuapiapp.data.tunnel.TunnelRepository
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页「服务与运行模式」子控制器：监听配置、运行模式、开机自启、保活心跳与通知行为。
 *
 * 所有持久化仍走原有 Preferences/Repository，行为与拆分前一致；跨模块刷新由回调注入。
 */
internal class SettingsServiceControlController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val runtimeRepo: RuntimeRepository,
    private val coreRepo: CoreRepository,
    private val settingsRepo: SettingsRepository,
    private val tunnelRepository: TunnelRepository,
    private val refreshRuntimeRelatedStates: () -> Unit,
    private val refreshWorkDirInfo: () -> Unit,
    private val postMessage: (String) -> Unit
) {
    var normalBootAutoStartEnabled by mutableStateOf(
        NormalAutoStartPrefs.isBootAutoStartEnabled(context)
    )
        private set
    var isRunModeSwitching by mutableStateOf(false)
        private set
    var a11yEnabled by mutableStateOf(NodeKeepAlivePrefs.isAccessibilityServiceEnabled(context))
        private set

    fun saveServiceConfig(
        port: Int,
        token: String,
        listenMode: RuntimeListenMode
    ) {
        val normalizedToken = RuntimeTokenNormalizer.normalizeInput(token)
        val old = runtimeRepo.runtimeState.value
        if (old.runMode == RunMode.Normal && port in 1..1023) {
            postMessage("普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口")
            return
        }

        val changed = old.port != port ||
            old.token != normalizedToken ||
            old.listenMode != listenMode
        if (!changed) {
            postMessage("配置未变化")
            return
        }

        runtimeRepo.applyServiceConfig(
            port = port,
            token = normalizedToken,
            restartIfRunning = true,
            listenMode = listenMode
        )
        // 端口变化后让内网穿透跟随（重写 frpc 配置的 localPort，运行中则重启穿透）
        if (old.port != port) {
            scope.launch { tunnelRepository.syncServicePort(port) }
        }
        postMessage(
            if (old.status == ServiceStatus.Running || old.status == ServiceStatus.Starting) {
                "配置已保存，服务正在应用新的监听设置"
            } else {
                "配置已保存"
            }
        )
    }

    fun restartService() = runtimeRepo.restartService()

    fun updateVariant(variant: ApiVariant) {
        if (!coreRepo.isCoreInstalled(variant)) {
            postMessage("${variant.label}尚未安装，请先到核心页安装或配置仓库")
            return
        }
        runtimeRepo.updateVariant(variant)
        if (runtimeRepo.runtimeState.value.status == ServiceStatus.Running) {
            runtimeRepo.restartService()
        }
    }

    fun updateRunMode(mode: RunMode) {
        if (isRunModeSwitching) return
        if (runtimeRepo.runtimeState.value.runMode == mode) return

        scope.launch {
            isRunModeSwitching = true
            try {
                if (mode.requiresRoot) {
                    val check = withContext(Dispatchers.IO) {
                        RootShell.exec("id", timeoutMs = 4000L)
                    }
                    if (!check.ok) {
                        postMessage(buildRootSwitchDeniedMessage(check))
                        return@launch
                    }
                }

                runtimeRepo.updateRunMode(mode)
                refreshRuntimeRelatedStates()
                refreshWorkDirInfo()
                SystemHeartbeatScheduler.refresh(context)
            } finally {
                isRunModeSwitching = false
            }
        }
    }

    fun setAutoStart(enabled: Boolean) = setNormalBootAutoStart(enabled)

    fun setNormalBootAutoStart(enabled: Boolean) {
        NormalAutoStartPrefs.setBootAutoStartEnabled(context, enabled)
        normalBootAutoStartEnabled = enabled
        postMessage(
            if (enabled) {
                "已开启普通模式开机自启"
            } else {
                "已关闭普通模式开机自启"
            }
        )
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        settingsRepo.setKeepAlive(enabled)
        if (!enabled) {
            NodeKeepAlivePrefs.requestDisableAccessibilityService(context)
        }
        SystemHeartbeatScheduler.refresh(context)
        postMessage(
            if (enabled) {
                "已开启无障碍保活，请在系统无障碍中启用服务"
            } else {
                "已关闭无障碍保活"
            }
        )
    }

    fun prepareAccessibilitySetup() {
        if (settingsRepo.keepAlive.value) return
        settingsRepo.setKeepAlive(true)
        SystemHeartbeatScheduler.refresh(context)
    }

    fun setKeepAliveHeartbeatEnabled(enabled: Boolean) {
        settingsRepo.setKeepAliveHeartbeatEnabled(enabled)
        SystemHeartbeatScheduler.refresh(context)
        postMessage(
            if (enabled) {
                "已开启心跳兜底检查"
            } else {
                "已关闭心跳兜底检查"
            }
        )
    }

    fun setKeepAliveHeartbeatMode(mode: KeepAliveHeartbeatMode) {
        settingsRepo.setKeepAliveHeartbeatMode(mode)
        SystemHeartbeatScheduler.refresh(context)
        postMessage(
            when (mode) {
                KeepAliveHeartbeatMode.Accessibility -> "已切换为无障碍心跳"
                KeepAliveHeartbeatMode.System -> "已切换为系统定时心跳（实验）"
            }
        )
    }

    fun setKeepAliveHeartbeatIntervalMinutes(minutes: Int) {
        val normalized = NodeKeepAlivePrefs.normalizeHeartbeatIntervalMinutes(minutes)
        settingsRepo.setKeepAliveHeartbeatIntervalMinutes(normalized)
        SystemHeartbeatScheduler.refresh(context)
        postMessage("心跳间隔已更新为 ${normalized} 分钟")
    }

    fun setCoreUpdateCheckIntervalMinutes(minutes: Int) {
        settingsRepo.setCoreUpdateCheckIntervalMinutes(minutes)
        postMessage("核心自动检查间隔已更新为 ${settingsRepo.coreUpdateCheckIntervalMinutes.value} 分钟")
    }

    fun setNormalModeStabilityMode(mode: NormalModeStabilityMode) {
        if (settingsRepo.normalModeStabilityMode.value == mode) {
            postMessage("普通模式稳定策略已是 ${mode.label}")
            return
        }
        settingsRepo.setNormalModeStabilityMode(mode)
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    NodeProjectManager.syncRuntimeEnvIfProjectReady(
                        context = context,
                        targetProjectDir = RuntimePaths.normalProjectDir(context),
                        preferredVariantKey = runtimeRepo.runtimeState.value.variant.key
                    )
                }
            }
            val state = runtimeRepo.runtimeState.value
            postMessage(
                when {
                    state.runMode != RunMode.Normal -> {
                        "普通模式稳定策略已改为 ${mode.label}，切回普通模式后生效"
                    }
                    state.status == ServiceStatus.Running -> {
                        runtimeRepo.addLog(LogLevel.Info, "普通模式稳定策略已切换为 ${mode.label}，正在重启服务应用新策略")
                        runtimeRepo.restartService()
                        "普通模式稳定策略已改为 ${mode.label}，服务正在重启"
                    }
                    else -> {
                        "普通模式稳定策略已改为 ${mode.label}，下次启动生效"
                    }
                }
            )
        }
    }

    fun setNormalNotificationBehavior(behavior: NormalNotificationBehavior) {
        if (settingsRepo.normalNotificationBehavior.value == behavior) {
            postMessage("普通模式通知行为已是 ${behavior.label}")
            return
        }
        settingsRepo.setNormalNotificationBehavior(behavior)
        if (behavior != NormalNotificationBehavior.RespectDismissal) {
            NormalNotificationBehaviorPrefs.clearManuallyHidden(context)
        }
        val state = runtimeRepo.runtimeState.value
        if (state.runMode == RunMode.Normal &&
            (state.status == ServiceStatus.Running || state.status == ServiceStatus.Starting)
        ) {
            NodeService.refreshForegroundNotification(context)
        }
        postMessage("普通模式通知行为已改为 ${behavior.label}")
    }

    fun setServiceNotificationInfoEnabled(enabled: Boolean) {
        if (settingsRepo.serviceNotificationInfoEnabled.value == enabled) {
            postMessage(if (enabled) "通知接口信息已开启" else "通知接口信息已关闭")
            return
        }
        settingsRepo.setServiceNotificationInfoEnabled(enabled)
        val state = runtimeRepo.runtimeState.value
        if (state.runMode == RunMode.Normal &&
            (state.status == ServiceStatus.Running || state.status == ServiceStatus.Starting)
        ) {
            NodeService.applyNotificationDisplayPreference(context)
        } else if (state.runMode == RunMode.Root && state.status == ServiceStatus.Running) {
            RootRuntimeNotificationManager.show(
                context = context,
                text = state.statusMessage.orEmpty(),
                endpoint = state.lanUrl
            )
        }
        postMessage(
            if (enabled) {
                "已开启通知接口信息，服务通知将显示接口地址"
            } else {
                "已关闭通知接口信息，服务通知仅显示运行状态"
            }
        )
    }

    fun setRootNotificationEnabled(enabled: Boolean) {
        if (settingsRepo.rootNotificationEnabled.value == enabled) {
            postMessage(if (enabled) "Root 模式通知已开启" else "Root 模式通知已关闭")
            return
        }
        settingsRepo.setRootNotificationEnabled(enabled)
        val state = runtimeRepo.runtimeState.value
        if (state.runMode == RunMode.Root && state.status == ServiceStatus.Running) {
            if (enabled) {
                RootRuntimeNotificationManager.show(
                    context = context,
                    text = state.statusMessage.orEmpty(),
                    endpoint = state.lanUrl
                )
            } else {
                RootRuntimeNotificationManager.cancel(context)
            }
        }
        postMessage(
            if (enabled) {
                "已开启 Root 模式通知，服务运行时显示常驻通知"
            } else {
                "已关闭 Root 模式通知"
            }
        )
    }

    fun hasPostNotificationPermission(): Boolean {
        return NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
    }

    fun refreshBootStates() {
        normalBootAutoStartEnabled = NormalAutoStartPrefs.isBootAutoStartEnabled(context)
        a11yEnabled = NodeKeepAlivePrefs.isAccessibilityServiceEnabled(context)
    }
}
