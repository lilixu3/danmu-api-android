package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首页「运行时快捷操作」子控制器：端口/Token 快捷修改、服务启停与运行模式切换。
 *
 * 对外由 [HomeViewModel] 转发，行为与拆分前一致。
 */
internal class HomeRuntimeQuickActionsController(
    private val scope: CoroutineScope,
    private val runtimeRepo: RuntimeRepository,
    private val runtimeState: StateFlow<RuntimeState>,
    private val postMessage: (String) -> Unit,
    private val isCoreBusy: () -> Boolean,
    private val isCheckingCoreUpdate: () -> Boolean,
    private val onStartRequested: () -> Unit,
    private val onStopRequested: () -> Unit
) {
    fun applyPortQuick(port: Int) {
        val state = runtimeState.value
        if (isCoreBusy() || isCheckingCoreUpdate()) {
            postMessage("当前有运行任务，稍后再修改端口")
            return
        }
        if (state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping) {
            postMessage("服务切换中，请稍后再修改端口")
            return
        }
        if (port !in 1..65535) {
            postMessage("端口必须在 1-65535 之间")
            return
        }
        if (state.runMode == RunMode.Normal && port in 1..1023) {
            postMessage("普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口")
            return
        }
        if (state.port == port) {
            postMessage("端口未变化")
            return
        }

        runtimeRepo.applyServiceConfig(
            port = port,
            token = state.token,
            restartIfRunning = true
        )
        postMessage(
            if (state.status == ServiceStatus.Running) {
                "正在切换到新端口：$port"
            } else {
                "端口已更新为：$port"
            }
        )
    }

    fun applyTokenQuick(token: String) {
        val state = runtimeState.value
        if (isCoreBusy() || isCheckingCoreUpdate()) {
            postMessage("当前有运行任务，稍后再修改 Token")
            return
        }
        if (state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping) {
            postMessage("服务切换中，请稍后再修改 Token")
            return
        }

        val normalized = RuntimeTokenNormalizer.normalizeInput(token)
        if (state.token == normalized) {
            postMessage("Token 未变化")
            return
        }

        runtimeRepo.applyServiceConfig(
            port = state.port,
            token = normalized,
            restartIfRunning = false
        )
        postMessage(
            if (state.status == ServiceStatus.Running) {
                "Token 已热更新，新请求将按最新 Token 生效"
            } else {
                "Token 已更新"
            }
        )
    }

    fun toggleService() {
        if (isCoreBusy()) return
        when (runtimeState.value.status) {
            ServiceStatus.Running,
            ServiceStatus.Starting -> onStopRequested()
            ServiceStatus.Stopped, ServiceStatus.Error -> onStartRequested()
            else -> { }
        }
    }

    fun toggleRunModeQuick() {
        val current = runtimeState.value.runMode
        val target = when (current) {
            RunMode.Normal -> RunMode.Root
            RunMode.Root -> RunMode.Normal
        }
        switchRunModeQuick(target)
    }

    fun switchRunModeQuick(target: RunMode) {
        if (isCoreBusy() || isCheckingCoreUpdate()) return
        if (runtimeState.value.runMode == target) return

        scope.launch {
            if (target.requiresRoot) {
                val check = withContext(Dispatchers.IO) {
                    RootShell.exec("id", timeoutMs = 4000L)
                }
                if (!check.ok) {
                    postMessage(buildRootSwitchDeniedMessage(check))
                    return@launch
                }
            }

            runtimeRepo.updateRunMode(target)
            val switched = withTimeoutOrNull(12_000L) {
                runtimeState.first { it.runMode == target }
            } != null
            postMessage(
                if (switched) {
                    "已切换到${target.label}"
                } else {
                    "切换${target.label}失败，请检查日志后重试"
                }
            )
        }
    }
}
