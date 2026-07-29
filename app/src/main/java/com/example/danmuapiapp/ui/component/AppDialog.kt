package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

private data class DialogTonePalette(
    val foreground: Color,
    val container: Color
)

@Composable
private fun dialogTonePalette(tone: AppDialogTone): DialogTonePalette {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
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
            DialogTonePalette(Color(0xFF72D6A3), Color(0xFF173B2B))
        } else {
            DialogTonePalette(Color(0xFF176B45), Color(0xFFDDF4E7))
        }

        AppDialogTone.Warning -> if (dark) {
            DialogTonePalette(Color(0xFFFFC56F), Color(0xFF493108))
        } else {
            DialogTonePalette(Color(0xFF855100), Color(0xFFFFE9C5))
        }

        AppDialogTone.Danger -> DialogTonePalette(
            foreground = colors.error,
            container = colors.errorContainer
        )

        AppDialogTone.Info -> if (dark) {
            DialogTonePalette(Color(0xFF9CCBFF), Color(0xFF173A5C))
        } else {
            DialogTonePalette(Color(0xFF1B6098), Color(0xFFDCEEFF))
        }
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
    val palette = dialogTonePalette(tone)
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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
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
                tonalElevation = 2.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                ),
                content = content
            )
        }
    }
}
