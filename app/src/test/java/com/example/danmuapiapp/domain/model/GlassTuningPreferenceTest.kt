package com.example.danmuapiapp.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTuningPreferenceTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun jsonRoundTrip_preservesRoleOverrides() {
        val original = GlassTuningPreference(
            overrides = mapOf(
                GlassMaterialTarget.Dialog to GlassEffectOverridePreference(
                    blurRadius = 22f,
                    surfaceAlpha = 0.68f,
                    depthEffect = true
                ),
                GlassMaterialTarget.Selected to GlassEffectOverridePreference(
                    refractionAmount = 18f,
                    highlightAlpha = 0.24f
                )
            )
        )

        val restored = json.decodeFromString<GlassTuningPreference>(
            json.encodeToString(original)
        )

        assertEquals(original, restored)
    }

    @Test
    fun normalized_clampsValuesAndDropsInvalidNumbers() {
        val normalized = GlassTuningPreference(
            overrides = mapOf(
                GlassMaterialTarget.Card to GlassEffectOverridePreference(
                    blurRadius = 99f,
                    surfaceAlpha = -1f,
                    saturation = Float.NaN
                ),
                GlassMaterialTarget.Button to GlassEffectOverridePreference()
            )
        ).normalized()

        val card = normalized.overrides.getValue(GlassMaterialTarget.Card)
        assertEquals(40f, card.blurRadius)
        assertEquals(0f, card.surfaceAlpha)
        assertNull(card.saturation)
        assertFalse(GlassMaterialTarget.Button in normalized.overrides)
    }

    @Test
    fun presets_coverEveryGlassRoleAndSurvivePersistence() {
        assertEquals(GlassTuningPreset.Default, GlassTuningPreference().matchingPreset())

        GlassTuningPreset.entries.drop(1).forEach { preset ->
            assertEquals(GlassMaterialTarget.entries.toSet(), preset.tuning.overrides.keys)

            val restored = json.decodeFromString<GlassTuningPreference>(
                json.encodeToString(preset.tuning)
            )
            assertEquals(preset, restored.matchingPreset())
        }

        assertTrue(GlassTuningPreset.Adaptive.tuning.adaptiveLuminance)
        assertFalse(GlassTuningPreset.Adaptive.tuning.isDefault)
    }

    @Test
    fun advancedChange_noLongerMatchesPreset() {
        val crystal = GlassTuningPreset.Crystal.tuning
        val card = crystal.overrides.getValue(GlassMaterialTarget.Card)
        val customized = crystal.copy(
            overrides = crystal.overrides + (
                GlassMaterialTarget.Card to card.copy(
                    blurRadius = checkNotNull(card.blurRadius) + 1f
                )
            )
        )

        assertNull(customized.matchingPreset())
        assertTrue(customized.overrides.isNotEmpty())
    }
}
