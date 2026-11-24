package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteRequestPageTest {
    @Test
    fun coreTotalIsIndependentOfReturnedRowsAndOnlyMeasuredDurationsAreReported() {
        val page = requireNotNull(parseRemoteRequestPage("""
            {"todayReqNum":530,"records":[{"requestId":"a"},{"durationMs":0},{"totalMs":42},{"elapsedMs":-1}]}
        """.trimIndent()))
        assertEquals(530, page.todayCount)
        assertEquals(listOf(null, 0L, 42L, null), page.records.map { it.durationMs })
    }

    @Test
    fun emptyListCanCarryZeroAndLegacyListCanOmitTotal() {
        assertEquals(0, parseRemoteRequestPage("""{"records":[],"todayReqNum":0}""")?.todayCount)
        assertNull(parseRemoteRequestPage("""{"records":[]}""")?.todayCount)
        assertNull(parseRemoteRequestPage("""{"success":false}"""))
    }
}
