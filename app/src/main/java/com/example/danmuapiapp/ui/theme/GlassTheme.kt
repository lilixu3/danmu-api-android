package com.example.danmuapiapp.ui.theme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.resolveImageData
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Immutable
data class GlassMaterialSpec(
    val enabled: Boolean,
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val bottomBarAlpha: Float,
    val contentAlpha: Float,
    val dialogAlpha: Float
) {
    companion object {
        val Disabled = GlassMaterialSpec(
            enabled = false,
            blurRadius = 0.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0.dp,
            bottomBarAlpha = 1f,
            contentAlpha = 1f,
            dialogAlpha = 1f
        )
    }
}

val LocalGlassMaterial = staticCompositionLocalOf { GlassMaterialSpec.Disabled }
val LocalAppBackground = staticCompositionLocalOf { AppBackgroundPreference() }
val LocalAppBackgroundForegroundKey = staticCompositionLocalOf { 0L }
/** True while content is rendered inside the shared dialog surface. */
val LocalAppDialogContext = staticCompositionLocalOf { false }

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
        contentAlpha = if (darkTheme) 0.48f else 0.56f,
        // Dialogs keep the backdrop blur and refraction, but need a denser neutral
        // wash than cards so text and controls remain readable over photographs.
        dialogAlpha = if (darkTheme) 0.78f else 0.82f
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
fun GlassAppBackground(
    modifier: Modifier = Modifier,
    solidBackgroundColor: Color? = null
) {
    val context = LocalContext.current
    val darkTheme = LocalAppDarkTheme.current
    val background = LocalAppBackground.current
    val foregroundKey = LocalAppBackgroundForegroundKey.current
    val glassEnabled = LocalGlassMaterial.current.enabled
    val imageData = remember(background, foregroundKey, glassEnabled) {
        resolveEffectiveBackgroundImageData(background, foregroundKey, glassEnabled)
    }
    val randomOnline = background.mode == AppBackgroundMode.RandomOnlineImage
    var displayedImageData by remember { mutableStateOf<String?>(null) }
    var pendingImageData by remember { mutableStateOf<String?>(null) }
    var pendingImageReady by remember { mutableStateOf(false) }
    var startCrossfade by remember { mutableStateOf(false) }

    // Keep the displayed image while a replacement is downloading. The new
    // image is promoted only after Coil reports success, so a refresh never
    // exposes the solid background between requests.
    LaunchedEffect(imageData) {
        when {
            imageData == null -> {
                displayedImageData = null
                pendingImageData = null
                pendingImageReady = false
                startCrossfade = false
            }

            imageData == displayedImageData -> {
                pendingImageData = null
                pendingImageReady = false
                startCrossfade = false
            }

            else -> {
                pendingImageData = imageData
                pendingImageReady = false
                startCrossfade = false
            }
        }
    }

    val crossfadeProgress by animateFloatAsState(
        targetValue = if (startCrossfade) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "backgroundCrossfade"
    )

    LaunchedEffect(pendingImageData, pendingImageReady, displayedImageData) {
        val candidate = pendingImageData
        if (candidate == null || !pendingImageReady) return@LaunchedEffect
        if (displayedImageData == null) {
            displayedImageData = candidate
            pendingImageData = null
            pendingImageReady = false
            startCrossfade = false
        } else if (candidate != displayedImageData) {
            startCrossfade = true
        }
    }

    LaunchedEffect(crossfadeProgress) {
        val candidate = pendingImageData
        if (startCrossfade && candidate != null && crossfadeProgress >= 0.999f) {
            displayedImageData = candidate
            pendingImageData = null
            pendingImageReady = false
            startCrossfade = false
        }
    }

    val displayedData = displayedImageData
    val pendingData = pendingImageData
    val displayedRequest = remember(context, displayedData, randomOnline) {
        displayedData?.let { data ->
            buildBackgroundImageRequest(
                context = context,
                imageData = data,
                randomOnline = randomOnline
            )
        }
    }
    val pendingRequest = remember(context, pendingData, randomOnline) {
        pendingData?.let { data ->
            buildBackgroundImageRequest(
                context = context,
                imageData = data,
                randomOnline = randomOnline
            )
        }
    }
    val displayedAlpha = if (startCrossfade) 1f - crossfadeProgress else 1f
    val pendingAlpha = if (startCrossfade && pendingImageReady) crossfadeProgress else 0f
    val hasLoadedImage = displayedImageData != null || pendingImageReady

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(solidBackgroundColor ?: MaterialTheme.colorScheme.background)
    ) {
        if (displayedRequest != null && displayedData != null) {
            AsyncImage(
                model = displayedRequest,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(displayedAlpha),
                contentScale = ContentScale.Crop,
                onError = { /* Keep the previous image visible on replacement errors. */ }
            )
        }
        if (pendingRequest != null && pendingData != null) {
            AsyncImage(
                model = pendingRequest,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(pendingAlpha),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    if (pendingImageData == pendingData) {
                        pendingImageReady = true
                    }
                },
                onError = {
                    if (pendingImageData == pendingData) {
                        pendingImageData = null
                        pendingImageReady = false
                        startCrossfade = false
                    }
                }
            )
        }
        if (hasLoadedImage) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (darkTheme) {
                            Color(0xFF24262B).copy(alpha = 0.34f)
                        } else {
                            MaterialTheme.colorScheme.background.copy(alpha = 0.40f)
                        }
                    )
            )
        }
    }
}

private fun buildBackgroundImageRequest(
    context: android.content.Context,
    imageData: String,
    randomOnline: Boolean
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(imageData)
        .crossfade(false)
        .apply {
            if (randomOnline) {
                // Keep the successful candidate in memory so promoting it to
                // the displayed slot does not trigger a second network load.
                diskCachePolicy(CachePolicy.DISABLED)
            }
        }
        .build()
}

internal fun resolveEffectiveBackgroundImageData(
    background: AppBackgroundPreference,
    foregroundKey: Long,
    glassEnabled: Boolean
): String? {
    return if (glassEnabled) background.resolveImageData(foregroundKey) else null
}

/** Backdrop containing only the app background, so cards never sample themselves or adjacent content. */
val LocalGlassBackgroundBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun GlassBackdropScene(
    modifier: Modifier = Modifier,
    solidBackgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val enabled = LocalGlassMaterial.current.enabled
    val backgroundBackdrop = rememberLayerBackdrop()

    Box(modifier = modifier) {
        GlassAppBackground(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (enabled) {
                        Modifier.layerBackdrop(backgroundBackdrop)
                    } else {
                        Modifier
                    }
                ),
            solidBackgroundColor = solidBackgroundColor
        )
        CompositionLocalProvider(
            LocalGlassBackgroundBackdrop provides backgroundBackdrop
        ) {
            content()
        }
    }
}
