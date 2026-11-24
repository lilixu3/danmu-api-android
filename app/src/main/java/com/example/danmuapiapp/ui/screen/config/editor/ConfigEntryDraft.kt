package com.example.danmuapiapp.ui.screen.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/** The open entry form can supply its complete save value, or null while input is incomplete. */
internal class ConfigEntryDraft {
    private var active by mutableStateOf<State<String?>?>(null)

    fun valueForSave(base: String): String? {
        val entry = active ?: return base
        return entry.value
    }

    @Composable
    fun IncludeOnSave(value: String?) {
        val latest = rememberUpdatedState(value)
        DisposableEffect(this) {
            active = latest
            onDispose { active = null }
        }
    }
}
