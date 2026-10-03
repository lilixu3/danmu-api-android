package com.example.danmuapiapp.data.util

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AppDpiIsolationTest {
    @Test fun `compat changes and resets never overwrite the normal DPI`() {
        val prefs = prefs()
        AppAppearancePrefs.writeAppDpiOverride(prefs, 420)
        AppAppearancePrefs.writeAppDpiOverride(prefs, 280, compat = true)
        assertEquals(420, AppAppearancePrefs.readAppDpiOverride(prefs))
        assertEquals(280, AppAppearancePrefs.readAppDpiOverride(prefs, compat = true))
        AppAppearancePrefs.writeAppDpiOverride(prefs, -1, compat = true)
        assertEquals(420, AppAppearancePrefs.readAppDpiOverride(prefs))
        assertEquals(-1, AppAppearancePrefs.readAppDpiOverride(prefs, compat = true))
    }
    @Test fun `normal reset does not alter an independently saved compat DPI`() {
        val prefs = prefs()
        AppAppearancePrefs.writeAppDpiOverride(prefs, 420)
        AppAppearancePrefs.writeAppDpiOverride(prefs, 280, compat = true)
        AppAppearancePrefs.writeAppDpiOverride(prefs, -1)
        assertEquals(-1, AppAppearancePrefs.readAppDpiOverride(prefs))
        assertEquals(280, AppAppearancePrefs.readAppDpiOverride(prefs, compat = true))
    }
    @Test fun `legacy DPI belongs to normal interface and compat initially follows system`() {
        val prefs = prefs()
        prefs.edit().putInt("app_dpi_override", 360).commit()
        assertEquals(360, AppAppearancePrefs.readAppDpiOverride(prefs))
        assertEquals(-1, AppAppearancePrefs.readAppDpiOverride(prefs, compat = true))
    }
    @Test fun `stale state cannot suppress restoring actual system density`() {
        assertTrue(AppAppearancePrefs.shouldRecreateForDpi(-1, -1, 280, 420))
        assertFalse(AppAppearancePrefs.shouldRecreateForDpi(-1, -1, 420, 420))
        assertTrue(AppAppearancePrefs.shouldRecreateForDpi(280, 420, 280, 420))
        assertTrue(AppAppearancePrefs.shouldRecreateForDpi(420, 420, 280, 420))
    }

    private fun prefs(): SharedPreferences {
        val data = mutableMapOf<String, Int>()
        val pending = mutableMapOf<String, Int>()
        val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt" -> { pending[args!![0] as String] = args[1] as Int; proxy }
                "apply" -> { data.putAll(pending); pending.clear(); null }
                "commit" -> { data.putAll(pending); pending.clear(); true }
                else -> error("Unexpected editor method ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getInt" -> data[args!![0] as String] ?: args[1]
                "getAll" -> data.toMap()
                "contains" -> data.containsKey(args!![0] as String)
                "edit" -> editor
                else -> error("Unexpected prefs method ${method.name}")
            }
        } as SharedPreferences
    }
}
