package com.example.danmuapiapp.data.service

/**
 * 补齐 onCreate -> onTaskRemoved、尚未收到 onStartCommand 的恢复入口。
 *
 * 仅由 Service 主线程调用，依据的是命令是否投递和用户意图，不是进程/端口健康情况。
 * 正常收到启动命令的 Service 不做任何恢复；每个新 Service 最多补交一次，失败不循环重试。
 */
internal class NodeTaskRemovalStartRecovery {
    enum class Result {
        Skipped,
        ForegroundUnavailable,
        StartRequested
    }

    private var startCommandReceived = false
    private var recoveryAttempted = false

    fun onStartCommandReceived() {
        // 包括 STOP、null intent 和通知辅助命令，不能覆盖框架已投递的命令语义。
        startCommandReceived = true
    }

    fun onTaskRemoved(
        normalMode: Boolean,
        desiredRunning: Boolean,
        stopRequested: Boolean,
        enterForeground: () -> Boolean,
        requestStart: (explicitStart: Boolean, notificationOnly: Boolean) -> Unit
    ): Result {
        if (startCommandReceived || recoveryAttempted || !normalMode || !desiredRunning || stopRequested) {
            return Result.Skipped
        }
        recoveryAttempted = true
        // 先建立前台服务，再请求 Node 启动；不绕过现有的前台权限/失败处理。
        if (!enterForeground()) return Result.ForegroundUnavailable
        // 自动恢复不修改 desiredRunning；Controller 处理时会再次尊重停止命令。
        // 必须是完整启动请求，不能使用只补通知的 notificationOnly=true。
        requestStart(false, false)
        return Result.StartRequested
    }
}
