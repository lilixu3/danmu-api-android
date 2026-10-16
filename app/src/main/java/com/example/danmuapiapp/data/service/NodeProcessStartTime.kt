package com.example.danmuapiapp.data.service

/** /proc/[pid]/stat 第 22 列是开机以来的启动时刻，不是进程已经运行的时长。 */
internal fun parseNodeProcessStartedElapsedMs(stat: String, clockTicksPerSecond: Long): Long? {
    if (clockTicksPerSecond <= 0L || ')' !in stat) return null
    val fields = stat.substringAfterLast(')').trim().split(Regex("\\s+"))
    val ticks = fields.getOrNull(19)?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
    // 先做整除，避免长期运行设备上 ticks * 1000 溢出。
    val seconds = ticks / clockTicksPerSecond
    if (seconds > Long.MAX_VALUE / 1000L) return null
    return seconds * 1000L + (ticks % clockTicksPerSecond) * 1000L / clockTicksPerSecond
}

internal fun hasNodeProcessExceededMinUptime(
    nowElapsedMs: Long,
    startedElapsedMs: Long,
    minUptimeMs: Long
): Boolean = nowElapsedMs >= startedElapsedMs &&
    nowElapsedMs - startedElapsedMs >= minUptimeMs

/** A previous process's exit marker must not identify a reused PID as stopped. */
internal fun isStoppedNodeProcessIdentity(marker: String, pid: Int, startedElapsedMs: Long): Boolean {
    val fields = marker.trim().split(' ')
    return pid > 0 && startedElapsedMs >= 0L && fields.size == 2 &&
        fields[0].toIntOrNull() == pid && fields[1].toLongOrNull() == startedElapsedMs
}
