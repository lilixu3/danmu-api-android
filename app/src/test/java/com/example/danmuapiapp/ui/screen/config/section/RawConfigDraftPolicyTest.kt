package com.example.danmuapiapp.ui.screen.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawConfigDraftPolicyTest {

    @Test
    fun `draft syncs when user has no local changes`() {
        assertTrue(shouldSyncRawDraft(currentText = "A=1\n", lastSyncedRaw = "A=1\n"))
    }

    @Test
    fun `draft does not sync over local edits`() {
        assertFalse(shouldSyncRawDraft(currentText = "A=2\n", lastSyncedRaw = "A=1\n"))
    }
}
