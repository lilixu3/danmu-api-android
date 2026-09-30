package com.example.danmuapiapp.data.tunnel

/** 中断表示正常取消，包括 waitFor 和重连退避中的 sleep。 */
internal inline fun runInterruptibleTunnelTask(block: () -> Unit) {
    try {
        block()
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
