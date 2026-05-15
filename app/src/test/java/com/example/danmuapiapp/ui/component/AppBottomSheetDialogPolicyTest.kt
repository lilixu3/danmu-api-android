package com.example.danmuapiapp.ui.component

import com.example.danmuapiapp.domain.model.DialogPresentationPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBottomSheetDialogPolicyTest {
    @Test
    fun defaultPresentationUsesPopupDialog() {
        assertTrue(
            resolveDialogPresentation(null, null) == DialogPresentationPreference.Popup
        )
    }

    @Test
    fun explicitPresentationOverridesGlobalPreference() {
        assertTrue(
            resolveDialogPresentation(
                globalPreference = DialogPresentationPreference.Popup,
                presentation = DialogPresentationPreference.BottomSheet
            ) == DialogPresentationPreference.BottomSheet
        )
    }

    @Test
    fun bottomSheetGesturesAreEnabledByDefault() {
        assertTrue(resolveSheetGesturesEnabled(bottomSheetGesturesEnabled = null))
    }

    @Test
    fun explicitBottomSheetGestureOverrideWins() {
        assertTrue(resolveSheetGesturesEnabled(bottomSheetGesturesEnabled = true))
        assertFalse(resolveSheetGesturesEnabled(bottomSheetGesturesEnabled = false))
    }
}
