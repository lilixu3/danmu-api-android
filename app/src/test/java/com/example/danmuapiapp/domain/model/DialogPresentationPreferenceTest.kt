package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogPresentationPreferenceTest {
    @Test
    fun defaultDialogPresentationUsesPopupDialog() {
        assertEquals(
            DialogPresentationPreference.Popup,
            DialogPresentationPreference.fromStorageValue(null)
        )
        assertEquals(
            DialogPresentationPreference.Popup,
            DialogPresentationPreference.fromStorageValue("")
        )
    }

    @Test
    fun storageValuesRoundTripToDialogPresentation() {
        assertEquals(
            DialogPresentationPreference.Popup,
            DialogPresentationPreference.fromStorageValue("popup")
        )
        assertEquals(
            DialogPresentationPreference.BottomSheet,
            DialogPresentationPreference.fromStorageValue("bottom_sheet")
        )
    }

    @Test
    fun unknownDialogPresentationFallsBackToPopupDialog() {
        assertEquals(
            DialogPresentationPreference.Popup,
            DialogPresentationPreference.fromStorageValue("legacy")
        )
    }
}
