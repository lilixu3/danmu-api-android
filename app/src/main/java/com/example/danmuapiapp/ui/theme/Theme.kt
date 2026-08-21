package com.example.danmuapiapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.domain.model.GlassMaterialPreference

/** Explicit app theme state; unlike isSystemInDarkTheme this also reflects the in-app override. */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7DCFFF),
    onPrimary = Color(0xFF031A28),
    primaryContainer = Color(0xFF244554),
    onPrimaryContainer = Color(0xFFBEE8FF),

    secondary = Color(0xFFB6C7D9),
    onSecondary = Color(0xFF202A34),
    secondaryContainer = Color(0xFF414A54),
    onSecondaryContainer = Color(0xFFE5EDF5),

    tertiary = Color(0xFF73DACA),
    onTertiary = Color(0xFF042620),
    tertiaryContainer = Color(0xFF244B45),
    onTertiaryContainer = Color(0xFFB7F4EA),

    background = Color(0xFF24262B),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF2A2D32),
    onSurface = Color(0xFFF5F6F8),

    surfaceVariant = Color(0xFF3A3E45),
    onSurfaceVariant = Color(0xFFCDD1D7),

    outline = Color(0xFF8B929B),
    outlineVariant = Color(0xFF555C65),

    surfaceContainerLowest = Color(0xFF202226),
    surfaceContainerLow = Color(0xFF282B30),
    surfaceContainer = Color(0xFF2D3036),
    surfaceContainerHigh = Color(0xFF34383F),
    surfaceContainerHighest = Color(0xFF3D424A),

    error = Color(0xFFFF8BA7),
    onError = Color(0xFF3A0618),
    errorContainer = Color(0xFF5A1A32),
    onErrorContainer = Color(0xFFFFD9E3),
)

private val Blue40 = Color(0xFF4E6498)
private val BlueGrey40 = Color(0xFF5D667C)
private val Indigo40 = Color(0xFF6A6393)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color(0xFFFFFFFF),
    // 浅色主容器仅加深一点，保持同色系观感
    primaryContainer = Color(0xFFD2DCF4),
    onPrimaryContainer = Color(0xFF26324D),
    secondary = BlueGrey40,
    secondaryContainer = Color(0xFFDCE2EE),
    onSecondaryContainer = Color(0xFF2C3344),
    tertiary = Indigo40,
    tertiaryContainer = Color(0xFFDDD9EE),
    onTertiaryContainer = Color(0xFF322F4B),
    background = Color(0xFFF4F5FA),
    surface = Color(0xFFF4F5FA),
    surfaceVariant = Color(0xFFDCE2EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2F8),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE5E8F0),
    surfaceContainerHighest = Color(0xFFDDE1EA),
    onSurface = Color(0xFF232731),
    onSurfaceVariant = Color(0xFF646C7D),
    outline = Color(0xFF7B8395),
    outlineVariant = Color(0xFFC6CBD8),
)

@Composable
fun DanmuApiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassMaterial: GlassMaterialPreference = GlassMaterialPreference.Default,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography
        ) {
            ProvideGlassTheme(
                preference = glassMaterial,
                darkTheme = darkTheme,
                content = content
            )
        }
    }
}
