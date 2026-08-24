package com.example.danmuapiapp.ui.theme

import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.ui.component.AppDialogHost

/** Explicit app theme state; unlike isSystemInDarkTheme this also reflects the in-app override. */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

/** Palette introduced with the liquid-glass redesign. */
private val GlassDarkColorScheme = darkColorScheme(
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

private val GlassBlue40 = Color(0xFF4E6498)
private val GlassBlueGrey40 = Color(0xFF5D667C)
private val GlassIndigo40 = Color(0xFF6A6393)

/** Keep the off-state dark theme on the black/gray palette from the glass redesign. */
private val LegacyDarkColorScheme = GlassDarkColorScheme

private val LegacyLightColorScheme = lightColorScheme(
    primary = GlassBlue40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2DCF4),
    onPrimaryContainer = Color(0xFF26324D),
    secondary = GlassBlueGrey40,
    secondaryContainer = Color(0xFFDCE2EE),
    onSecondaryContainer = Color(0xFF2C3344),
    tertiary = GlassIndigo40,
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

private val GlassLightColorScheme = LegacyLightColorScheme

@Composable
fun DanmuApiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassMaterial: GlassMaterialPreference = GlassMaterialPreference.Default,
    appBackground: AppBackgroundPreference = AppBackgroundPreference(),
    content: @Composable () -> Unit
) {
    val useGlassPalette = usesLiquidGlassPalette(glassMaterial)
    val colorScheme = when {
        useGlassPalette && darkTheme -> GlassDarkColorScheme
        useGlassPalette -> GlassLightColorScheme
        darkTheme -> LegacyDarkColorScheme
        else -> LegacyLightColorScheme
    }
    val foregroundKey = rememberBackgroundRefreshKey(
        glassMaterial = glassMaterial,
        appBackground = appBackground
    )

    CompositionLocalProvider(
        LocalAppDarkTheme provides darkTheme,
        LocalAppBackground provides appBackground,
        LocalAppBackgroundForegroundKey provides foregroundKey
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography
        ) {
            ProvideGlassTheme(
                preference = glassMaterial,
                darkTheme = darkTheme
            ) {
                AppDialogHost(content = content)
            }
        }
    }
}

internal fun usesLiquidGlassPalette(
    preference: GlassMaterialPreference,
    sdkInt: Int = android.os.Build.VERSION.SDK_INT
): Boolean {
    return preference == GlassMaterialPreference.LiquidGlass && isLiquidGlassSupported(sdkInt)
}

@Composable
private fun rememberBackgroundRefreshKey(
    glassMaterial: GlassMaterialPreference,
    appBackground: AppBackgroundPreference
): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableLongStateOf(System.nanoTime()) }
    var lastRefreshAtMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val refreshEnabled = isLiquidGlassSupported() &&
        glassMaterial == GlassMaterialPreference.LiquidGlass &&
        appBackground.mode == AppBackgroundMode.RandomOnlineImage
    val currentRefreshEnabled by rememberUpdatedState(refreshEnabled)
    val currentRefreshPolicy by rememberUpdatedState(appBackground.randomRefreshPolicy)
    val currentCustomRefreshSeconds by rememberUpdatedState(
        appBackground.customRandomRefreshSeconds
    )

    LaunchedEffect(
        appBackground.mode,
        appBackground.randomImageUrl,
        appBackground.randomRefreshPolicy,
        appBackground.customRandomRefreshSeconds
    ) {
        lastRefreshAtMs = SystemClock.elapsedRealtime()
    }

    DisposableEffect(lifecycleOwner) {
        var suppressInitialResume =
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED).not()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (suppressInitialResume) {
                    suppressInitialResume = false
                } else {
                    val now = SystemClock.elapsedRealtime()
                    if (
                        shouldRefreshRandomBackground(
                            nowElapsedMillis = now,
                            lastRefreshElapsedMillis = lastRefreshAtMs,
                            refreshEnabled = currentRefreshEnabled,
                            policy = currentRefreshPolicy,
                            customRefreshSeconds = currentCustomRefreshSeconds
                        )
                    ) {
                        lastRefreshAtMs = now
                        refreshKey = System.nanoTime()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return refreshKey
}

internal fun shouldRefreshRandomBackground(
    nowElapsedMillis: Long,
    lastRefreshElapsedMillis: Long,
    refreshEnabled: Boolean,
    policy: AppBackgroundRefreshPolicy,
    customRefreshSeconds: Long = 0L
): Boolean {
    if (!refreshEnabled) return false
    if (policy == AppBackgroundRefreshPolicy.OnForeground) return true
    val intervalMillis = policy.resolveIntervalMillis(customRefreshSeconds) ?: return false
    if (lastRefreshElapsedMillis <= 0L || nowElapsedMillis < lastRefreshElapsedMillis) return true
    return nowElapsedMillis - lastRefreshElapsedMillis >= intervalMillis
}
