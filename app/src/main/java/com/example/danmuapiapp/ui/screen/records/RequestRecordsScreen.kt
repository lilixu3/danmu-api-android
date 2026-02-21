package com.example.danmuapiapp.ui.screen.records

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.RequestRecord
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestRecordsScreen(
    onBack: () -> Unit,
    viewModel: RequestRecordsViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    var filterSuccess by remember { mutableStateOf<Boolean?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    val today = ZonedDateTime.now()
    val todayCount = records.count {
        val dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.timestamp), ZoneId.systemDefault())
        dt.toLocalDate() == today.toLocalDate()
    }
    val errorCount = records.count { !it.success }
    val successCount = records.count { it.success }

    val filteredRecords = when (filterSuccess) {
        true -> records.filter { it.success }
        false -> records.filter { !it.success }
        null -> records
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                }
                Column {
                    Text("请求记录", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "${records.size} 条记录" +
                            (if (todayCount > 0) " · 今日 $todayCount" else "") +
                            (if (errorCount > 0) " · $errorCount 失败" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(
                    onClick = viewModel::refresh,
                    enabled = !viewModel.isRefreshing,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (viewModel.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
                    }
                }
                FilledTonalIconButton(
                    onClick = viewModel::clearAll,
                    enabled = records.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ClearAll, "清空", Modifier.size(18.dp))
                }
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterSuccess == null,
                onClick = { filterSuccess = null },
                label = { Text("全部") },
                leadingIcon = if (filterSuccess == null) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterSuccess == true,
                onClick = { filterSuccess = if (filterSuccess == true) null else true },
                label = { Text("成功") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF4CAF50)).copy(alpha = 0.15f)
                ),
                leadingIcon = if (filterSuccess == true) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterSuccess == false,
                onClick = { filterSuccess = if (filterSuccess == false) null else false },
                label = { Text("失败") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFE53935)).copy(alpha = 0.15f)
                ),
                leadingIcon = if (filterSuccess == false) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        // Stats card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "今日", value = todayCount.toString())
                StatItem(label = "成功", value = successCount.toString(), valueColor = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF4CAF50))
                StatItem(label = "失败", value = errorCount.toString(), valueColor = if (errorCount > 0) (if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFE53935)) else MaterialTheme.colorScheme.onSurface)
                StatItem(label = "总计", value = records.size.toString())
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Records list
        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.History, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (filterSuccess != null) "没有匹配的记录" else "暂无请求记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredRecords, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        expanded = expandedId == record.id,
                        onClick = {
                            expandedId = if (expandedId == record.id) null else record.id
                        },
                        onCopyUrl = {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("请求地址", record.url)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecordCard(
    record: RequestRecord,
    expanded: Boolean,
    onClick: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val statusText = record.statusCode?.toString() ?: "ERR"
    val dark = isSystemInDarkTheme()
    val statusColor = if (record.success) {
        if (dark) Color(0xFF4ADE80) else Color(0xFF4CAF50)
    } else {
        if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top row: scene + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.scene,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Method + URL
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        record.method,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Text(
                    record.url,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 1
                )
            }

            // Bottom row: time + duration
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    "${record.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Expanded details
            if (expanded) {
                // Error message
                if (!record.errorMessage.isNullOrBlank()) {
                    Text(
                        record.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFE53935)
                    )
                }

                // Response snippet
                if (!record.responseSnippet.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 0.dp
                    ) {
                        Text(
                            record.responseSnippet.take(500),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(8.dp),
                            maxLines = 10
                        )
                    }
                }

                // Copy URL button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onCopyUrl,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制 URL", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
