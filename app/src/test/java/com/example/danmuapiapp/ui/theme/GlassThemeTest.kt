package com.example.danmuapiapp.ui.theme

import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassThemeTest {

    @Test
    fun liquidGlass_usesReferenceBottomTabParameters() {
        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.LiquidGlass,
            darkTheme = false
        )

        assertTrue(spec.enabled)
        assertEquals(8.dp, spec.blurRadius)
        assertEquals(24.dp, spec.refractionHeight)
        assertEquals(24.dp, spec.refractionAmount)
        assertEquals(0.4f, spec.bottomBarAlpha)
        assertEquals(0.8f, spec.contentAlpha)
    }

    @Test
    fun liquidGlass_darkTheme_keepsReferenceSurfaceAlpha() {
        val spec = resolveGlassMaterialSpec(
            GlassMaterialPreference.LiquidGlass,
            darkTheme = true
        )

        assertTrue(spec.enabled)
        assertEquals(0.74f, spec.contentAlpha)
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
    }

    @Test
    fun liquidGlass_requiresAndroid13ForFullReferenceEffect() {
        assertFalse(isLiquidGlassSupported(32))
        assertTrue(isLiquidGlassSupported(33))
    }
}
