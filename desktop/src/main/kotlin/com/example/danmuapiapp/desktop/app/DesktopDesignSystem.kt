package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.danmuapiapp.desktop.runtime.ServicePhase

/**
 * Shared dimensions and shape values for the desktop application.
 * Keep page-level layout decisions in this object instead of scattering literals.
 */
object DesktopTokens {
    val SidebarWidth: Dp = 232.dp
    val SidebarCollapsedWidth: Dp = 72.dp
    val SidebarHorizontalInset: Dp = 12.dp
    val SidebarContentInset: Dp = 8.dp
    val SidebarIconSlot: Dp = 24.dp
    val SidebarHeaderHeight: Dp = 68.dp
    val TopBarHeight: Dp = 72.dp
    val StatusStripHeight: Dp = 40.dp
    val PagePadding: Dp = 24.dp
    val PageGap: Dp = 16.dp
    val CardPadding: Dp = 20.dp
    val CompactCardPadding: Dp = 16.dp
    val CardCornerRadius: Dp = 16.dp
    val ItemCornerRadius: Dp = 10.dp
    val ControlHeight: Dp = 40.dp
    val RowVerticalPadding: Dp = 8.dp
    val InfoLabelWidth: Dp = 112.dp
    val DividerThickness: Dp = 1.dp
    val OverviewSingleColumnBreakpoint: Dp = 980.dp
    val SidebarCollapseBreakpoint: Dp = 1120.dp

    val CardShape: Shape = RoundedCornerShape(CardCornerRadius)
    val ItemShape: Shape = RoundedCornerShape(ItemCornerRadius)
    val PillShape: Shape = RoundedCornerShape(999.dp)
}

/** A semantic status color triplet used by badges, banners, and indicators. */
@Immutable
data class DesktopStatusColors(
    val content: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Desktop-specific palette. Material colors are exposed separately from status colors
 * because Material 3 has no built-in success or warning roles.
 */
@Immutable
data class DesktopThemePalette(
    val primary: Color = Color(0xFF315BC9),
    val onPrimary: Color = Color.White,
    val primaryContainer: Color = Color(0xFFDDE5FF),
    val onPrimaryContainer: Color = Color(0xFF00174B),
    val secondary: Color = Color(0xFF58627A),
    val onSecondary: Color = Color.White,
    val secondaryContainer: Color = Color(0xFFDDE2F9),
    val onSecondaryContainer: Color = Color(0xFF151B30),
    val tertiary: Color = Color(0xFF76546D),
    val onTertiary: Color = Color.White,
    val tertiaryContainer: Color = Color(0xFFFFD8F1),
    val onTertiaryContainer: Color = Color(0xFF2C1227),
    val background: Color = Color(0xFFF9F9FC),
    val onBackground: Color = Color(0xFF1A1B20),
    val surface: Color = Color(0xFFF9F9FC),
    val onSurface: Color = Color(0xFF1A1B20),
    val surfaceVariant: Color = Color(0xFFE1E2E9),
    val onSurfaceVariant: Color = Color(0xFF44464F),
    val outline: Color = Color(0xFF747780),
    val outlineVariant: Color = Color(0xFFC5C6CD),
    val error: Color = Color(0xFFBA1A1A),
    val onError: Color = Color.White,
    val errorContainer: Color = Color(0xFFFFDAD6),
    val onErrorContainer: Color = Color(0xFF410002),
    val success: DesktopStatusColors = DesktopStatusColors(
        content = Color(0xFF1B7F4D),
        container = Color(0xFFDDF6E7),
        onContainer = Color(0xFF07391F),
    ),
    val warning: DesktopStatusColors = DesktopStatusColors(
        content = Color(0xFF9A5B00),
        container = Color(0xFFFFEBC8),
        onContainer = Color(0xFF321A00),
    ),
    val info: DesktopStatusColors = DesktopStatusColors(
        content = Color(0xFF1769A6),
        container = Color(0xFFDCEEFF),
        onContainer = Color(0xFF001D35),
    ),
    val neutral: DesktopStatusColors = DesktopStatusColors(
        content = Color(0xFF62646C),
        container = Color(0xFFE9E9EE),
        onContainer = Color(0xFF1B1B20),
    ),
) {
    fun colorsFor(status: DesktopStatus): DesktopStatusColors = when (status) {
        DesktopStatus.Success -> success
        DesktopStatus.Warning -> warning
        DesktopStatus.Error -> DesktopStatusColors(error, errorContainer, onErrorContainer)
        DesktopStatus.Info, DesktopStatus.Loading -> info
        DesktopStatus.Neutral -> neutral
    }

    companion object {
        val Light: DesktopThemePalette = DesktopThemePalette()

        val Dark: DesktopThemePalette = Light.copy(
            primary = Color(0xFFB8C7FF),
            onPrimary = Color(0xFF092C72),
            primaryContainer = Color(0xFF174397),
            onPrimaryContainer = Color(0xFFDDE5FF),
            secondary = Color(0xFFC0C6DD),
            onSecondary = Color(0xFF293044),
            secondaryContainer = Color(0xFF40475C),
            onSecondaryContainer = Color(0xFFDDE2F9),
            tertiary = Color(0xFFE5BBD7),
            onTertiary = Color(0xFF43263D),
            tertiaryContainer = Color(0xFF5D3C55),
            onTertiaryContainer = Color(0xFFFFD8F1),
            background = Color(0xFF111318),
            onBackground = Color(0xFFE3E2E9),
            surface = Color(0xFF111318),
            onSurface = Color(0xFFE3E2E9),
            surfaceVariant = Color(0xFF44464F),
            onSurfaceVariant = Color(0xFFC5C6D0),
            outline = Color(0xFF8E9099),
            outlineVariant = Color(0xFF44464F),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            success = DesktopStatusColors(
                content = Color(0xFF7BE2A7),
                container = Color(0xFF123B26),
                onContainer = Color(0xFFB8F3C9),
            ),
            warning = DesktopStatusColors(
                content = Color(0xFFFFB95E),
                container = Color(0xFF4D2C00),
                onContainer = Color(0xFFFFDDB1),
            ),
            info = DesktopStatusColors(
                content = Color(0xFF91CCFF),
                container = Color(0xFF004A70),
                onContainer = Color(0xFFC9E6FF),
            ),
            neutral = DesktopStatusColors(
                content = Color(0xFFC5C6D0),
                container = Color(0xFF2B2D33),
                onContainer = Color(0xFFE3E2E9),
            ),
        )

        val DefaultLight: DesktopThemePalette get() = Light
        val DefaultDark: DesktopThemePalette get() = Dark
    }
}

/** States shared by status badges and other stateful desktop controls. */
enum class DesktopStatus(val defaultLabel: String) {
    Neutral("未运行"),
    Info("信息"),
    Loading("处理中"),
    Success("正常"),
    Warning("注意"),
    Error("失败"),
}

/** Maps runtime state to the design-system status semantic. */
fun ServicePhase.toDesktopStatus(): DesktopStatus = when (this) {
    ServicePhase.Running -> DesktopStatus.Success
    ServicePhase.Failed -> DesktopStatus.Error
    ServicePhase.CoreSetupRequired -> DesktopStatus.Warning
    ServicePhase.Preparing,
    ServicePhase.Starting,
    ServicePhase.Stopping,
    -> DesktopStatus.Loading
    ServicePhase.Stopped -> DesktopStatus.Neutral
}

val LocalDesktopThemePalette = staticCompositionLocalOf { DesktopThemePalette.Light }

/** Returns the Material 3 scheme corresponding to the selected desktop palette. */
fun desktopColorScheme(
    darkTheme: Boolean = false,
    palette: DesktopThemePalette = if (darkTheme) DesktopThemePalette.Dark else DesktopThemePalette.Light,
): ColorScheme = if (darkTheme) {
    androidx.compose.material3.darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer,
        background = palette.background,
        onBackground = palette.onBackground,
        surface = palette.surface,
        onSurface = palette.onSurface,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.onSurfaceVariant,
        outline = palette.outline,
        outlineVariant = palette.outlineVariant,
        error = palette.error,
        onError = palette.onError,
        errorContainer = palette.errorContainer,
        onErrorContainer = palette.onErrorContainer,
    )
} else {
    androidx.compose.material3.lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer,
        background = palette.background,
        onBackground = palette.onBackground,
        surface = palette.surface,
        onSurface = palette.onSurface,
        surfaceVariant = palette.surfaceVariant,
        onSurfaceVariant = palette.onSurfaceVariant,
        outline = palette.outline,
        outlineVariant = palette.outlineVariant,
        error = palette.error,
        onError = palette.onError,
        errorContainer = palette.errorContainer,
        onErrorContainer = palette.onErrorContainer,
    )
}

/** Root theme for all desktop pages. */
@Composable
fun DesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: DesktopThemePalette = if (darkTheme) DesktopThemePalette.Dark else DesktopThemePalette.Light,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDesktopThemePalette provides palette) {
        MaterialTheme(
            colorScheme = desktopColorScheme(darkTheme = darkTheme, palette = palette),
            content = content,
        )
    }
}

@Composable
fun DesktopPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    status: DesktopStatus? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadingContent != null) leadingContent()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (status != null) DesktopStatusBadge(status)
        if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun DesktopPageScaffold(
    title: String,
    subtitle: String? = null,
    status: DesktopStatus? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // The header is part of the page canvas rather than a second colored panel. This keeps
        // the window chrome visually continuous while cards and the sidebar provide hierarchy.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopTokens.TopBarHeight)
                .padding(horizontal = DesktopTokens.PagePadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            DesktopPageHeader(
                title = title,
                subtitle = subtitle,
                status = status,
                leadingContent = leadingContent,
                actions = actions,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}

@Composable
fun DesktopSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = DesktopTokens.CardShape,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Surface(
            modifier = modifier,
            color = color,
            contentColor = contentColor,
            shape = shape,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            color = color,
            contentColor = contentColor,
            shape = shape,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = content,
        )
    }
}

@Composable
fun DesktopSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    DesktopSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(DesktopTokens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (trailingContent != null) {
                    Spacer(Modifier.weight(1f))
                    trailingContent()
                }
            }
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun DesktopMetricCard(
    label: String,
    value: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    status: DesktopStatus? = null,
    icon: DesktopIconGlyph? = null,
    onClick: (() -> Unit)? = null,
) {
    DesktopSurface(
        modifier = modifier,
        onClick = onClick,
        enabled = onClick != null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(DesktopTokens.CardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    DesktopIcon(icon, modifier = Modifier.padding(end = 8.dp), size = 18.sp)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status != null) {
                    Spacer(Modifier.weight(1f))
                    DesktopStatusBadge(status, compact = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DesktopStatusBadge(
    status: DesktopStatus,
    label: String = status.defaultLabel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalDesktopThemePalette.current.colorsFor(status)
    Surface(
        modifier = modifier,
        shape = DesktopTokens.PillShape,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(if (compact) 6.dp else 7.dp)
                    .height(if (compact) 6.dp else 7.dp)
                    .background(colors.content, DesktopTokens.PillShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun DesktopInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    monospace: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingContent: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = DesktopTokens.RowVerticalPadding),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leadingContent != null) leadingContent()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(DesktopTokens.InfoLabelWidth),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) action()
    }
}

@Composable
fun DesktopDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DesktopTokens.DividerThickness)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    )
}

@Composable
fun DesktopEmptyState(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    icon: DesktopIconGlyph? = DesktopIcons.Empty,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(DesktopTokens.CardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) DesktopIcon(icon, size = 28.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun DesktopRestartBanner(
    message: String = "部分设置将在重启应用后生效。",
    modifier: Modifier = Modifier,
    title: String = "需要重启",
    actionLabel: String = "立即重启",
    onRestart: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DesktopTokens.ItemShape,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesktopIcon(DesktopIcons.Restart, tint = scheme.onPrimaryContainer, size = 20.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
            if (onRestart != null) {
                DesktopActionButton(
                    label = actionLabel,
                    onClick = onRestart,
                    style = DesktopActionButtonStyle.Tonal,
                )
            }
        }
    }
}

/** Option model for [DesktopSegmentedChoice]. */
data class DesktopSegmentedOption<T>(val value: T, val label: String)

@Composable
fun <T> DesktopSegmentedChoice(
    options: List<DesktopSegmentedOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = DesktopTokens.ItemShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                Surface(
                    onClick = { onSelected(option.value) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = DesktopTokens.ItemShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopSettingRow(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    control: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = DesktopTokens.RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.6f),
                )
            }
        }
        control()
    }
}

enum class DesktopActionButtonStyle { Primary, Tonal, Outlined, Destructive }

@Composable
fun DesktopActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: DesktopActionButtonStyle = DesktopActionButtonStyle.Primary,
    icon: DesktopIconGlyph? = null,
) {
    val contentColor = when (style) {
        DesktopActionButtonStyle.Primary -> MaterialTheme.colorScheme.onPrimary
        DesktopActionButtonStyle.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
        DesktopActionButtonStyle.Outlined -> MaterialTheme.colorScheme.onSurface
        DesktopActionButtonStyle.Destructive -> MaterialTheme.colorScheme.onError
    }
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            DesktopIcon(icon, tint = contentColor, size = 16.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
    when (style) {
        DesktopActionButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(contentColor = contentColor),
            content = content,
        )
        DesktopActionButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.filledTonalButtonColors(contentColor = contentColor),
            content = content,
        )
        DesktopActionButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            content = content,
        )
        DesktopActionButtonStyle.Destructive -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            content = content,
        )
    }
}

private val LocalDesktopButtonContentColor = staticCompositionLocalOf { Color.Unspecified }
