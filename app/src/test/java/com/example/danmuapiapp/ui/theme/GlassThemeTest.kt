package com.example.danmuapiapp.ui.theme

import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassEffectOverridePreference
import com.example.danmuapiapp.domain.model.GlassMaterialTarget
import com.example.danmuapiapp.domain.model.GlassTuningPreset
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassThemeTest {

    @Test
    fun liquidGlass_usesReferenceEffectsAndLightSurfaceAlpha() {
        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.LiquidGlass,
            darkTheme = false
        )

        assertTrue(spec.enabled)
        assertEquals(8.dp, spec.blurRadius)
        assertEquals(24.dp, spec.refractionHeight)
        assertEquals(24.dp, spec.refractionAmount)
        assertEquals(0.4f, spec.bottomBarAlpha)
        assertEquals(0.56f, spec.contentAlpha)
        assertEquals(0.82f, spec.dialogAlpha)
    }

    @Test
    fun liquidGlass_darkTheme_usesReadableSurfaceAlpha() {
        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.LiquidGlass,
            darkTheme = true
        )

        assertTrue(spec.enabled)
        assertEquals(0.48f, spec.contentAlpha)
        assertEquals(0.78f, spec.dialogAlpha)
    }

    @Test
    fun crystalPreset_appliesToAllResolvedMaterialRoles() {
        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.LiquidGlass,
            darkTheme = false,
            tuning = GlassMaterialTuningState(GlassTuningPreset.Crystal.tuning)
        )

        assertEquals(2.dp, spec.card.blurRadius)
        assertEquals(0.22f, spec.card.surfaceAlpha)
        assertEquals(7.dp, spec.dialog.blurRadius)
        assertEquals(0.64f, spec.dialog.surfaceAlpha)
        assertEquals(0.18f, spec.button.surfaceAlpha)
        assertEquals(0.12f, spec.dialogButton.surfaceAlpha)
        assertEquals(0.58f, spec.primaryButton.surfaceAlpha)
        assertEquals(0.26f, spec.dialogPrimaryButton.surfaceAlpha)
        assertEquals(0.13f, spec.selected.surfaceAlpha)
        assertEquals(0.26f, spec.bottomBar.surfaceAlpha)
    }

    @Test
    fun roleTuning_overridesOnlyTheRequestedGlassRole() {
        val tuning = GlassMaterialTuningState()
        tuning.setOverride(
            GlassMaterialRole.Dialog,
            GlassEffectOverride(
                blurRadius = 24.dp,
                surfaceAlpha = 0.64f
            )
        )

        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.LiquidGlass,
            darkTheme = false,
            tuning = tuning
        )

        assertEquals(24.dp, spec.dialog.blurRadius)
        assertEquals(0.64f, spec.dialog.surfaceAlpha)
        assertEquals(8.dp, spec.card.blurRadius)
        assertEquals(0.56f, spec.card.surfaceAlpha)

        val disabled = resolveGlassMaterialSpec(
            GlassMaterialPreference.Off,
            darkTheme = false,
            tuning = tuning
        )
        assertFalse(disabled.enabled)
        assertEquals(0.dp, disabled.dialog.blurRadius)
    }

    @Test
    fun persistedTuning_mapsToUiRolesAndBack() {
        val preference = GlassTuningPreference(
            adaptiveLuminance = true,
            overrides = mapOf(
                GlassMaterialTarget.BottomBar to GlassEffectOverridePreference(
                    blurRadius = 14f,
                    chromaticAberration = true
                )
            )
        )
        val tuning = GlassMaterialTuningState(preference)

        assertEquals(14.dp, tuning.bottomBarOverride?.blurRadius)
        assertEquals(true, tuning.bottomBarOverride?.chromaticAberration)
        assertTrue(tuning.adaptiveLuminance)
        assertEquals(preference, tuning.toPreference())

        tuning.reset()
        assertFalse(tuning.adaptiveLuminance)
        assertTrue(tuning.toPreference().isDefault)
    }

    @Test
    fun off_disablesGlassEffects() {
        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.Off,
            darkTheme = false
        )

        assertFalse(spec.enabled)
        assertEquals(0.dp, spec.blurRadius)
        assertEquals(0.dp, spec.refractionHeight)
        assertEquals(0.dp, spec.refractionAmount)
        assertEquals(1f, spec.bottomBarAlpha)
        assertEquals(1f, spec.contentAlpha)
        assertEquals(1f, spec.dialogAlpha)
    }

    @Test
    fun legacyPalette_isSelectedWhenGlassIsOffOrUnsupported() {
        assertFalse(usesLiquidGlassPalette(GlassMaterialPreference.Off, sdkInt = 35))
        assertFalse(usesLiquidGlassPalette(GlassMaterialPreference.LiquidGlass, sdkInt = 32))
        assertTrue(usesLiquidGlassPalette(GlassMaterialPreference.LiquidGlass, sdkInt = 33))
    }

    @Test
    fun liquidGlass_requiresAndroid13ForFullReferenceEffect() {
        assertFalse(isLiquidGlassSupported(32))
        assertTrue(isLiquidGlassSupported(33))
    }

    @Test
    fun imageBackground_requiresLiquidGlass() {
        val background = AppBackgroundPreference(
            mode = AppBackgroundMode.OnlineImage,
            onlineImageUrl = "https://example.com/background.jpg"
        )

        assertNull(resolveEffectiveBackgroundImageData(background, 1L, glassEnabled = false))
        assertEquals(
            "https://example.com/background.jpg",
            resolveEffectiveBackgroundImageData(background, 1L, glassEnabled = true)
        )
    }

    @Test
    fun timedRandomBackground_onlyRefreshesOnForegroundAfterInterval() {
        assertFalse(
            shouldRefreshRandomBackground(
                nowElapsedMillis = 30_000L,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = true,
                policy = AppBackgroundRefreshPolicy.Seconds30
            )
        )
        assertTrue(
            shouldRefreshRandomBackground(
                nowElapsedMillis = 30_001L,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = true,
                policy = AppBackgroundRefreshPolicy.Seconds30
            )
        )
        assertFalse(
            shouldRefreshRandomBackground(
                nowElapsedMillis = Long.MAX_VALUE,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = false,
                policy = AppBackgroundRefreshPolicy.OnForeground
            )
        )
        assertTrue(
            shouldRefreshRandomBackground(
                nowElapsedMillis = 2L,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = true,
                policy = AppBackgroundRefreshPolicy.OnForeground,
                customRefreshSeconds = 0L
            )
        )
        assertFalse(
            shouldRefreshRandomBackground(
                nowElapsedMillis = 90_000L,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = true,
                policy = AppBackgroundRefreshPolicy.Custom,
                customRefreshSeconds = 90L
            )
        )
        assertTrue(
            shouldRefreshRandomBackground(
                nowElapsedMillis = 90_001L,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = true,
                policy = AppBackgroundRefreshPolicy.Custom,
                customRefreshSeconds = 90L
            )
        )
        assertFalse(
            shouldRefreshRandomBackground(
                nowElapsedMillis = Long.MAX_VALUE,
                lastRefreshElapsedMillis = 1L,
                refreshEnabled = true,
                policy = AppBackgroundRefreshPolicy.Custom,
                customRefreshSeconds = 0L
            )
        )
    }
}
