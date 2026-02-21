package com.example.danmuapiapp.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.ServiceStatus

// ── Shared (non-settings) ──────────────────────────────────────────

@Composable
fun StatusIndicator(
    status: ServiceStatus,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val color by animateColorAsState(
        targetValue = when (status) {
            ServiceStatus.Running -> if (dark) Color(0xFF4ADE80) else Color(0xFF4CAF50)
            ServiceStatus.Starting, ServiceStatus.Stopping -> if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)
            ServiceStatus.Error -> if (dark) Color(0xFFF87171) else Color(0xFFE53935)
            ServiceStatus.Stopped -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(400),
        label = "statusColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ServiceStatus.Running) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (status == ServiceStatus.Running) alpha else 1f))
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        action?.invoke()
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = color)
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    disabledColors: List<Color> = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
) {
    Button(
        onClick = onClick, modifier = modifier.height(52.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                brush = if (enabled) Brush.horizontalGradient(colors)
                else Brush.horizontalGradient(disabledColors),
                shape = RoundedCornerShape(16.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f))
        }
    }
}

// ── Settings Design System ─────────────────────────────────────────

/**
 * 统一设置页头部：标题、副标题、可选返回按钮。
 */
@Composable
fun SettingsPageHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null
) {
    if (onBack == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 设置分组容器：标题 + 卡片化内容区域。
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (title != null) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 4.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

/**
 * 设置项行：支持图标、标题、副标题和右侧操作组件。
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val rowShape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                modifier = Modifier
                    .size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, iconTint.copy(alpha = 0.18f)),
                tonalElevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconTint)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

/** Thin divider inside a SettingsGroup, indented to align with text. */
@Composable
fun SettingsDivider(startIndent: Dp = 68.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    )
}

/** A SettingsItem with a built-in Switch as trailing widget. */
@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    )
}

/** A SettingsItem that shows a value on the right side. */
@Composable
fun SettingsValueItem(
    title: String,
    value: String,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    SettingsItem(
        title = title,
        icon = icon,
        iconTint = iconTint,
        onClick = onClick,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
                if (onClick != null) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
        }
    )
}

@Composable
fun SettingsHintCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Legacy aliases (kept for non-settings screens) ─────────────────

@Composable
fun SettingsEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchItem(title = title, subtitle = subtitle, icon = icon, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
fun SettingsInfoRow(label: String, value: String) {
    SettingsValueItem(title = label, value = value)
}
