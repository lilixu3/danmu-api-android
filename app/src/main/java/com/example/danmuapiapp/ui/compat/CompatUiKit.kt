package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.danmuapiapp.ui.component.remoteFocusHighlight
import com.example.danmuapiapp.data.util.DeviceCompatMode

internal val LocalCompatLayout = staticCompositionLocalOf {
    CompatLayoutPolicy.resolve(800f, 360f, 1f)
}

/** A flat, lightweight palette independent of the normal interface's glass material. */
@Composable
internal fun CompatConsoleTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF92CFFF), onPrimary = Color(0xFF062640),
        primaryContainer = Color(0xFF123752), onPrimaryContainer = Color(0xFFD5EBFF),
        secondary = Color(0xFF6EDCC0), onSecondary = Color(0xFF00382C),
        secondaryContainer = Color(0xFF153B34), onSecondaryContainer = Color(0xFFA7F3DD),
        tertiary = Color(0xFFFFD18B), onTertiary = Color(0xFF402C09),
        background = Color(0xFF0B111B), onBackground = Color(0xFFE8EFF8),
        surface = Color(0xFF121C2A), onSurface = Color(0xFFE8EFF8),
        surfaceContainer = Color(0xFF121C2A), surfaceContainerLow = Color(0xFF0E1723),
        surfaceContainerHigh = Color(0xFF1C2A3B), surfaceContainerHighest = Color(0xFF25374A),
        surfaceVariant = Color(0xFF1C2A3B), onSurfaceVariant = Color(0xFFA6B6C9),
        outline = Color(0xFF53667C), outlineVariant = Color(0xFF293B50),
        error = Color(0xFFFFACAF), errorContainer = Color(0xFF47232D), onErrorContainer = Color(0xFFFFDADF)
    ) else lightColorScheme(
        primary = Color(0xFF205D92), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEEFF), onPrimaryContainer = Color(0xFF123752),
        secondary = Color(0xFF126A54), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCF5EB), onSecondaryContainer = Color(0xFF164D3D),
        tertiary = Color(0xFF835600), onTertiary = Color.White,
        background = Color(0xFFF2F5FA), onBackground = Color(0xFF162439),
        surface = Color.White, onSurface = Color(0xFF162439),
        surfaceContainer = Color.White, surfaceContainerLow = Color(0xFFF8FAFD),
        surfaceContainerHigh = Color(0xFFEBF1F8), surfaceContainerHighest = Color(0xFFE0E9F3),
        surfaceVariant = Color(0xFFEBF1F8), onSurfaceVariant = Color(0xFF54667E),
        outline = Color(0xFF71839A), outlineVariant = Color(0xFFD6DFEA),
        error = Color(0xFFAA3541), errorContainer = Color(0xFFFFE7E9), onErrorContainer = Color(0xFF771F2B)
    )
    val type = Typography(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    )
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}

internal enum class CompatActionTone { Primary, Neutral, Danger }

@Composable
internal fun CompatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tone: CompatActionTone = CompatActionTone.Neutral
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val fill = when {
        !enabled -> colors.surfaceContainerHigh
        tone == CompatActionTone.Primary -> colors.primary
        tone == CompatActionTone.Danger -> colors.errorContainer
        focused || pressed -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val ink = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.55f)
        tone == CompatActionTone.Primary -> colors.onPrimary
        tone == CompatActionTone.Danger -> colors.onErrorContainer
        focused || pressed -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Row(
        modifier = modifier.clip(shape).background(fill)
            .remoteFocusHighlight(shape, enabled)
            .clickable(interactionSource = interaction, indication = null,
                enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (LocalCompatLayout.current.compact) 48.dp else 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text(text, color = ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun CompatCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(contentModifier.padding(LocalCompatLayout.current.cardPadding.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun CompatCardTitle(title: String, subtitle: String? = null, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun CompatBadge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f))
        .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompatActions(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** A separate window owns modal focus on phones and televisions alike. */
@Composable
internal fun CompatDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    cancelText: String = "取消",
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmInitialFocus: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val remoteDevice = remember(context) { DeviceCompatMode.isCompatModeDevice(context) }
    val footer: @Composable () -> Unit = {
        CompatActions {
            CompatButton(cancelText, onDismiss,
                if (!confirmInitialFocus || !confirmEnabled) Modifier.focusRequester(cancelFocus) else Modifier)
            CompatButton(confirmText, onConfirm,
                modifier = if (confirmInitialFocus && confirmEnabled) Modifier.focusRequester(cancelFocus) else Modifier,
                enabled = confirmEnabled,
                tone = if (danger) CompatActionTone.Danger else CompatActionTone.Primary)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                val shortWindow = maxHeight.value / LocalDensity.current.fontScale.coerceAtLeast(1f) < 240f
                val sheetScroll = rememberScrollState()
                val bodyScroll = rememberScrollState()
                // Keep the same composition tree when the IME resizes this window.
                // Replacing the body would dispose focused text fields and dismiss the keyboard.
                val sheetModifier = if (shortWindow) Modifier.verticalScroll(sheetScroll) else Modifier
                Column(sheetModifier.padding(if (shortWindow) 16.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (shortWindow) 12.dp else 16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    val bodyModifier = if (shortWindow) Modifier.fillMaxWidth() else
                        Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(bodyScroll)
                    Column(bodyModifier, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                    footer()
                }
            }
        }
        LaunchedEffect(remoteDevice) {
            if (remoteDevice) {
                withFrameNanos { }
                cancelFocus.requestFocus()
            }
        }
    }
}
