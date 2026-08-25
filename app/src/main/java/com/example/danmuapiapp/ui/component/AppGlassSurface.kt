/*
 * Glass effects follow AndroidLiquidGlass's DialogContent, ScrollContainerContent,
 * and LiquidButton examples at commit b18eb0ff12c616546a68c72e7d0097f1ab286c87.
 * Licensed under Apache-2.0.
 */
package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.theme.LocalAppDialogContext
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.example.danmuapiapp.ui.theme.LocalGlassMaterialTuning
import com.example.danmuapiapp.ui.theme.GlassMaterialRole
import com.example.danmuapiapp.ui.theme.glassEffectStyle
import com.example.danmuapiapp.ui.theme.rememberGlassAdaptiveEffect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangularShape

enum class AppGlassSurfaceRole {
    Card,
    Dialog
}

@Composable
fun AppGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    role: AppGlassSurfaceRole = AppGlassSurfaceRole.Card,
    materialRole: GlassMaterialRole? = null,
    backdropOverride: Backdrop? = null,
    glassAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val spec = LocalGlassMaterial.current
    val backdrop = backdropOverride ?: LocalGlassBackgroundBackdrop.current
    val dialogContent = LocalAppDialogContext.current && role == AppGlassSurfaceRole.Card
    val resolvedMaterialRole = materialRole ?: if (role == AppGlassSurfaceRole.Dialog) {
        GlassMaterialRole.Dialog
    } else {
        GlassMaterialRole.Card
    }
    val baseEffectStyle = glassEffectStyle(resolvedMaterialRole)
    val adaptiveEffect = rememberGlassAdaptiveEffect(baseEffectStyle)
    val effectStyle = adaptiveEffect.style
    val tuningOverride = if (spec.enabled) {
        LocalGlassMaterialTuning.current.overrideFor(resolvedMaterialRole)
    } else {
        null
    }
    // The dialog shell owns the only backdrop pass. Cards rendered inside it
    // are tonal overlays; refracting the same scene again creates visible
    // bands between the header, body, and action area.
    val dialogFlatSurface = spec.enabled && dialogContent
    val glassEnabled = spec.enabled && backdrop != null && !dialogFlatSurface
    // Existing callers keep their explicit alpha unless a saved role override
    // or the adaptive preset deliberately owns the material opacity.
    val surfaceAlpha = when {
        adaptiveEffect.active -> effectStyle.surfaceAlpha
        tuningOverride?.surfaceAlpha != null -> tuningOverride.surfaceAlpha.coerceIn(0f, 1f)
        glassAlpha != null -> glassAlpha.coerceIn(0f, 1f)
        else -> effectStyle.surfaceAlpha
    }
    val tint = if (adaptiveEffect.active || tuningOverride?.surfaceAlpha != null) {
        color.copy(alpha = surfaceAlpha)
    } else {
        color.copy(alpha = minOf(color.alpha, surfaceAlpha))
    }
    // Backdrop's lens shader only accepts rounded-rectangle shapes. Keep blur
    // and color treatment for arbitrary shapes such as RectangleShape, but do
    // not let the optional refraction effect crash composition.
    val lensSupported = shape is CornerBasedShape || shape is RoundedRectangularShape
    val fallbackColor = when {
        // Dialog internals stay flat to avoid recursive backdrop passes while
        // saved and adaptive material opacity still applies to their fill.
        dialogFlatSurface && (adaptiveEffect.active || tuningOverride?.surfaceAlpha != null) -> tint
        dialogFlatSurface -> color
        dialogContent && !glassEnabled && color.alpha >= 0.34f -> color.copy(alpha = 1f)
        else -> color
    }
    val glassModifier = if (glassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                colorControls(
                    brightness = effectStyle.brightness,
                    contrast = effectStyle.contrast,
                    saturation = effectStyle.saturation
                )
                blur(effectStyle.blurRadius.toPx())
                if (lensSupported) {
                    lens(
                        effectStyle.refractionHeight.toPx(),
                        effectStyle.refractionAmount.toPx(),
                        depthEffect = effectStyle.depthEffect,
                        chromaticAberration = effectStyle.chromaticAberration
                    )
                }
            },
            highlight = {
                if (effectStyle.highlightAlpha > 0f) {
                    Highlight.Plain.copy(
                        width = effectStyle.highlightWidth,
                        blurRadius = effectStyle.highlightWidth / 2f,
                        alpha = effectStyle.highlightAlpha
                    )
                } else {
                    null
                }
            },
            onDrawSurface = { drawRect(tint) }
        )
    } else {
        Modifier
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(adaptiveEffect.positionModifier)
            .then(glassModifier)
            .then(clickModifier),
        shape = shape,
        color = if (glassEnabled) Color.Transparent else fallbackColor,
        contentColor = contentColor,
        tonalElevation = if (glassEnabled) 0.dp else tonalElevation,
        shadowElevation = if (glassEnabled) 0.dp else shadowElevation,
        border = if (glassEnabled && !dialogContent) null else border
    ) {
        content()
    }
}
