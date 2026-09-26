package com.example.danmuapiapp.ui.component.liquid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalAppDarkTheme
import com.example.danmuapiapp.ui.theme.LocalAppDialogContext
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.example.danmuapiapp.ui.theme.GlassMaterialRole
import com.kyant.shapes.Capsule

@Composable
private fun defaultControlSurfaceColor(dialogAccent: Color = Color.Unspecified): Color {
    val glassAvailable = LocalGlassMaterial.current.enabled &&
        LocalGlassBackgroundBackdrop.current != null
    val dialogContext = LocalAppDialogContext.current
    val colors = MaterialTheme.colorScheme
    if (dialogContext && glassAvailable) {
        val darkTheme = LocalAppDarkTheme.current
        val alpha = if (dialogAccent.isSpecified) {
            if (darkTheme) 0.30f else 0.26f
        } else {
            // Match the selection wash used by the cache list. The border below
            // supplies the small amount of edge definition a flat dialog control needs.
            if (darkTheme) 0.16f else 0.13f
        }
        return (if (dialogAccent.isSpecified) dialogAccent else colors.primary)
            .copy(alpha = alpha)
    }
    val base = if (dialogContext) {
        colors.surfaceContainerHigh
    } else {
        colors.surfaceContainerHighest
    }
    val alpha = when {
        !glassAvailable -> 1f
        dialogContext -> 0.72f
        else -> 0.42f
    }
    return base.copy(alpha = alpha)
}

@Composable
fun AppGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = if (LocalAppDialogContext.current) 14.dp else 16.dp
    ),
    height: Dp = if (LocalAppDialogContext.current) 44.dp else 48.dp,
    shape: Shape = RoundedCornerShape(if (LocalAppDialogContext.current) 12.dp else 14.dp),
    materialRole: GlassMaterialRole? = null,
    content: @Composable RowScope.() -> Unit
) {
    val glassRequested = LocalGlassMaterial.current.enabled
    val dialogContext = LocalAppDialogContext.current
    val dialogDarkTheme = LocalAppDarkTheme.current
    val resolvedContentColor = when {
        contentColor.isSpecified -> contentColor
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
    if (!glassRequested) {
        // 关闭液态玻璃时恢复传统外观：所有按钮都有实底，并按底色自动选文字颜色，
        // 避免出现“只有文字没有背景”或底色与文字太接近的情况。
        val baseColor = when {
            surfaceColor.isSpecified -> surfaceColor
            tint.isSpecified -> tint
            else -> null
        }
        val container = baseColor?.compositeOver(MaterialTheme.colorScheme.surface)
            ?: MaterialTheme.colorScheme.surfaceContainerHighest
        val legacyContentColor = if (contentColor.isSpecified) {
            contentColor
        } else {
            val schemeContent = baseColor?.let { contentColorFor(it) }
            when {
                schemeContent != null && schemeContent.isSpecified -> schemeContent
                baseColor == null -> MaterialTheme.colorScheme.onSurface
                container.luminance() > 0.5f -> Color.Black
                else -> Color.White
            }
        }
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.height(height),
            enabled = enabled,
            shape = shape,
            border = if (borderColor.isSpecified) BorderStroke(1.dp, borderColor) else null,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = container,
                contentColor = legacyContentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            ),
            contentPadding = contentPadding,
            content = content
        )
        return
    }

    val resolvedBorderColor = when {
        !dialogContext -> Color.Unspecified
        borderColor.isSpecified -> borderColor
        tint.isSpecified -> tint.copy(alpha = if (dialogDarkTheme) 0.34f else 0.28f)
        surfaceColor.isSpecified -> surfaceColor.copy(alpha = if (dialogDarkTheme) 0.30f else 0.24f)
        else -> MaterialTheme.colorScheme.primary.copy(
            alpha = if (dialogDarkTheme) 0.28f else 0.22f
        )
    }

    AppLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        surfaceColor = if (surfaceColor.isSpecified) {
            surfaceColor
        } else {
            defaultControlSurfaceColor(dialogAccent = tint)
        },
        border = if (resolvedBorderColor.isSpecified) {
            BorderStroke(
                width = 0.75.dp,
                color = resolvedBorderColor.copy(
                    alpha = if (enabled) resolvedBorderColor.alpha else resolvedBorderColor.alpha * 0.45f
                )
            )
        } else {
            null
        },
        contentColor = resolvedContentColor,
        contentPadding = contentPadding,
        height = height,
        shape = shape,
        materialRole = materialRole ?: if (dialogContext) {
            GlassMaterialRole.DialogButton
        } else {
            GlassMaterialRole.Button
        },
        content = content
    )
}

/** A high-emphasis glass action with a readable container/content color pair. */
@Composable
fun AppGlassPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = if (LocalAppDialogContext.current) 14.dp else 16.dp
    ),
    height: Dp = if (LocalAppDialogContext.current) 44.dp else 48.dp,
    shape: Shape = RoundedCornerShape(if (LocalAppDialogContext.current) 12.dp else 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val glassRequested = LocalGlassMaterial.current.enabled
    val glassEnabled = glassRequested &&
        LocalGlassBackgroundBackdrop.current != null
    val dialogContext = LocalAppDialogContext.current
    val dialogDarkTheme = LocalAppDarkTheme.current
    if (!glassRequested) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            ),
            content = content
        )
        return
    }
    AppGlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = if (glassEnabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Unspecified
        },
        surfaceColor = if (glassEnabled) {
            if (dialogContext) {
                MaterialTheme.colorScheme.primary.copy(
                    alpha = if (dialogDarkTheme) 0.30f else 0.28f
                )
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            }
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        borderColor = if (dialogContext && glassEnabled) {
            MaterialTheme.colorScheme.primary.copy(
                alpha = if (dialogDarkTheme) 0.46f else 0.38f
            )
        } else {
            Color.Unspecified
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        contentPadding = contentPadding,
        height = height,
        shape = shape,
        materialRole = if (dialogContext) {
            GlassMaterialRole.DialogPrimaryButton
        } else {
            GlassMaterialRole.PrimaryButton
        },
        content = content
    )
}

/** A readable destructive glass action using the Material error container pair. */
@Composable
fun AppGlassDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = if (LocalAppDialogContext.current) 14.dp else 16.dp
    ),
    height: Dp = if (LocalAppDialogContext.current) 44.dp else 48.dp,
    shape: Shape = RoundedCornerShape(if (LocalAppDialogContext.current) 12.dp else 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val glassRequested = LocalGlassMaterial.current.enabled
    val glassEnabled = glassRequested &&
        LocalGlassBackgroundBackdrop.current != null
    if (!glassRequested) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            ),
            content = content
        )
        return
    }
    AppGlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = if (glassEnabled) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        } else {
            Color.Unspecified
        },
        surfaceColor = if (glassEnabled) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        contentPadding = contentPadding,
        height = height,
        shape = shape,
        materialRole = if (LocalAppDialogContext.current) {
            GlassMaterialRole.DialogButton
        } else {
            GlassMaterialRole.Button
        },
        content = content
    )
}

@Composable
fun AppGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    },
    content: @Composable () -> Unit
) {
    val glassRequested = LocalGlassMaterial.current.enabled
    if (!glassRequested) {
        // 传统外观：带色图标按钮用实底，避免 0.3 透明度在浅色背景上看不见。
        val container = if (surfaceColor.isSpecified) {
            surfaceColor.compositeOver(MaterialTheme.colorScheme.surface)
        } else {
            Color.Unspecified
        }
        if (LocalAppDialogContext.current) {
            if (container.isSpecified) {
                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = modifier.size(size),
                    enabled = enabled,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = container,
                        contentColor = contentColor,
                        disabledContainerColor = container.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    ),
                    content = content
                )
            } else {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalContentColor provides contentColor
                ) {
                    IconButton(
                        onClick = onClick,
                        modifier = modifier.size(size),
                        enabled = enabled,
                        content = content
                    )
                }
            }
        } else {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier.size(size),
                enabled = enabled,
                colors = if (container.isSpecified) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = container,
                        contentColor = contentColor,
                        disabledContainerColor = container.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        contentColor = contentColor,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                },
                content = content
            )
        }
        return
    }
    AppLiquidButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        height = size,
        shape = CircleShape,
        materialRole = if (LocalAppDialogContext.current) {
            GlassMaterialRole.DialogButton
        } else {
            GlassMaterialRole.Button
        },
        surfaceColor = if (surfaceColor.isSpecified) {
            surfaceColor
        } else {
            defaultControlSurfaceColor(dialogAccent = Color.Unspecified)
        },
        contentColor = contentColor,
        contentPadding = PaddingValues(0.dp)
    ) {
        content()
    }
}

@Composable
fun AppGlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    colors: Any? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    if (!LocalGlassMaterial.current.enabled) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = accent.copy(alpha = 0.16f),
                selectedLabelColor = accent,
                selectedLeadingIconColor = accent,
                selectedTrailingIconColor = accent
            )
        )
        return
    }

    val dialogContext = LocalAppDialogContext.current
    val dialogDarkTheme = LocalAppDarkTheme.current
    AppLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = 36.dp,
        shape = Capsule(),
        materialRole = when {
            selected -> GlassMaterialRole.Selected
            dialogContext -> GlassMaterialRole.DialogButton
            else -> GlassMaterialRole.Button
        },
        surfaceColor = when {
            selected && dialogContext -> MaterialTheme.colorScheme.primary.copy(
                alpha = if (dialogDarkTheme) 0.16f else 0.13f
            )

            dialogContext -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            selected -> accent.copy(alpha = 0.46f)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f)
        },
        border = if (dialogContext) {
            BorderStroke(
                width = 0.75.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.32f else 0.14f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.30f else 0.14f)
                }
            )
        } else {
            null
        },
        contentColor = when {
            selected && dialogContext -> MaterialTheme.colorScheme.onPrimaryContainer
            selected -> accent
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        leadingIcon?.invoke()
        label()
        trailingIcon?.invoke()
    }
}

@Composable
fun AppGlassAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    if (!LocalGlassMaterial.current.enabled) {
        AssistChip(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon
        )
        return
    }

    AppLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = 36.dp,
        shape = Capsule(),
        materialRole = if (LocalAppDialogContext.current) {
            GlassMaterialRole.DialogButton
        } else {
            GlassMaterialRole.Button
        },
        tint = accent.copy(alpha = 0.12f),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        leadingIcon?.invoke()
        label()
        trailingIcon?.invoke()
    }
}
