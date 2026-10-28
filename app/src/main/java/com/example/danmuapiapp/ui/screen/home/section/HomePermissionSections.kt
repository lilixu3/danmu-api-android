package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun RuntimePermissionHintCard(
    notificationReady: Boolean,
    batteryRequired: Boolean,
    batteryReady: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val showNotification = !notificationReady
    val showBattery = batteryRequired && !batteryReady

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "运行环境提醒",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (showNotification && showBattery) {
                        "建议尽快补齐下面两项，启动状态会更清楚，普通模式后台也更稳定。"
                    } else if (showNotification) {
                        "建议把通知权限补齐，启动和运行状态会更清楚。"
                    } else {
                        "建议把电池优化设为不受限制，普通模式后台会更稳定。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showNotification) {
                PermissionQuickActionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "通知权限",
                    summary = "建议开启，避免误判是否真的启动成功",
                    ready = false,
                    readyText = "已开启",
                    pendingText = "去开启",
                    onClick = onOpenNotificationSettings
                )
            }

            if (showNotification && showBattery) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )
            }

            if (showBattery) {
                PermissionQuickActionRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "电池优化",
                    summary = "建议改为不受限制，减少后台被清理",
                    ready = false,
                    readyText = "已优化",
                    pendingText = "去设置",
                    onClick = onOpenBatterySettings
                )
            }
        }
    }
}

@Composable
private fun PermissionQuickActionRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    summary: String,
    ready: Boolean,
    readyText: String,
    pendingText: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppGlassSurface(
            shape = RoundedCornerShape(10.dp),
            color = accent.copy(alpha = 0.14f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ready) {
            AppGlassButton(
                onClick = {},
                surfaceColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.46f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(readyText)
            }
        } else {
            AppGlassButton(
                onClick = { onClick?.invoke() },
                tint = MaterialTheme.colorScheme.primary
            ) {
                Text(pendingText)
            }
        }
    }
}

