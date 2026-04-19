package com.example.danmuapiapp.data.service

internal enum class NodeRuntimeExitAction {
    DeferToStopController,
    ReportStopped,
    ReportError
}

internal fun decideNodeRuntimeExitAction(
    stopping: Boolean,
    exitCode: Int,
    crashThrowable: Throwable?
): NodeRuntimeExitAction {
    if (stopping) return NodeRuntimeExitAction.DeferToStopController
    if (crashThrowable != null || (exitCode != 0 && exitCode != 130 && exitCode != 143)) {
        return NodeRuntimeExitAction.ReportError
    }
    return NodeRuntimeExitAction.ReportStopped
}
