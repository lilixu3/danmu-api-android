package com.example.danmuapiapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the persisted light/dark preference as early as possible.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(MainActivity.PREFS_UI, MODE_PRIVATE)
        val dark = prefs.getBoolean(MainActivity.KEY_DARK_MODE, true)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
