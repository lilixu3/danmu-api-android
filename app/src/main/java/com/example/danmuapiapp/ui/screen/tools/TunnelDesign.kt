package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.theme.glassBorderColor
import com.example.danmuapiapp.ui.theme.glassSurfaceColor

@Composable
internal fun TunnelTopBar(title: String, subtitle: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.weight(1f)) { SettingsPageHeader(title, subtitle, onBack) }
        action?.invoke()
    }
}

/** Use the app's shared surface colors and glass treatment, with compact content spacing. */
@Composable
internal fun TunnelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    AppGlassSurface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = glassSurfaceColor(), contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, glassBorderColor())
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun TunnelSectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun TunnelBadge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        Modifier.background(color.copy(alpha = 0.09f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun TunnelNote(text: String, isError: Boolean = false, icon: ImageVector = Icons.Rounded.Info) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun TunnelChoice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size)
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun TunnelToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun TunnelDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(76.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun tunnelStatusColor(tone: TunnelTone) = when (tone) {
    TunnelTone.Active -> MaterialTheme.colorScheme.primary
    TunnelTone.Pending -> MaterialTheme.colorScheme.tertiary
    TunnelTone.Error -> MaterialTheme.colorScheme.error
    TunnelTone.Quiet -> MaterialTheme.colorScheme.onSurfaceVariant
}
