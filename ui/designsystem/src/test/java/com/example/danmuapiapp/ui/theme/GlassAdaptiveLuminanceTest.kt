package com.example.danmuapiapp.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassAdaptiveLuminanceTest {

    @Test
    fun imageGrid_samplesTheVisibleBackgroundRegion() {
        val pixels = IntArray(48 * 48) { index ->
            if (index / 48 < 24) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        }
        val source = GlassImageLuminanceSource(
            sourceWidth = 48,
            sourceHeight = 96,
            pixels = pixels
        )
        val grid = GlassLuminanceGrid.image(
            viewportSize = IntSize(480, 960),
            source = source,
            overlay = Color.Transparent
        )

        val top = grid.luminanceAt(grid.cellAt(Offset(240f, 120f)))
        val bottom = grid.luminanceAt(grid.cellAt(Offset(240f, 840f)))

        assertTrue(top > 0.95f)
        assertTrue(bottom < 0.05f)
    }

    @Test
    fun solidGrid_returnsOneStableLuminance() {
        val grid = GlassLuminanceGrid.solid(
            viewportSize = IntSize(1080, 2400),
            color = Color(0xFF808080)
        )

        val first = grid.luminanceAt(grid.cellAt(Offset(1f, 1f)))
        val last = grid.luminanceAt(grid.cellAt(Offset(1079f, 2399f)))

        assertEquals(first, last, 0.0001f)
    }

    @Test
    fun adaptiveStyle_increasesSeparationOnlyForConflictingBackgrounds() {
        val base = GlassEffectStyle(
            blurRadius = 6.dp,
            refractionHeight = 18.dp,
            refractionAmount = 34.dp,
            surfaceAlpha = 0.4f,
            brightness = 0f,
            contrast = 1.05f,
            saturation = 1.3f,
            highlightAlpha = 0.28f
        )

        val lightThemeOnDark = adaptGlassStyleForLuminance(base, 0f, darkTheme = false)
        val lightThemeOnLight = adaptGlassStyleForLuminance(base, 1f, darkTheme = false)
        val darkThemeOnLight = adaptGlassStyleForLuminance(base, 1f, darkTheme = true)

        assertTrue(lightThemeOnDark.surfaceAlpha > base.surfaceAlpha)
        assertTrue(lightThemeOnDark.brightness > base.brightness)
        assertTrue(lightThemeOnDark.blurRadius > base.blurRadius)
        assertTrue(lightThemeOnLight.surfaceAlpha < base.surfaceAlpha)
        assertTrue(darkThemeOnLight.surfaceAlpha > base.surfaceAlpha)
        assertTrue(darkThemeOnLight.brightness < base.brightness)
    }
}
