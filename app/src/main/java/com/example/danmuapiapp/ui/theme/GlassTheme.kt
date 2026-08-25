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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassEffectOverridePreference
import com.example.danmuapiapp.domain.model.GlassMaterialTarget
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.domain.model.resolveImageData
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Immutable
data class GlassEffectStyle(
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val surfaceAlpha: Float,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val highlightAlpha: Float = 0f,
    val highlightWidth: Dp = 0.5.dp,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false
) {
    fun normalized(): GlassEffectStyle = copy(
        blurRadius = blurRadius.coerceAtLeast(0.dp),
        refractionHeight = refractionHeight.coerceAtLeast(0.dp),
        refractionAmount = refractionAmount.coerceAtLeast(0.dp),
        surfaceAlpha = surfaceAlpha.coerceIn(0f, 1f),
        contrast = contrast.coerceAtLeast(0f),
        saturation = saturation.coerceAtLeast(0f),
        highlightAlpha = highlightAlpha.coerceIn(0f, 1f),
        highlightWidth = highlightWidth.coerceAtLeast(0.dp)
    )

    companion object {
        val Disabled = GlassEffectStyle(
            blurRadius = 0.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0.dp,
            surfaceAlpha = 1f
        )
    }
}

enum class GlassMaterialRole {
    Card,
    Dialog,
    Button,
    DialogButton,
    PrimaryButton,
    DialogPrimaryButton,
    Selected,
    BottomBar
}

@Immutable
data class GlassEffectOverride(
    val blurRadius: Dp? = null,
    val refractionHeight: Dp? = null,
    val refractionAmount: Dp? = null,
    val surfaceAlpha: Float? = null,
    val brightness: Float? = null,
    val contrast: Float? = null,
    val saturation: Float? = null,
    val highlightAlpha: Float? = null,
    val highlightWidth: Dp? = null,
    val depthEffect: Boolean? = null,
    val chromaticAberration: Boolean? = null
) {
    fun applyTo(style: GlassEffectStyle): GlassEffectStyle = style.copy(
        blurRadius = blurRadius ?: style.blurRadius,
        refractionHeight = refractionHeight ?: style.refractionHeight,
        refractionAmount = refractionAmount ?: style.refractionAmount,
        surfaceAlpha = surfaceAlpha ?: style.surfaceAlpha,
        brightness = brightness ?: style.brightness,
        contrast = contrast ?: style.contrast,
        saturation = saturation ?: style.saturation,
        highlightAlpha = highlightAlpha ?: style.highlightAlpha,
        highlightWidth = highlightWidth ?: style.highlightWidth,
        depthEffect = depthEffect ?: style.depthEffect,
        chromaticAberration = chromaticAberration ?: style.chromaticAberration
    ).normalized()
}

@Stable
class GlassMaterialTuningState(
    initialPreference: GlassTuningPreference = GlassTuningPreference()
) {
    var adaptiveLuminance by mutableStateOf(false)
    var cardOverride by mutableStateOf<GlassEffectOverride?>(null)
    var dialogOverride by mutableStateOf<GlassEffectOverride?>(null)
    var buttonOverride by mutableStateOf<GlassEffectOverride?>(null)
    var dialogButtonOverride by mutableStateOf<GlassEffectOverride?>(null)
    var primaryButtonOverride by mutableStateOf<GlassEffectOverride?>(null)
    var dialogPrimaryButtonOverride by mutableStateOf<GlassEffectOverride?>(null)
    var selectedOverride by mutableStateOf<GlassEffectOverride?>(null)
    var bottomBarOverride by mutableStateOf<GlassEffectOverride?>(null)

    init {
        replaceWith(initialPreference)
    }

    fun overrideFor(role: GlassMaterialRole): GlassEffectOverride? = when (role) {
        GlassMaterialRole.Card -> cardOverride
        GlassMaterialRole.Dialog -> dialogOverride
        GlassMaterialRole.Button -> buttonOverride
        GlassMaterialRole.DialogButton -> dialogButtonOverride
        GlassMaterialRole.PrimaryButton -> primaryButtonOverride
        GlassMaterialRole.DialogPrimaryButton -> dialogPrimaryButtonOverride
        GlassMaterialRole.Selected -> selectedOverride
        GlassMaterialRole.BottomBar -> bottomBarOverride
    }

    fun setOverride(role: GlassMaterialRole, override: GlassEffectOverride?) {
        when (role) {
            GlassMaterialRole.Card -> cardOverride = override
            GlassMaterialRole.Dialog -> dialogOverride = override
            GlassMaterialRole.Button -> buttonOverride = override
            GlassMaterialRole.DialogButton -> dialogButtonOverride = override
            GlassMaterialRole.PrimaryButton -> primaryButtonOverride = override
            GlassMaterialRole.DialogPrimaryButton -> dialogPrimaryButtonOverride = override
            GlassMaterialRole.Selected -> selectedOverride = override
            GlassMaterialRole.BottomBar -> bottomBarOverride = override
        }
    }

    fun reset() {
        adaptiveLuminance = false
        cardOverride = null
        dialogOverride = null
        buttonOverride = null
        dialogButtonOverride = null
        primaryButtonOverride = null
        dialogPrimaryButtonOverride = null
        selectedOverride = null
        bottomBarOverride = null
    }

    fun replaceWith(preference: GlassTuningPreference) {
        adaptiveLuminance = preference.adaptiveLuminance
        GlassMaterialRole.entries.forEach { role ->
            setOverride(
                role,
                preference.overrides[role.toPreferenceTarget()]?.toGlassOverride()
            )
        }
    }

    fun toPreference(): GlassTuningPreference {
        return GlassTuningPreference(
            adaptiveLuminance = adaptiveLuminance,
            overrides = GlassMaterialRole.entries.mapNotNull { role ->
                overrideFor(role)?.toPreferenceOverride()?.let { role.toPreferenceTarget() to it }
            }.toMap()
        ).normalized()
    }
}

private fun GlassMaterialRole.toPreferenceTarget(): GlassMaterialTarget = when (this) {
    GlassMaterialRole.Card -> GlassMaterialTarget.Card
    GlassMaterialRole.Dialog -> GlassMaterialTarget.Dialog
    GlassMaterialRole.Button -> GlassMaterialTarget.Button
    GlassMaterialRole.DialogButton -> GlassMaterialTarget.DialogButton
    GlassMaterialRole.PrimaryButton -> GlassMaterialTarget.PrimaryButton
    GlassMaterialRole.DialogPrimaryButton -> GlassMaterialTarget.DialogPrimaryButton
    GlassMaterialRole.Selected -> GlassMaterialTarget.Selected
    GlassMaterialRole.BottomBar -> GlassMaterialTarget.BottomBar
}

private fun GlassEffectOverridePreference.toGlassOverride(): GlassEffectOverride {
    return GlassEffectOverride(
        blurRadius = blurRadius?.dp,
        refractionHeight = refractionHeight?.dp,
        refractionAmount = refractionAmount?.dp,
        surfaceAlpha = surfaceAlpha,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        highlightAlpha = highlightAlpha,
        highlightWidth = highlightWidth?.dp,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration
    )
}

private fun GlassEffectOverride.toPreferenceOverride(): GlassEffectOverridePreference {
    return GlassEffectOverridePreference(
        blurRadius = blurRadius?.value,
        refractionHeight = refractionHeight?.value,
        refractionAmount = refractionAmount?.value,
        surfaceAlpha = surfaceAlpha,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        highlightAlpha = highlightAlpha,
        highlightWidth = highlightWidth?.value,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration
    )
}

@Immutable
data class GlassMaterialSpec(
    val enabled: Boolean,
    val card: GlassEffectStyle,
    val dialog: GlassEffectStyle,
    val button: GlassEffectStyle,
    val dialogButton: GlassEffectStyle,
    val primaryButton: GlassEffectStyle,
    val dialogPrimaryButton: GlassEffectStyle,
    val selected: GlassEffectStyle,
    val bottomBar: GlassEffectStyle
) {
    fun styleFor(role: GlassMaterialRole): GlassEffectStyle = when (role) {
        GlassMaterialRole.Card -> card
        GlassMaterialRole.Dialog -> dialog
        GlassMaterialRole.Button -> button
        GlassMaterialRole.DialogButton -> dialogButton
        GlassMaterialRole.PrimaryButton -> primaryButton
        GlassMaterialRole.DialogPrimaryButton -> dialogPrimaryButton
        GlassMaterialRole.Selected -> selected
        GlassMaterialRole.BottomBar -> bottomBar
    }
    // Compatibility accessors for existing non-component callers.
    val blurRadius: Dp get() = card.blurRadius
    val refractionHeight: Dp get() = bottomBar.refractionHeight
    val refractionAmount: Dp get() = bottomBar.refractionAmount
    val bottomBarAlpha: Float get() = bottomBar.surfaceAlpha
    val contentAlpha: Float get() = card.surfaceAlpha
    val dialogAlpha: Float get() = dialog.surfaceAlpha

    companion object {
        val Disabled = GlassMaterialSpec(
            enabled = false,
            card = GlassEffectStyle.Disabled,
            dialog = GlassEffectStyle.Disabled,
            button = GlassEffectStyle.Disabled,
            dialogButton = GlassEffectStyle.Disabled,
            primaryButton = GlassEffectStyle.Disabled,
            dialogPrimaryButton = GlassEffectStyle.Disabled,
            selected = GlassEffectStyle.Disabled,
            bottomBar = GlassEffectStyle.Disabled
        )
    }
}

val LocalGlassMaterial = staticCompositionLocalOf { GlassMaterialSpec.Disabled }
val LocalGlassMaterialTuning = staticCompositionLocalOf { GlassMaterialTuningState() }

@Composable
fun glassEffectStyle(role: GlassMaterialRole): GlassEffectStyle {
    return LocalGlassMaterial.current.styleFor(role)
}
val LocalAppBackground = staticCompositionLocalOf { AppBackgroundPreference() }
val LocalAppBackgroundForegroundKey = staticCompositionLocalOf { 0L }
/** True while content is rendered inside the shared dialog surface. */
val LocalAppDialogContext = staticCompositionLocalOf { false }

internal fun isLiquidGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU
}

internal fun resolveGlassMaterialSpec(
    preference: GlassMaterialPreference,
    darkTheme: Boolean,
    tuning: GlassMaterialTuningState = GlassMaterialTuningState()
): GlassMaterialSpec {
    if (preference == GlassMaterialPreference.Off) {
        return GlassMaterialSpec.Disabled
    }
    val card = GlassEffectStyle(
        blurRadius = 8.dp,
        refractionHeight = 16.dp,
        refractionAmount = 32.dp,
        surfaceAlpha = if (darkTheme) 0.48f else 0.56f,
        saturation = 1.5f
    )
    val dialog = GlassEffectStyle(
        blurRadius = if (darkTheme) 12.dp else 18.dp,
        refractionHeight = 20.dp,
        refractionAmount = 40.dp,
        surfaceAlpha = if (darkTheme) 0.78f else 0.82f,
        brightness = if (darkTheme) 0.04f else 0.08f,
        saturation = 1.08f,
        highlightAlpha = 0.38f,
        depthEffect = true
    )
    val button = GlassEffectStyle(
        blurRadius = 2.dp,
        refractionHeight = 12.dp,
        refractionAmount = 24.dp,
        surfaceAlpha = 0.42f,
        saturation = 1.5f
    )
    val dialogButton = button.copy(surfaceAlpha = if (darkTheme) 0.16f else 0.13f)
    val primaryButton = button.copy(surfaceAlpha = 0.82f)
    val dialogPrimaryButton = button.copy(
        surfaceAlpha = if (darkTheme) 0.30f else 0.28f,
        saturation = 1.15f
    )
    val selected = button.copy(
        // Selection is a subtle wash. Dialog options may still provide a
        // denser explicit fill, while this token keeps row/chip selection calm.
        surfaceAlpha = if (darkTheme) 0.16f else 0.13f,
        refractionHeight = 10.dp,
        refractionAmount = 14.dp,
        saturation = 1.15f
    )
    val bottomBar = GlassEffectStyle(
        blurRadius = 8.dp,
        refractionHeight = 24.dp,
        refractionAmount = 24.dp,
        surfaceAlpha = 0.4f,
        saturation = 1.5f
    )
    fun tuned(role: GlassMaterialRole, fallback: GlassEffectStyle): GlassEffectStyle {
        return tuning.overrideFor(role)?.applyTo(fallback) ?: fallback.normalized()
    }
    return GlassMaterialSpec(
        enabled = true,
        card = tuned(GlassMaterialRole.Card, card),
        dialog = tuned(GlassMaterialRole.Dialog, dialog),
        button = tuned(GlassMaterialRole.Button, button),
        dialogButton = tuned(GlassMaterialRole.DialogButton, dialogButton),
        primaryButton = tuned(GlassMaterialRole.PrimaryButton, primaryButton),
        dialogPrimaryButton = tuned(GlassMaterialRole.DialogPrimaryButton, dialogPrimaryButton),
        selected = tuned(GlassMaterialRole.Selected, selected),
        bottomBar = tuned(GlassMaterialRole.BottomBar, bottomBar)
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
    val tuning = LocalGlassMaterialTuning.current
    val spec = resolveGlassMaterialSpec(
        preference = effectivePreference,
        darkTheme = darkTheme,
        tuning = tuning
    )
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
    val adaptiveEnabled = glassEnabled && LocalGlassMaterialTuning.current.adaptiveLuminance
    val adaptiveLuminanceState = LocalGlassAdaptiveLuminance.current
    val solidColor = solidBackgroundColor ?: MaterialTheme.colorScheme.background
    val imageOverlayColor = if (darkTheme) {
        Color(0xFF24262B).copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0.40f)
    }
    val imageData = remember(background, foregroundKey, glassEnabled) {
        resolveEffectiveBackgroundImageData(background, foregroundKey, glassEnabled)
    }
    val randomOnline = background.mode == AppBackgroundMode.RandomOnlineImage
    var displayedImageData by remember { mutableStateOf<String?>(null) }
    var pendingImageData by remember { mutableStateOf<String?>(null) }
    var pendingImageReady by remember { mutableStateOf(false) }
    var startCrossfade by remember { mutableStateOf(false) }
    var displayedLuminanceSource by remember { mutableStateOf<GlassImageLuminanceSource?>(null) }
    var pendingLuminanceSource by remember { mutableStateOf<GlassImageLuminanceSource?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

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
                displayedLuminanceSource = null
                pendingLuminanceSource = null
            }

            imageData == displayedImageData -> {
                pendingImageData = null
                pendingImageReady = false
                startCrossfade = false
                pendingLuminanceSource = null
            }

            else -> {
                pendingImageData = imageData
                pendingImageReady = false
                startCrossfade = false
                pendingLuminanceSource = null
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
            displayedLuminanceSource = pendingLuminanceSource
            pendingImageData = null
            pendingImageReady = false
            pendingLuminanceSource = null
            startCrossfade = false
        } else if (candidate != displayedImageData) {
            startCrossfade = true
        }
    }

    LaunchedEffect(crossfadeProgress) {
        val candidate = pendingImageData
        if (startCrossfade && candidate != null && crossfadeProgress >= 0.999f) {
            displayedImageData = candidate
            displayedLuminanceSource = pendingLuminanceSource
            pendingImageData = null
            pendingImageReady = false
            pendingLuminanceSource = null
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
    val luminanceGrid = remember(
        adaptiveEnabled,
        viewportSize,
        displayedLuminanceSource,
        hasLoadedImage,
        solidColor,
        imageOverlayColor
    ) {
        if (!adaptiveEnabled) {
            null
        } else if (hasLoadedImage && displayedLuminanceSource != null) {
            GlassLuminanceGrid.image(
                viewportSize = viewportSize,
                source = displayedLuminanceSource!!,
                overlay = imageOverlayColor
            )
        } else {
            GlassLuminanceGrid.solid(
                viewportSize = viewportSize.takeUnless { it == IntSize.Zero } ?: IntSize(1, 1),
                color = solidColor
            )
        }
    }

    LaunchedEffect(luminanceGrid) {
        luminanceGrid?.let(adaptiveLuminanceState::update)
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .fillMaxSize()
            .background(solidColor)
    ) {
        if (displayedRequest != null && displayedData != null) {
            AsyncImage(
                model = displayedRequest,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(displayedAlpha),
                contentScale = ContentScale.Crop,
                onSuccess = { success ->
                    if (displayedImageData == displayedData) {
                        displayedLuminanceSource = GlassImageLuminanceSource.from(
                            success.result.drawable
                        )
                    }
                },
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
                onSuccess = { success ->
                    if (pendingImageData == pendingData) {
                        pendingLuminanceSource = GlassImageLuminanceSource.from(
                            success.result.drawable
                        )
                        pendingImageReady = true
                    }
                },
                onError = {
                    if (pendingImageData == pendingData) {
                        pendingImageData = null
                        pendingImageReady = false
                        pendingLuminanceSource = null
                        startCrossfade = false
                    }
                }
            )
        }
        if (hasLoadedImage) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(imageOverlayColor)
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
