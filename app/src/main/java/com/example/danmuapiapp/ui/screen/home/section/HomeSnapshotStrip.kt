package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppGlassSurface
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus

@Composable
internal fun SnapshotStrip(
    status: ServiceStatus,
    isDarkTheme: Boolean,
    runMode: RunMode,
    cacheTileValue: String,
    cacheTileBadge: String?,
    cacheTileAccent: Color,
    onOpenCacheQuick: () -> Unit,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onEditToken: () -> Unit,
    port: Int,
    coreVersionText: String,
    coreVersionBadge: String?,
    coreVersionAccent: Color,
    isActionBusy: Boolean,
    onEditPort: () -> Unit,
    onCheckCoreUpdate: () -> Unit
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "缓存管理",
                    value = cacheTileValue,
                    badge = cacheTileBadge,
                    accent = cacheTileAccent,
                    onClick = onOpenCacheQuick
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "端口",
                    value = "TCP $port",
                    badge = when {
                        runMode == RunMode.Root && port == 80 -> "默认端口"
                        status == ServiceStatus.Running -> "监听中"
                        else -> "未监听"
                    },
                    accent = MaterialTheme.colorScheme.onSurface,
                    enabled = !isActionBusy,
                    onClick = onEditPort
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TokenMetricTile(
                    modifier = Modifier.weight(1f),
                    token = token,
                    maskedToken = maskedToken,
                    tokenVisible = tokenVisible,
                    onEditToken = onEditToken
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "核心版本",
                    value = coreVersionText,
                    badge = coreVersionBadge,
                    accent = coreVersionAccent,
                    enabled = !isActionBusy,
                    onClick = onCheckCoreUpdate
                )
            }
        }
    }
}

