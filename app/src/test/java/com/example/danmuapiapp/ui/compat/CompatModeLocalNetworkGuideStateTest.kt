package com.example.danmuapiapp.ui.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatModeLocalNetworkGuideStateTest {

    @Test
    fun `local-only dismissal survives later compat UI state copies`() {
        val initial = CompatModeUiState()

        assertFalse(initial.localNetworkGuideDismissedThisLaunch)

        val dismissed = initial.dismissLocalNetworkGuideForThisLaunch()
        val afterConfigurationDrivenUiUpdate = dismissed.copy(isOperating = true)

        assertTrue(dismissed.localNetworkGuideDismissedThisLaunch)
        assertTrue(afterConfigurationDrivenUiUpdate.localNetworkGuideDismissedThisLaunch)
    }

    @Test
    fun `fresh compat ViewModel state shows the guide again on next launch`() {
        assertFalse(CompatModeUiState().localNetworkGuideDismissedThisLaunch)
    }
}
