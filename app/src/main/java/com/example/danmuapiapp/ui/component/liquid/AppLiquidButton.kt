/*
 * Adapted from AndroidLiquidGlass's LiquidButton at commit
 * b18eb0ff12c616546a68c72e7d0097f1ab286c87. Licensed under Apache-2.0.
 */
package com.example.danmuapiapp.ui.component.liquid

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun AppLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val spec = LocalGlassMaterial.current
    val backdrop = LocalGlassBackgroundBackdrop.current
    val glassEnabled = spec.enabled && backdrop != null
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    val fallbackColor = when {
        surfaceColor.isSpecified -> surfaceColor
        tint.isSpecified -> tint
        else -> Color.Transparent
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier.then(
                if (glassEnabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx())
                        },
                        layerBlock = if (enabled) {
                            {
                                val progress = interactiveHighlight.pressProgress
                                val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, progress)
                                val maxOffset = size.minDimension
                                val offset = interactiveHighlight.offset
                                translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                                translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)

                                val maxDragScale = 4.dp.toPx() / size.height
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX = scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (size.width / size.height).fastCoerceAtMost(1f)
                                scaleY = scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (size.height / size.width).fastCoerceAtMost(1f)
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            if (tint.isSpecified) {
                                drawRect(tint, blendMode = BlendMode.Hue)
                                drawRect(tint.copy(alpha = tint.alpha * 0.75f))
                            }
                            if (surfaceColor.isSpecified) {
                                drawRect(surfaceColor)
                            }
                        }
                    )
                } else {
                    Modifier
                        .clip(shape)
                        .background(fallbackColor)
                }
            )
                .clickable(
                interactionSource = null,
                indication = if (glassEnabled && enabled) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
                .then(
                if (glassEnabled && enabled) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
                .height(height)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
