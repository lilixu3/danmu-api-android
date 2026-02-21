package com.example.danmuapiapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    // 暗色主题：霓虹夜景风（灵感来自 Tokyo Night / Catppuccin）
    primary            = Color(0xFF7DCFFF),
    onPrimary          = Color(0xFF031A28),
    primaryContainer   = Color(0xFF12324A),
    onPrimaryContainer = Color(0xFFBEE8FF),

    secondary            = Color(0xFFC4B5FD),
    onSecondary          = Color(0xFF2B1D46),
    secondaryContainer   = Color(0xFF3A2B5A),
    onSecondaryContainer = Color(0xFFE9DDFF),

    tertiary            = Color(0xFF73DACA),
    onTertiary          = Color(0xFF042620),
    tertiaryContainer   = Color(0xFF123F38),
    onTertiaryContainer = Color(0xFFB7F4EA),

    background   = Color(0xFF090E19),
    onBackground = Color(0xFFE6EAFA),
    surface      = Color(0xFF0E1422),
    onSurface    = Color(0xFFE6EAFA),

    surfaceVariant   = Color(0xFF202A42),
    onSurfaceVariant = Color(0xFF9CA8CA),

    outline        = Color(0xFF4F5E86),
    outlineVariant = Color(0xFF313D60),

    surfaceContainerLowest  = Color(0xFF060A13),
    surfaceContainerLow     = Color(0xFF0A1020),
    surfaceContainer        = Color(0xFF111A2E),
    surfaceContainerHigh    = Color(0xFF17233A),
    surfaceContainerHighest = Color(0xFF1F2D47),

    error            = Color(0xFFFF8BA7),
    onError          = Color(0xFF3A0618),
    errorContainer   = Color(0xFF5A1A32),
    onErrorContainer = Color(0xFFFFD9E3),
)

// 浅色主题保持现有风格

private val Blue40 = Color(0xFF4E6498)
private val BlueGrey40 = Color(0xFF5D667C)
private val Indigo40 = Color(0xFF6A6393)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE5F8),
    onPrimaryContainer = Color(0xFF26324D),
    secondary = BlueGrey40,
    secondaryContainer = Color(0xFFE3E7F1),
    onSecondaryContainer = Color(0xFF2C3344),
    tertiary = Indigo40,
    tertiaryContainer = Color(0xFFE5E1F4),
    onTertiaryContainer = Color(0xFF322F4B),
    background = Color(0xFFF4F5FA),
    surface = Color(0xFFF4F5FA),
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
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
