package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.domain.model.AccessEntryTab
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.AccessEntryLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AccessGatewayPanel(
    localUrl: String,
    lanUrl: String,
    lanIpv6Url: String,
    tunnelUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    showLocalNetworkPermissionHint: Boolean,
    onOpenLocalNetworkPermission: () -> Unit,
    onToggleTokenVisible: () -> Unit,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit,
    onCopyLanIpv6: () -> Unit,
    onCopyTunnel: () -> Unit,
    defaultTab: AccessEntryTab = AccessEntryTab.Ipv4,
    selectionSession: Int = 0,
    layout: AccessEntryLayout = AccessEntryLayout.Tabs
) {
    val displayLocal = maskRuntimeUrl(localUrl, token, maskedToken, tokenVisible)
    val displayLan = maskRuntimeUrl(lanUrl, token, maskedToken, tokenVisible)
    val displayLanIpv6 = maskRuntimeUrl(lanIpv6Url, token, maskedToken, tokenVisible)
    val displayTunnel = maskRuntimeUrl(tunnelUrl, token, maskedToken, tokenVisible)

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "访问入口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AppGlassIconButton(
                    onClick = onToggleTokenVisible,
                    size = 30.dp
                ) {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "切换地址 Token 可见",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(visible = showLocalNetworkPermissionHint) {
                LocalNetworkAddressPermissionHint(
                    onClick = onOpenLocalNetworkPermission
                )
            }
            if (layout == AccessEntryLayout.Tabs) {
                AccessGatewayTabs(
                    displayLocal, displayLan, displayLanIpv6, displayTunnel,
                    onCopyLocal, onCopyLan, onCopyLanIpv6, onCopyTunnel, defaultTab, selectionSession
                )
            } else {
                if (tunnelUrl.isNotBlank()) {
                    GatewayItem(
                        title = "内网穿透",
                        subtitle = "经 frp 服务器转发的公网地址",
                        value = displayTunnel,
                        onCopy = onCopyTunnel,
                        emphasize = true
                    )
                }
                GatewayItem(
                    title = "局域网 IPv4",
                    subtitle = "兼容性最佳，推荐在同一 Wi-Fi 下使用",
                    value = displayLan,
                    onCopy = onCopyLan,
                    emphasize = true
                )
                if (lanIpv6Url.isNotBlank()) {
                    GatewayItem(
                        title = "局域网 IPv6",
                        subtitle = "适用于已分配 IPv6 地址的双栈网络",
                        value = displayLanIpv6,
                        onCopy = onCopyLanIpv6,
                        emphasize = false
                    )
                }
                GatewayItem(
                    title = "本机",
                    subtitle = "仅当前设备可访问",
                    value = displayLocal,
                    onCopy = onCopyLocal,
                    emphasize = false
                )
            }
        }
    }
}

@Composable
private fun LocalNetworkAddressPermissionHint(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = "仅本机可访问 · 局域网权限未开启",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "去授权",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
}

