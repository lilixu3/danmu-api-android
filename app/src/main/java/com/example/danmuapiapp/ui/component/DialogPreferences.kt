package com.example.danmuapiapp.ui.component

import androidx.compose.runtime.compositionLocalOf
import com.example.danmuapiapp.domain.model.DialogPresentationPreference

data class DialogPreferences(
    val presentation: DialogPresentationPreference = DialogPresentationPreference.Popup,
    val bottomSheetGesturesEnabled: Boolean = true
)

val LocalDialogPreferences = compositionLocalOf { DialogPreferences() }
