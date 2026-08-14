package com.example.danmuapiapp.ui.screen.records

import com.example.danmuapiapp.domain.model.RequestRecord
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

enum class RequestTimeRange { All, LastHour, Today }

data class RequestRecordFilter(
    val query: String = "",
    val success: Boolean? = null,
    val method: String? = null,
    val timeRange: RequestTimeRange = RequestTimeRange.All
)

data class RequestInsights(
    val total: Int = 0,
    val failureRatePercent: Int = 0,
    val p95DurationMs: Long = 0L,
    val uniqueClients: Int = 0,
    val slowCount: Int = 0,
    val topFailureEndpoint: String? = null,
    val topFailureCount: Int = 0
)

internal object RequestRecordInsights {
    fun filter(
        records: List<RequestRecord>,
        filter: RequestRecordFilter,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<RequestRecord> {
        val query = filter.query.trim().lowercase()
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        return records.filter { record ->
            val matchesStatus = filter.success == null || record.success == filter.success
            val matchesMethod = filter.method == null || record.method.equals(filter.method, true)
            val matchesTime = when (filter.timeRange) {
                RequestTimeRange.All -> true
                RequestTimeRange.LastHour -> record.timestamp >= nowMs - 60L * 60L * 1000L
                RequestTimeRange.Today ->
                    Instant.ofEpochMilli(record.timestamp).atZone(zoneId).toLocalDate() == today
            }
            val matchesQuery = query.isBlank() || searchableText(record).contains(query)
            matchesStatus && matchesMethod && matchesTime && matchesQuery
        }
    }

    fun summarize(records: List<RequestRecord>): RequestInsights {
        if (records.isEmpty()) return RequestInsights()
        val failures = records.filterNot { it.success }
        val durations = records.map { it.durationMs.coerceAtLeast(0L) }.sorted()
        val p95Index = (ceil(durations.size * 0.95).toInt() - 1).coerceIn(durations.indices)
        val failureGroups = failures.groupingBy { endpoint(it.url) }.eachCount()
        val topFailure = failureGroups.maxByOrNull { it.value }
        return RequestInsights(
            total = records.size,
            failureRatePercent = ((failures.size * 100.0) / records.size).toInt(),
            p95DurationMs = durations[p95Index],
            uniqueClients = records.mapNotNull(::clientIp).distinct().size,
            slowCount = records.count { it.durationMs >= 2_000L },
            topFailureEndpoint = topFailure?.key?.takeIf { it.isNotBlank() },
            topFailureCount = topFailure?.value ?: 0
        )
    }

    fun clientIp(record: RequestRecord): String? {
        val fromScene = record.scene.substringAfter('/', "").trim()
        if (fromScene.isNotBlank()) return fromScene
        return record.responseSnippet
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("客户端IP:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun endpoint(url: String): String {
        return url.substringBefore('?').trim().ifBlank { "未知接口" }
    }

    private fun searchableText(record: RequestRecord): String = buildString {
        append(record.scene)
        append(' ')
        append(record.method)
        append(' ')
        append(record.url)
        append(' ')
        append(record.statusCode ?: "")
        append(' ')
        append(record.errorMessage.orEmpty())
        append(' ')
        append(record.responseSnippet.orEmpty())
    }.lowercase()
}
