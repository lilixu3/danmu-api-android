package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeEnvDefaultsTest {

    @Test
    fun `log defaults should be generated only for missing keys`() {
        val defaults = NodeProjectManager.buildRuntimeEnvLogDefaults(
            mapOf(
                "DANMU_API_LOG_TO_FILE" to "1",
                "APP_LOG_TO_FILE" to "1"
            )
        )

        assertEquals("1048576", defaults["DANMU_API_LOG_MAX_BYTES"])
        assertEquals("1048576", defaults["APP_LOG_MAX_BYTES"])
        // Existing keys should not be forced back to 0.
        assertEquals(null, defaults["DANMU_API_LOG_TO_FILE"])
        assertEquals(null, defaults["APP_LOG_TO_FILE"])
    }
}
