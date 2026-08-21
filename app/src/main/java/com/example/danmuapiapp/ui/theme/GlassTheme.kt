package com.example.danmuapiapp.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.GlassMaterialPreference

@Immutable
data class GlassMaterialSpec(
    val enabled: Boolean,
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val bottomBarAlpha: Float,
    val contentAlpha: Float
) {
    companion object {
        val Disabled = GlassMaterialSpec(
            enabled = false,
            blurRadius = 0.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0.dp,
            bottomBarAlpha = 1f,
            contentAlpha = 1f
        )
    }
}

val LocalGlassMaterial = staticCompositionLocalOf { GlassMaterialSpec.Disabled }

internal fun isLiquidGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU
}

internal fun resolveGlassMaterialSpec(
    preference: GlassMaterialPreference,
    darkTheme: Boolean
): GlassMaterialSpec {
    if (preference == GlassMaterialPreference.Off) {
        return GlassMaterialSpec.Disabled
    }
    return GlassMaterialSpec(
        enabled = true,
        // These are the values used by AndroidLiquidGlass's LiquidBottomTabs.
        blurRadius = 8.dp,
        refractionHeight = 24.dp,
        refractionAmount = 24.dp,
        bottomBarAlpha = 0.4f,
        contentAlpha = if (darkTheme) 0.74f else 0.8f
    )
}

@Composable
fun ProvideGlassTheme(
    preference: GlassMaterialPreference,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val effectivePreference = if (isLiquidGlassSupported()) {
        preference
    } else {
        GlassMaterialPreference.Off
    }
    val spec = remember(effectivePreference, darkTheme) {
        resolveGlassMaterialSpec(
            preference = effectivePreference,
            darkTheme = darkTheme
        )
    }
    CompositionLocalProvider(LocalGlassMaterial provides spec, content = content)
}

@Composable
fun glassSurfaceColor(emphasized: Boolean = false): Color {
    val colors = MaterialTheme.colorScheme
    val spec = LocalGlassMaterial.current
    if (!spec.enabled) {
        return if (emphasized) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    }
    val base = if (emphasized) colors.surfaceContainerHighest else colors.surfaceContainerHigh
    return base.copy(alpha = spec.contentAlpha)
}

@Composable
fun glassBorderColor(): Color {
    val spec = LocalGlassMaterial.current
    val alpha = if (!spec.enabled) 0.28f else 0.44f
    return MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
}

@Composable
fun GlassAppBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}
