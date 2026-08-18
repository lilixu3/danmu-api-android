package com.example.danmuapiapp.data.service

object CoreUpdateCheckPolicy {
    const val DEFAULT_INTERVAL_MINUTES = 10
    val intervalOptionsMinutes: List<Int> = listOf(5, 10, 30, 60)

    fun normalizeIntervalMinutes(minutes: Int): Int =
        minutes.takeIf { it in intervalOptionsMinutes } ?: DEFAULT_INTERVAL_MINUTES

    fun shouldCheck(
        nowEpochMillis: Long,
        lastCheckEpochMillis: Long,
        intervalMinutes: Int
    ): Boolean {
        if (lastCheckEpochMillis <= 0L || nowEpochMillis < lastCheckEpochMillis) return true
        val intervalMillis = normalizeIntervalMinutes(intervalMinutes) * 60_000L
        return nowEpochMillis - lastCheckEpochMillis >= intervalMillis
    }
}
