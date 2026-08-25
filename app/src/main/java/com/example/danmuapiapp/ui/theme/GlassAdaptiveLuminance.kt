package com.example.danmuapiapp.ui.theme

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

private const val SOURCE_COLUMNS = 48
private const val SOURCE_ROWS = 48
private const val VIEWPORT_COLUMNS = 18
private const val VIEWPORT_ROWS = 36

internal class GlassImageLuminanceSource(
    val sourceWidth: Int,
    val sourceHeight: Int,
    private val pixels: IntArray
) {
    fun colorAt(normalizedX: Float, normalizedY: Float): Color {
        val column = (normalizedX.coerceIn(0f, 0.9999f) * SOURCE_COLUMNS).toInt()
        val row = (normalizedY.coerceIn(0f, 0.9999f) * SOURCE_ROWS).toInt()
        return Color(pixels[row * SOURCE_COLUMNS + column])
    }

    companion object {
        fun from(drawable: Drawable): GlassImageLuminanceSource? = runCatching {
            val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: SOURCE_COLUMNS
            val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: SOURCE_ROWS
            val bitmap = drawable.toBitmap(
                width = SOURCE_COLUMNS,
                height = SOURCE_ROWS,
                config = Bitmap.Config.ARGB_8888
            )
            val pixels = IntArray(SOURCE_COLUMNS * SOURCE_ROWS)
            bitmap.getPixels(
                pixels,
                0,
                SOURCE_COLUMNS,
                0,
                0,
                SOURCE_COLUMNS,
                SOURCE_ROWS
            )
            GlassImageLuminanceSource(sourceWidth, sourceHeight, pixels)
        }.getOrNull()
    }
}

@Stable
internal class GlassLuminanceGrid private constructor(
    val viewportSize: IntSize,
    private val values: FloatArray
) {
    val averageLuminance: Float = values.average().toFloat().coerceIn(0f, 1f)

    fun cellAt(position: Offset): IntOffset {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return IntOffset(-1, -1)
        val column = (position.x / viewportSize.width * VIEWPORT_COLUMNS)
            .toInt()
            .coerceIn(0, VIEWPORT_COLUMNS - 1)
        val row = (position.y / viewportSize.height * VIEWPORT_ROWS)
            .toInt()
            .coerceIn(0, VIEWPORT_ROWS - 1)
        return IntOffset(column, row)
    }

    fun luminanceAt(cell: IntOffset): Float {
        if (cell.x !in 0 until VIEWPORT_COLUMNS || cell.y !in 0 until VIEWPORT_ROWS) {
            return averageLuminance
        }
        return values[cell.y * VIEWPORT_COLUMNS + cell.x]
    }

    companion object {
        fun neutral(): GlassLuminanceGrid {
            return GlassLuminanceGrid(
                viewportSize = IntSize(1, 1),
                values = FloatArray(VIEWPORT_COLUMNS * VIEWPORT_ROWS) { 0.5f }
            )
        }

        fun solid(viewportSize: IntSize, color: Color): GlassLuminanceGrid {
            return GlassLuminanceGrid(
                viewportSize = viewportSize,
                values = FloatArray(VIEWPORT_COLUMNS * VIEWPORT_ROWS) {
                    color.perceivedLuminance()
                }
            )
        }

        fun image(
            viewportSize: IntSize,
            source: GlassImageLuminanceSource,
            overlay: Color
        ): GlassLuminanceGrid {
            if (viewportSize.width <= 0 || viewportSize.height <= 0) {
                return solid(IntSize(1, 1), overlay)
            }
            val viewportWidth = viewportSize.width.toFloat()
            val viewportHeight = viewportSize.height.toFloat()
            val scale = max(
                viewportWidth / source.sourceWidth,
                viewportHeight / source.sourceHeight
            )
            val renderedWidth = source.sourceWidth * scale
            val renderedHeight = source.sourceHeight * scale
            val cropX = (renderedWidth - viewportWidth) / 2f
            val cropY = (renderedHeight - viewportHeight) / 2f
            val values = FloatArray(VIEWPORT_COLUMNS * VIEWPORT_ROWS) { index ->
                val column = index % VIEWPORT_COLUMNS
                val row = index / VIEWPORT_COLUMNS
                val viewportX = (column + 0.5f) / VIEWPORT_COLUMNS * viewportWidth
                val viewportY = (row + 0.5f) / VIEWPORT_ROWS * viewportHeight
                val sourceX = (viewportX + cropX) / renderedWidth
                val sourceY = (viewportY + cropY) / renderedHeight
                overlay.compositeOver(source.colorAt(sourceX, sourceY)).perceivedLuminance()
            }
            return GlassLuminanceGrid(viewportSize, values)
        }
    }
}

private fun Color.perceivedLuminance(): Float {
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 1f)
}

@Stable
internal class GlassAdaptiveLuminanceState {
    var grid by mutableStateOf(GlassLuminanceGrid.neutral())
        private set

    fun update(grid: GlassLuminanceGrid) {
        this.grid = grid
    }
}

internal val LocalGlassAdaptiveLuminance = staticCompositionLocalOf {
    GlassAdaptiveLuminanceState()
}

internal data class GlassAdaptiveEffect(
    val style: GlassEffectStyle,
    val active: Boolean,
    val luminance: Float,
    val positionModifier: Modifier
)

@Composable
internal fun rememberGlassAdaptiveEffect(baseStyle: GlassEffectStyle): GlassAdaptiveEffect {
    val spec = LocalGlassMaterial.current
    val tuning = LocalGlassMaterialTuning.current
    val darkTheme = LocalAppDarkTheme.current
    val grid = LocalGlassAdaptiveLuminance.current.grid
    val active = spec.enabled && tuning.adaptiveLuminance
    var sampledCell by remember { mutableStateOf(IntOffset(-1, -1)) }
    val targetLuminance = if (active) {
        grid.luminanceAt(sampledCell)
    } else {
        0.5f
    }
    val luminance by animateFloatAsState(
        targetValue = targetLuminance,
        animationSpec = tween(durationMillis = 500),
        label = "adaptiveGlassLuminance"
    )
    val style = remember(baseStyle, active, luminance, darkTheme) {
        if (active) {
            adaptGlassStyleForLuminance(baseStyle, luminance, darkTheme)
        } else {
            baseStyle
        }
    }
    val positionModifier = if (active) {
        Modifier.onGloballyPositioned { coordinates ->
            val center = coordinates.boundsInRoot().center
            val cell = grid.cellAt(center)
            if (cell != sampledCell) sampledCell = cell
        }
    } else {
        Modifier
    }
    return GlassAdaptiveEffect(style, active, luminance, positionModifier)
}

internal fun adaptGlassStyleForLuminance(
    style: GlassEffectStyle,
    luminance: Float,
    darkTheme: Boolean
): GlassEffectStyle {
    val signedLuminance = ((luminance.coerceIn(0f, 1f) - 0.5f) * 2f)
    val conflict = if (darkTheme) {
        signedLuminance.coerceAtLeast(0f)
    } else {
        (-signedLuminance).coerceAtLeast(0f)
    }
    val compatible = if (darkTheme) {
        (-signedLuminance).coerceAtLeast(0f)
    } else {
        signedLuminance.coerceAtLeast(0f)
    }
    val conflictWeight = conflict * conflict
    val compatibleWeight = compatible * compatible
    val brightnessDelta = (if (darkTheme) -0.10f else 0.10f) * conflictWeight
    val targetSaturation = 1f + (style.saturation - 1f) * (1f - 0.22f * conflictWeight)

    return style.copy(
        blurRadius = (style.blurRadius.value + 4f * conflictWeight - 1.5f * compatibleWeight)
            .coerceAtLeast(0f)
            .dp,
        surfaceAlpha = (style.surfaceAlpha + 0.16f * conflictWeight - 0.04f * compatibleWeight)
            .coerceIn(0f, 1f),
        brightness = (style.brightness + brightnessDelta).coerceIn(-0.25f, 0.25f),
        contrast = (style.contrast + 0.06f * conflictWeight).coerceIn(0.5f, 1.8f),
        saturation = targetSaturation.coerceIn(0f, 2.2f),
        highlightAlpha = (
            style.highlightAlpha + 0.06f * conflictWeight - 0.03f * compatibleWeight
        ).coerceIn(0f, 1f)
    ).normalized()
}
