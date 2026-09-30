package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.domain.model.AccessEntryTab
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

private data class GatewayTab(
    val id: AccessEntryTab, val label: String, val title: String, val description: String,
    val address: String, val copy: () -> Unit
)

@Composable
internal fun AccessGatewayTabs(
    local: String, ipv4: String, ipv6: String, tunnel: String,
    copyLocal: () -> Unit, copyIpv4: () -> Unit, copyIpv6: () -> Unit, copyTunnel: () -> Unit,
    defaultTab: AccessEntryTab = AccessEntryTab.Ipv4,
    selectionSession: Int = 0
) {
    val tabs = buildList {
        if (tunnel.isNotBlank()) add(GatewayTab(AccessEntryTab.Tunnel, "穿透", "内网穿透", "通过 frp 公网入口访问", tunnel, copyTunnel))
        add(GatewayTab(AccessEntryTab.Ipv4, "IPv4", "局域网 IPv4", "同一 Wi-Fi 下使用 · 兼容性最佳", ipv4, copyIpv4))
        if (ipv6.isNotBlank()) add(GatewayTab(AccessEntryTab.Ipv6, "IPv6", "局域网 IPv6", "适用于已分配 IPv6 地址的网络", ipv6, copyIpv6))
        add(GatewayTab(AccessEntryTab.Local, "本机", "本机地址", "仅当前设备可访问", local, copyLocal))
    }
    var selectedId by rememberSaveable(defaultTab, selectionSession) { mutableStateOf(defaultTab.storageValue) }
    val effectiveTab = AccessEntryTab.resolve(AccessEntryTab.fromStorageValue(selectedId), tabs.map { it.id }.toSet())
    val selected = tabs.first { it.id == effectiveTab }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
            .horizontalScroll(rememberScrollState()).selectableGroup().padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val active = selected.id == tab.id
            val background by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f),
                label = "gatewayTab"
            )
            Box(
                Modifier.clip(RoundedCornerShape(7.dp)).background(background)
                    .selectable(active, role = Role.Tab, onClick = { selectedId = tab.id.storageValue })
                    .heightIn(min = 34.dp).padding(horizontal = 16.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tab.label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 三行文字共享紧凑间距，复制按钮的触控高度不参与文字行间距。
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(selected.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(selected.description, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(selected.address, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            AppGlassIconButton(onClick = selected.copy, size = 44.dp,
                contentColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.ContentCopy, "复制${selected.title}", Modifier.size(18.dp))
            }
        }
    }
}
