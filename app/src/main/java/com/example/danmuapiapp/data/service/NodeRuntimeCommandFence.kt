package com.example.danmuapiapp.data.service

/** 命令顺序栅栏，不读取网络或运行状态。由控制器在同一个 stateLock 下访问。 */
internal class NodeRuntimeCommandFence {
    var stopEpoch: Long = 0L
        private set
    var commandVersion: Long = 0L
        private set

    fun recordStart(): Long {
        commandVersion++
        return stopEpoch
    }

    fun recordStop() {
        stopEpoch++
        commandVersion++
    }

    fun acceptsStart(epoch: Long): Boolean = stopEpoch == epoch
    fun acceptsStopCallback(version: Long): Boolean = commandVersion == version
}
