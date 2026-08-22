package com.example.danmuapiapp.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.danmuapiapp.ui.theme.DialogColorTokens
import com.example.danmuapiapp.ui.theme.LocalAppDarkTheme
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Layout contract for the structured application dialogs. */
enum class AppDialogStyle {
    Confirm,
    Form,
    Selection,
    Status
}

/** Semantic intent used by the leading icon and assistive content. */
enum class AppDialogTone {
    Neutral,
    Brand,
    Success,
    Warning,
    Danger,
    Info
}

private class AppDialogEntry(
    val onDismissRequest: () -> Unit,
    val maxWidth: () -> Dp,
    val modifier: () -> Modifier,
    val content: @Composable () -> Unit
)

private class AppDialogHostState {
    val entries = mutableStateListOf<AppDialogEntry>()

    fun attach(entry: AppDialogEntry) {
        if (entry !in entries) entries += entry
    }

    fun detach(entry: AppDialogEntry) {
        entries -= entry
    }
}

private val LocalAppDialogHost = staticCompositionLocalOf<AppDialogHostState?> { null }

/** Keeps liquid dialogs in the same render tree as the content they refract. */
@Composable
fun AppDialogHost(content: @Composable () -> Unit) {
    val state = remember { AppDialogHostState() }
    val spec = LocalGlassMaterial.current
    val backdrop = rememberLayerBackdrop()

    CompositionLocalProvider(LocalAppDialogHost provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (spec.enabled) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        }
                    )
            ) {
                content()
            }

            if (spec.enabled) {
                state.entries.lastOrNull()?.let { entry ->
                    AppLiquidDialogOverlay(entry = entry, backdrop = backdrop)
                }
            }
        }
    }
}

private data class DialogTonePalette(
    val foreground: Color,
    val container: Color
)

@Composable
private fun dialogTonePalette(tone: AppDialogTone): DialogTonePalette {
    val colors = MaterialTheme.colorScheme
    val dark = LocalAppDarkTheme.current
    return when (tone) {
        AppDialogTone.Neutral -> DialogTonePalette(
            foreground = colors.onSurfaceVariant,
            container = colors.surfaceContainerHighest
        )

        AppDialogTone.Brand -> DialogTonePalette(
            foreground = colors.primary,
            container = colors.primaryContainer
        )

        AppDialogTone.Success -> if (dark) {
            DialogTonePalette(
                Color(DialogColorTokens.DARK_SUCCESS),
                Color(DialogColorTokens.DARK_SUCCESS_CONTAINER)
            )
        } else {
            DialogTonePalette(
                Color(DialogColorTokens.LIGHT_SUCCESS),
                Color(DialogColorTokens.LIGHT_SUCCESS_CONTAINER)
            )
        }

        AppDialogTone.Warning -> if (dark) {
            DialogTonePalette(
                Color(DialogColorTokens.DARK_WARNING),
                Color(DialogColorTokens.DARK_WARNING_CONTAINER)
            )
        } else {
            DialogTonePalette(
                Color(DialogColorTokens.LIGHT_WARNING),
                Color(DialogColorTokens.LIGHT_WARNING_CONTAINER)
            )
        }

        AppDialogTone.Danger -> DialogTonePalette(
            foreground = colors.error,
            container = colors.errorContainer
        )

        AppDialogTone.Info -> DialogTonePalette(
            foreground = colors.onPrimaryContainer,
            container = colors.primaryContainer
        )
    }
}

/**
 * Structured dialog with one scroll owner and a fixed action area.
 *
 * Content passed to [text] must not add another vertical scroll container while
 * [scrollContent] is true. Disable it for a lazy body, or use [AppModalPanel] for
 * a fully custom layout.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AppDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppDialogStyle = AppDialogStyle.Form,
    tone: AppDialogTone = AppDialogTone.Brand,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    scrollContent: Boolean = true,
    text: (@Composable ColumnScope.() -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val maxWidth = when (style) {
        AppDialogStyle.Confirm -> 440.dp
        AppDialogStyle.Form,
        AppDialogStyle.Selection,
        AppDialogStyle.Status -> 600.dp
    }

    AppDialogFrame(
        onDismissRequest = onDismissRequest,
        maxWidth = maxWidth,
        modifier = modifier
    ) {
        val palette = dialogTonePalette(tone)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (icon != null || title != null || supportingText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = palette.container,
                            contentColor = palette.foreground
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CompositionLocalProvider(
                                    LocalContentColor provides palette.foreground,
                                    content = icon
                                )
                            }
                        }
                    }
                    if (title != null || supportingText != null) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (title != null) {
                                ProvideTextStyle(
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    content = title
                                )
                            }
                            if (supportingText != null) {
                                CompositionLocalProvider(
                                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    ProvideTextStyle(
                                        MaterialTheme.typography.bodySmall,
                                        content = supportingText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (text != null) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    val bodyModifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .let { base ->
                            if (scrollContent) {
                                base.verticalScroll(rememberScrollState())
                            } else {
                                base
                            }
                        }
                    Column(
                        modifier = bodyModifier,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = text
                    )
                }
            }

            if (dismissButton != null || confirmButton != null || actions != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dismissButton?.invoke()
                    confirmButton?.invoke()
                    actions?.invoke()
                }
            }
        }
    }
}

/**
 * Bounded dialog surface for rich content such as previews and dashboards.
 * The caller owns the internal layout and any scrolling.
 */
@Composable
fun AppModalPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    expanded: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    AppDialogFrame(
        onDismissRequest = onDismissRequest,
        maxWidth = maxWidth
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxHeight() else Modifier)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

@Composable
private fun AppDialogFrame(
    onDismissRequest: () -> Unit,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val host = LocalAppDialogHost.current
    if (LocalGlassMaterial.current.enabled && host != null) {
        AppDialogPortal(
            host = host,
            onDismissRequest = onDismissRequest,
            maxWidth = maxWidth,
            modifier = modifier,
            content = content
        )
        return
    }

    val parentColors = MaterialTheme.colorScheme
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val dialogColors = dialogColorScheme(parentColors, LocalAppDarkTheme.current)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        MaterialTheme(
            colorScheme = dialogColors,
            typography = parentTypography,
            shapes = parentShapes
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    ),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun AppDialogPortal(
    host: AppDialogHostState,
    onDismissRequest: () -> Unit,
    maxWidth: Dp,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberUpdatedState(onDismissRequest)
    val maxWidthState = rememberUpdatedState(maxWidth)
    val modifierState = rememberUpdatedState(modifier)
    val contentState = rememberUpdatedState(content)
    val entry = remember(host) {
        AppDialogEntry(
            onDismissRequest = { dismissState.value.invoke() },
            maxWidth = { maxWidthState.value },
            modifier = { modifierState.value },
            content = { contentState.value.invoke() }
        )
    }

    DisposableEffect(host, entry) {
        host.attach(entry)
        onDispose { host.detach(entry) }
    }
}

@Composable
private fun AppLiquidDialogOverlay(
    entry: AppDialogEntry,
    backdrop: Backdrop
) {
    val parentColors = MaterialTheme.colorScheme
    val parentTypography = MaterialTheme.typography
    val parentShapes = MaterialTheme.shapes
    val darkTheme = LocalAppDarkTheme.current
    val dialogColors = dialogColorScheme(parentColors, darkTheme)
    val dimColor = if (darkTheme) {
        Color(0xFF121212).copy(alpha = 0.56f)
    } else {
        Color(0xFF29293A).copy(alpha = 0.23f)
    }

    BackHandler(onBack = entry.onDismissRequest)

    MaterialTheme(
        colorScheme = dialogColors,
        typography = parentTypography,
        shapes = parentShapes
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(dimColor)
                .pointerInput(entry) {
                    detectTapGestures { entry.onDismissRequest() }
                }
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AppGlassSurface(
                modifier = entry.modifier()
                    .widthIn(max = entry.maxWidth())
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .pointerInput(entry) {
                        detectTapGestures { /* Consume taps inside the modal surface. */ }
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                ),
                role = AppGlassSurfaceRole.Dialog,
                backdropOverride = backdrop,
            ) {
                CompositionLocalProvider(LocalGlassBackgroundBackdrop provides backdrop) {
                    entry.content()
                }
            }
        }
    }
}

private fun dialogColorScheme(base: ColorScheme, dark: Boolean): ColorScheme {
    return if (dark) {
        base.copy(
            primary = Color(DialogColorTokens.DARK_PRIMARY),
            onPrimary = Color(DialogColorTokens.DARK_ON_PRIMARY),
            primaryContainer = Color(DialogColorTokens.DARK_PRIMARY_CONTAINER),
            onPrimaryContainer = Color(DialogColorTokens.DARK_ON_PRIMARY_CONTAINER),
            surface = Color(DialogColorTokens.DARK_DIALOG),
            onSurface = Color(DialogColorTokens.DARK_TEXT_PRIMARY),
            surfaceVariant = Color(DialogColorTokens.DARK_SURFACE_ACTIVE),
            onSurfaceVariant = Color(DialogColorTokens.DARK_TEXT_SECONDARY),
            surfaceContainerLowest = Color(DialogColorTokens.DARK_SURFACE_LOWEST),
            surfaceContainerLow = Color(DialogColorTokens.DARK_DIALOG),
            surfaceContainer = Color(DialogColorTokens.DARK_SURFACE_CONTAINER),
            surfaceContainerHigh = Color(DialogColorTokens.DARK_SURFACE_HIGH),
            surfaceContainerHighest = Color(DialogColorTokens.DARK_SURFACE_ACTIVE),
            outline = Color(DialogColorTokens.DARK_OUTLINE),
            outlineVariant = Color(DialogColorTokens.DARK_OUTLINE_VARIANT),
            error = Color(DialogColorTokens.DARK_ERROR),
            onError = Color(DialogColorTokens.DARK_ON_ERROR),
            errorContainer = Color(DialogColorTokens.DARK_ERROR_CONTAINER),
            onErrorContainer = Color(DialogColorTokens.DARK_ON_ERROR_CONTAINER)
        )
    } else {
        base.copy(
            primary = Color(DialogColorTokens.LIGHT_PRIMARY),
            onPrimary = Color(DialogColorTokens.LIGHT_ON_PRIMARY),
            primaryContainer = Color(DialogColorTokens.LIGHT_PRIMARY_CONTAINER),
            onPrimaryContainer = Color(DialogColorTokens.LIGHT_ON_PRIMARY_CONTAINER),
            surface = Color(DialogColorTokens.LIGHT_DIALOG),
            onSurface = Color(DialogColorTokens.LIGHT_TEXT_PRIMARY),
            surfaceVariant = Color(DialogColorTokens.LIGHT_SURFACE_ACTIVE),
            onSurfaceVariant = Color(DialogColorTokens.LIGHT_TEXT_SECONDARY),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(DialogColorTokens.LIGHT_DIALOG),
            surfaceContainer = Color(DialogColorTokens.LIGHT_SURFACE_CONTAINER),
            surfaceContainerHigh = Color(DialogColorTokens.LIGHT_SURFACE_HIGH),
            surfaceContainerHighest = Color(DialogColorTokens.LIGHT_SURFACE_ACTIVE),
            outline = Color(DialogColorTokens.LIGHT_OUTLINE),
            outlineVariant = Color(DialogColorTokens.LIGHT_OUTLINE_VARIANT),
            error = Color(DialogColorTokens.LIGHT_ERROR),
            onError = Color(DialogColorTokens.LIGHT_ON_ERROR),
            errorContainer = Color(DialogColorTokens.LIGHT_ERROR_CONTAINER),
            onErrorContainer = Color(DialogColorTokens.LIGHT_ON_ERROR_CONTAINER)
        )
    }
}
