package com.example.danmuapiapp.ui.component.liquid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.theme.LocalGlassBackgroundBackdrop
import com.example.danmuapiapp.ui.theme.LocalAppDialogContext
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial
import com.kyant.shapes.Capsule

@Composable
private fun defaultControlSurfaceColor(): Color {
    val glassAvailable = LocalGlassMaterial.current.enabled &&
        LocalGlassBackgroundBackdrop.current != null
    val dialogContext = LocalAppDialogContext.current
    val base = if (dialogContext) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
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
    surfaceColor: Color = defaultControlSurfaceColor(),
    contentColor: Color = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    },
    contentPadding: PaddingValues = PaddingValues(
        horizontal = if (LocalAppDialogContext.current) 14.dp else 16.dp
    ),
    height: Dp = if (LocalAppDialogContext.current) 44.dp else 48.dp,
    shape: Shape = RoundedCornerShape(if (LocalAppDialogContext.current) 12.dp else 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    AppLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        contentColor = contentColor,
        contentPadding = contentPadding,
        height = height,
        shape = shape,
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
    val glassEnabled = LocalGlassMaterial.current.enabled &&
        LocalGlassBackgroundBackdrop.current != null
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
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        },
        contentPadding = contentPadding,
        height = height,
        shape = shape,
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
    val glassEnabled = LocalGlassMaterial.current.enabled &&
        LocalGlassBackgroundBackdrop.current != null
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
        content = content
    )
}

@Composable
fun AppGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    surfaceColor: Color = defaultControlSurfaceColor(),
    contentColor: Color = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    },
    content: @Composable () -> Unit
) {
    AppLiquidButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        height = size,
        shape = CircleShape,
        surfaceColor = surfaceColor,
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
    AppLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = 36.dp,
        shape = Capsule(),
        surfaceColor = if (selected) {
            accent.copy(alpha = 0.46f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f)
        },
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
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
    AppLiquidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = 36.dp,
        shape = Capsule(),
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
