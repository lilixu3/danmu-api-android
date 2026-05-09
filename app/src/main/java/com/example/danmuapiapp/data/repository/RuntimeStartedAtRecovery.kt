package com.example.danmuapiapp.data.repository

internal fun calculateStartedAtFromUptime(nowMs: Long, uptimeSec: Long?): Long? {
    val seconds = uptimeSec ?: return null
    if (seconds < 0L || seconds > Long.MAX_VALUE / 1000L) return null

    val startedAt = nowMs - seconds * 1000L
    return startedAt.takeIf { it > 0L }
}

internal fun parseHealthUptimeSeconds(body: String): Long? {
    return Regex(""""uptimeSec"\s*:\s*(\d+)""")
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}
