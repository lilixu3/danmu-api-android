package com.example.danmuapiapp.ui.screen.records

import com.example.danmuapiapp.domain.model.RequestRecord
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestRecordInsightsTest {
    private val now = 1_700_000_000_000L

    @Test
    fun filterMatchesEndpointClientAndStatus() {
        val records = listOf(
            record(1, "/api/search?q=test", true, "192.168.1.2"),
            record(2, "/api/comment", false, "192.168.1.3")
        )
        val filtered = RequestRecordInsights.filter(
            records,
            RequestRecordFilter(query = "1.3", success = false),
            nowMs = now,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(listOf(2L), filtered.map { it.id })
    }

    @Test
    fun summarizeReportsP95ClientsAndTopFailure() {
        val records = listOf(
            record(1, "/api/a?x=1", false, "10.0.0.1", 100),
            record(2, "/api/a?x=2", false, "10.0.0.2", 3000),
            record(3, "/api/b", true, "10.0.0.1", 500)
        )
        val insight = RequestRecordInsights.summarize(records)
        assertEquals(66, insight.failureRatePercent)
        assertEquals(2, insight.uniqueClients)
        assertEquals(1, insight.slowCount)
        assertEquals(3000L, insight.p95DurationMs)
        assertEquals("/api/a", insight.topFailureEndpoint)
        assertEquals(2, insight.topFailureCount)
    }

    @Test
    fun summarizeDoesNotCountMaskedClientAddress() {
        val records = listOf(
            record(1, "/api/a", true, "***"),
            record(2, "/api/b", true, "2408:8215::1")
        )

        assertEquals(1, RequestRecordInsights.summarize(records).uniqueClients)
    }

    private fun record(
        id: Long,
        url: String,
        success: Boolean,
        ip: String,
        duration: Long = 100
    ) = RequestRecord(
        id = id,
        timestamp = now,
        scene = "外部调用/$ip",
        method = "GET",
        url = url,
        statusCode = if (success) 200 else 500,
        durationMs = duration,
        success = success
    )
}
