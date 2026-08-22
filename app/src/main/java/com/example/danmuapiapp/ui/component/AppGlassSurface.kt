/*
 * Glass effects follow AndroidLiquidGlass's DialogContent, ScrollContainerContent,
 * and LiquidButton examples at commit b18eb0ff12c616546a68c72e7d0097f1ab286c87.
 * Licensed under Apache-2.0.
 */
package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.theme.LocalAppDarkTheme
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

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
    backdropOverride: Backdrop? = null,
    glassAlpha: Float? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val spec = LocalGlassMaterial.current
    val backdrop = backdropOverride ?: LocalGlassBackgroundBackdrop.current
    val darkTheme = LocalAppDarkTheme.current
    val glassEnabled = spec.enabled && backdrop != null
    val surfaceAlpha = glassAlpha?.coerceIn(0f, 1f) ?: when (role) {
        AppGlassSurfaceRole.Card -> spec.contentAlpha
        AppGlassSurfaceRole.Dialog -> spec.dialogAlpha
    }
    val tint = color.copy(alpha = minOf(color.alpha, surfaceAlpha))
    val glassModifier = if (glassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                when (role) {
                    AppGlassSurfaceRole.Card -> {
                        vibrancy()
                        blur(spec.blurRadius.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    }

                    AppGlassSurfaceRole.Dialog -> {
                        colorControls(
                            brightness = if (darkTheme) 0f else 0.2f,
                            saturation = 1.5f
                        )
                        blur(if (darkTheme) 8.dp.toPx() else 16.dp.toPx())
                        lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
                    }
                }
            },
            highlight = {
                if (role == AppGlassSurfaceRole.Dialog) Highlight.Plain else null
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
        modifier = modifier.then(glassModifier).then(clickModifier),
        shape = shape,
        color = if (glassEnabled) Color.Transparent else color,
        contentColor = contentColor,
        tonalElevation = if (glassEnabled) 0.dp else tonalElevation,
        shadowElevation = if (glassEnabled) 0.dp else shadowElevation,
        border = if (glassEnabled) null else border
    ) {
        content()
    }
}
