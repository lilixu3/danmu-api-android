package com.example.danmuapiapp.data.service

/** 已有生命周期/启动事件的显示快照；不探测网络，不负责停止或重启运行时。 */
internal enum class NodeRuntimePhase(val message: String) {
    Idle("正在同步服务状态…"),
    Preparing("正在启动服务…"),
    WaitingForPort("正在等待服务端口就绪…"),
    Ready("服务运行中"),
    Stopping("正在停止服务…")
}

internal fun nodeRuntimePhase(
    running: Boolean,
    stopping: Boolean,
    threadAlive: Boolean,
    startupStarted: Boolean,
    generation: Long,
    readyPublishedGeneration: Long
): NodeRuntimePhase = when {
    stopping -> NodeRuntimePhase.Stopping
    // 表示此代次曾经完成启动就绪发布，不表示重新检查了此刻的网络可用性。
    // 已接管的运行时没有本地 Thread 引用，同样以已有就绪事件为准。
    running && generation >= 0L && readyPublishedGeneration == generation -> NodeRuntimePhase.Ready
    threadAlive -> NodeRuntimePhase.WaitingForPort
    running || startupStarted -> NodeRuntimePhase.Preparing
    else -> NodeRuntimePhase.Idle
}
