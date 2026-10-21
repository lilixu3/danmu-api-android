package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalNotificationBehaviorTest {

    @Test
    fun `missing or unknown value defaults to foreground restore`() {
        assertEquals(
            NormalNotificationBehavior.ForegroundRestore,
            NormalNotificationBehavior.fromStorageValue(null)
        )
        assertEquals(
            NormalNotificationBehavior.ForegroundRestore,
            NormalNotificationBehavior.fromStorageValue("unknown")
        )
    }

    @Test
    fun `storage values round trip`() {
        NormalNotificationBehavior.entries.forEach { behavior ->
            assertEquals(
                behavior,
                NormalNotificationBehavior.fromStorageValue(behavior.storageValue)
            )
        }
    }
}
