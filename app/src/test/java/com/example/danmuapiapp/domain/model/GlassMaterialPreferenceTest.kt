package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassMaterialPreferenceTest {

    @Test
    fun defaultMaterial_isOff() {
        assertEquals(GlassMaterialPreference.Off, GlassMaterialPreference.Default)
    }

    @Test
    fun storageValues_roundTripForEveryMaterial() {
        GlassMaterialPreference.entries.forEach { material ->
            assertEquals(
                material,
                GlassMaterialPreference.fromStorageValue(material.storageValue)
            )
        }
    }

    @Test
    fun legacyEnabledValues_migrateToLiquidGlass() {
        assertEquals(GlassMaterialPreference.LiquidGlass, GlassMaterialPreference.fromStorageValue(0))
        assertEquals(GlassMaterialPreference.LiquidGlass, GlassMaterialPreference.fromStorageValue(1))
        assertEquals(GlassMaterialPreference.LiquidGlass, GlassMaterialPreference.fromStorageValue(2))
        assertEquals(GlassMaterialPreference.LiquidGlass, GlassMaterialPreference.fromStorageValue(3))
    }

    @Test
    fun unknownStorageValue_fallsBackToLiquidGlass() {
        assertEquals(
            GlassMaterialPreference.LiquidGlass,
            GlassMaterialPreference.fromStorageValue(Int.MAX_VALUE)
        )
    }
}
