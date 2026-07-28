package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.RuntimeDependencyResumeAction
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.RuntimeRepository

internal enum class RuntimeDependencyContinuation {
    None,
    KeepRunning,
    Start,
    Restart
}

internal fun resolveRuntimeDependencyContinuation(
    resumeAction: RuntimeDependencyResumeAction,
    serviceStatus: ServiceStatus
): RuntimeDependencyContinuation {
    return when (resumeAction) {
        RuntimeDependencyResumeAction.None -> RuntimeDependencyContinuation.None
        RuntimeDependencyResumeAction.Start -> {
            if (serviceStatus == ServiceStatus.Running) {
                RuntimeDependencyContinuation.KeepRunning
            } else {
                RuntimeDependencyContinuation.Start
            }
        }
        RuntimeDependencyResumeAction.Restart -> {
            if (serviceStatus == ServiceStatus.Running) {
                RuntimeDependencyContinuation.Restart
            } else {
                RuntimeDependencyContinuation.Start
            }
        }
    }
}

fun RuntimeRepository.continueAfterDependencyRepair(
    request: CoreDependencyRepairRequest
): String? {
    return when (
        resolveRuntimeDependencyContinuation(
            resumeAction = request.resumeAction,
            serviceStatus = runtimeState.value.status
        )
    ) {
        RuntimeDependencyContinuation.None -> null
        RuntimeDependencyContinuation.KeepRunning -> "服务保持运行"
        RuntimeDependencyContinuation.Start -> {
            startService()
            "服务正在启动"
        }
        RuntimeDependencyContinuation.Restart -> {
            restartService()
            "服务正在重启"
        }
    }
}
