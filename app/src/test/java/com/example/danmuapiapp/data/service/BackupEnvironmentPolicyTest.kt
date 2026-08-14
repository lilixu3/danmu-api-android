package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEnvironmentPolicyTest {
    @Test
    fun exportExcludesCredentials() {
        val values = BackupEnvironmentPolicy.exportValues(
            "TOKEN=runtime-secret\nADMIN_TOKEN=admin-secret\nAPI_KEY=api-secret\nDANMU_API_PORT=9321\n"
        )
        assertFalse(values.containsKey("TOKEN"))
        assertFalse(values.containsKey("ADMIN_TOKEN"))
        assertFalse(values.containsKey("API_KEY"))
        assertEquals("9321", values["DANMU_API_PORT"])
    }

    @Test
    fun mergeKeepsLocalSecretsAndComments() {
        val current = "# local\nTOKEN=keep-me\nDANMU_API_PORT=9321\n"
        val merged = BackupEnvironmentPolicy.merge(
            current,
            mapOf("DANMU_API_PORT" to "9527", "DANMU_API_HOST" to "::")
        )
        assertTrue(merged.contains("# local"))
        assertTrue(merged.contains("TOKEN=keep-me"))
        assertTrue(merged.contains("DANMU_API_PORT=9527"))
        assertTrue(merged.contains("DANMU_API_HOST=::"))
    }
}
