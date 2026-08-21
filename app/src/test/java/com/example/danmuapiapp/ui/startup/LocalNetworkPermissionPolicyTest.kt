package com.example.danmuapiapp.ui.startup

import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPermissionPolicyTest {
    @Test
    fun `Android 16 does not require local network runtime permission`() {
        val state = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 36,
            granted = false,
            requestAttempted = false
        )

        assertFalse(state.required)
        assertTrue(state.ready)
        assertNull(
            LocalNetworkPermissionPolicy.resolveAction(
                state = state,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `Android 17 first install requests local network permission`() {
        val state = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = false
        )

        assertTrue(state.required)
        assertFalse(state.ready)
        assertEquals(
            LocalNetworkPermissionAction.Request,
            LocalNetworkPermissionPolicy.resolveAction(
                state = state,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `Android 17 denial with rationale can request again`() {
        val state = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = true
        )

        assertEquals(
            LocalNetworkPermissionAction.Request,
            LocalNetworkPermissionPolicy.resolveAction(
                state = state,
                hasActivity = true,
                shouldShowRationale = true
            )
        )
    }

    @Test
    fun `Android 17 permanent denial opens app settings`() {
        val state = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = true
        )

        assertEquals(
            LocalNetworkPermissionAction.Settings,
            LocalNetworkPermissionPolicy.resolveAction(
                state = state,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `same denied permission state follows the latest system rationale`() {
        val unchangedState = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = true
        )

        assertEquals(
            LocalNetworkPermissionAction.Request,
            LocalNetworkPermissionPolicy.resolveAction(
                state = unchangedState,
                hasActivity = true,
                shouldShowRationale = true
            )
        )
        assertEquals(
            LocalNetworkPermissionAction.Settings,
            LocalNetworkPermissionPolicy.resolveAction(
                state = unchangedState,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `Android 17 granted permission needs no action`() {
        val state = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = true,
            requestAttempted = true
        )

        assertTrue(state.ready)
        assertNull(
            LocalNetworkPermissionPolicy.resolveAction(
                state = state,
                hasActivity = true,
                shouldShowRationale = false
            )
        )
    }

    @Test
    fun `missing Android 17 local network permission keeps first install guide visible in Root mode`() {
        val localNetworkState = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = false
        )

        assertTrue(
            LocalNetworkPermissionPolicy.shouldShowSetupStep(
                runMode = RunMode.Root,
                notificationReady = true,
                batteryReady = true,
                localNetworkState = localNetworkState
            )
        )
    }

    @Test
    fun `ready permissions hide setup step in Root mode`() {
        val localNetworkState = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = true,
            requestAttempted = true
        )

        assertFalse(
            LocalNetworkPermissionPolicy.shouldShowSetupStep(
                runMode = RunMode.Root,
                notificationReady = true,
                batteryReady = true,
                localNetworkState = localNetworkState
            )
        )
    }

    @Test
    fun `Android 17 compat mode shows first entry guide until dismissed for this launch`() {
        val localNetworkState = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = false
        )

        assertTrue(
            LocalNetworkPermissionPolicy.shouldShowCompatGuide(
                state = localNetworkState,
                dismissedThisLaunch = false
            )
        )
        assertFalse(
            LocalNetworkPermissionPolicy.shouldShowCompatGuide(
                state = localNetworkState,
                dismissedThisLaunch = true
            )
        )
    }

    @Test
    fun `granted Android 17 compat mode skips local network guide`() {
        val localNetworkState = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = true,
            requestAttempted = true
        )

        assertFalse(
            LocalNetworkPermissionPolicy.shouldShowCompatGuide(
                state = localNetworkState,
                dismissedThisLaunch = false
            )
        )
    }

    @Test
    fun `address hint appears only when Android 17 local network permission is missing`() {
        val android16 = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 36,
            granted = false,
            requestAttempted = false
        )
        val android17Missing = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = false,
            requestAttempted = false
        )
        val android17Granted = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = 37,
            granted = true,
            requestAttempted = true
        )

        assertFalse(LocalNetworkPermissionPolicy.shouldShowAddressHint(android16))
        assertTrue(LocalNetworkPermissionPolicy.shouldShowAddressHint(android17Missing))
        assertFalse(LocalNetworkPermissionPolicy.shouldShowAddressHint(android17Granted))
    }
}
