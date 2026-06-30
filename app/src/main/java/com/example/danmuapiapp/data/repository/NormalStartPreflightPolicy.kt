package com.example.danmuapiapp.data.repository

internal enum class NormalStartPreflightDecision {
    Proceed,
    ForeignInstanceOccupiesPort
}

internal fun decideNormalStartPreflight(
    portOpen: Boolean,
    ownership: RuntimeOwnership
): NormalStartPreflightDecision {
    if (!portOpen) return NormalStartPreflightDecision.Proceed
    return if (ownership == RuntimeOwnership.OwnedExact || ownership == RuntimeOwnership.OwnedLegacy) {
        NormalStartPreflightDecision.Proceed
    } else {
        NormalStartPreflightDecision.ForeignInstanceOccupiesPort
    }
}

internal fun buildNormalForeignPortOccupiedMessage(port: Int): String {
    return "端口 $port 已有其他实例在运行，请先停止外部进程后再启动"
}
