package com.example.danmuapiapp.data.repository

/**
 * 普通模式启动/停止过程中的共享标记。
 *
 * 状态广播接收、生命周期控制和状态协调都会读写这几个值，单独收敛成一个
 * 线程安全的小对象，避免它们散落在 Repository 里被多方直接修改。
 */
internal class NormalRuntimeStartState {

    /** 当前是否处于"重启"流程：停止广播需要按重启语义解释。 */
    @Volatile
    var pendingRestart: Boolean = false

    /** 最近一次下发启动指令的时间，用于判定启动是否已经过期。 */
    @Volatile
    var startIssuedAtMs: Long = 0L

    /** 最近一次启动是否为用户显式触发，决定运行时长锚点是否强制刷新。 */
    @Volatile
    var pendingExplicitStart: Boolean = false

    fun clearStartMarkers() {
        startIssuedAtMs = 0L
        pendingExplicitStart = false
    }
}
