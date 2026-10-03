package com.example.danmuapiapp.ui.startup

import org.junit.Assert.*
import org.junit.Test

class StartupPermissionSupportTest {
    @Test fun unsupportedAndSkippedStepsCompleteWithoutARealGrant() {
        assertTrue(startupPermissionStepReady(true, false, false, false))
        assertTrue(startupPermissionStepReady(true, true, false, true))
        assertTrue(startupPermissionStepReady(false, false, false, false))
        assertFalse(startupPermissionStepReady(true, true, false, false))
        assertTrue(startupPermissionStepReady(true, true, true, false))
    }

    @Test fun missingPermissionOrAllConfirmedMissingEntriesAreUnsupported() {
        assertFalse(startupRuntimePermissionSupported(false, true, true))
        assertFalse(startupRuntimePermissionSupported(true, false, false))
    }

    @Test fun queryErrorsAndOemSettingsFallbackNeverBecomeUnsupported() {
        assertTrue(startupRuntimePermissionSupported(null, false, false))
        assertTrue(startupRuntimePermissionSupported(true, null, false))
        assertTrue(startupRuntimePermissionSupported(true, false, null))
        assertTrue(startupRuntimePermissionSupported(true, false, true))
        assertTrue(startupRuntimePermissionSupported(true, true, false))
    }
    @Test fun genericTvAppInfoDoesNotProveBatteryControlSupport() {
        assertFalse(startupBatterySettingsSupported(true, false, true, false, true))
        assertFalse(startupBatterySettingsSupported(false, true, true, true, false))
        assertTrue(startupBatterySettingsSupported(true, true, false, false, true))
    }

    @Test fun oemFallbacksAndUnknownQueriesRemainAvailable() {
        assertTrue(startupBatterySettingsSupported(true, false, true, true, true))
        assertTrue(startupBatterySettingsSupported(true, false, true, false, false))
        assertTrue(startupBatterySettingsSupported(true, null, false, false, true))
        assertTrue(startupBatterySettingsSupported(true, false, true, null, true))
        assertTrue(startupBatterySettingsSupported(null, true, true, false, true))
    }

}
