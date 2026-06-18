package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreLocationPolicyTest {

    @Test
    fun `Normal mode core status should use normal directory`() {
        assertEquals(
            CorePresenceSource.NormalDir,
            corePresenceSourceFor(RunMode.Normal)
        )
    }

    @Test
    fun `Root mode core status should use root directory`() {
        assertEquals(
            CorePresenceSource.RootDir,
            corePresenceSourceFor(RunMode.Root)
        )
    }
}
