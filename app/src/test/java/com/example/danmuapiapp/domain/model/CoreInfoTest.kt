package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreInfoTest {
    @Test
    fun sourceMismatchIsInstalledButNotReadyToSwitch() {
        val matched = CoreInfo(ApiVariant.Custom, "1.0.0", isInstalled = true)
        val mismatched = matched.copy(sourceMismatch = true)
        assertTrue(matched.isReady)
        assertFalse(mismatched.isReady)
    }
}
