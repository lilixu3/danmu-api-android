package com.example.danmuapiapp.data.repository

/** 只过滤旧广播，不探测网络、不推断服务是否可用，也不触发停止或重启。 */
internal class NormalRuntimeEventOrder {
    private data class Stamp(val processStartedAt: Long, val generation: Long, val sequence: Long)
    private var latest: Stamp? = null

    @Synchronized
    fun accept(processStartedAt: Long, generation: Long, sequence: Long): Boolean {
        // 兼容缺少代次元数据的旧调用方。
        if (processStartedAt < 0L || generation < 0L || sequence < 0L) return latest == null
        val next = Stamp(processStartedAt, generation, sequence)
        val previous = latest
        if (previous != null) {
            val comparison = compareValuesBy(
                next, previous, Stamp::processStartedAt, Stamp::generation, Stamp::sequence
            )
            if (comparison <= 0) return false
        }
        latest = next
        return true
    }
}
