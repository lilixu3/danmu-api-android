package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode

object NormalModeStabilityPrefs {

    private const val PREFS_SETTINGS = "settings"
    private const val KEY_NORMAL_MODE_STABILITY_MODE = "normal_mode_stability_mode"

    fun get(context: Context): NormalModeStabilityMode {
        val raw = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(KEY_NORMAL_MODE_STABILITY_MODE, NormalModeStabilityMode.Auto.storageValue)
        return NormalModeStabilityMode.fromStorageValue(raw)
    }

    fun set(context: Context, mode: NormalModeStabilityMode) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit {
            putString(KEY_NORMAL_MODE_STABILITY_MODE, mode.storageValue)
        }
    }
}
