package com.example.danmuapiapp.ui.screen.console

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboard.current
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    val dark = isSystemInDarkTheme()
    val chipErrorColor = if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    val chipWarnColor  = if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)

    val filteredLogs = if (filterLevel != null) {
        logs.filter { it.level == filterLevel }
    } else logs

    val errorCount = logs.count { it.level == LogLevel.Error }
    val warnCount = logs.count { it.level == LogLevel.Warn }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refreshLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("控制台", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${logs.size} 条日志" +
                        (if (errorCount > 0) " / $errorCount 错误" else "") +
                        (if (warnCount > 0) " / $warnCount 警告" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = viewModel::refreshLogs,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新日志", Modifier.size(18.dp))
                }
                // Copy all logs
                FilledTonalIconButton(
                    onClick = {
                        val text = logs.joinToString("\n") {
                            "[${it.level.name}] ${it.message}"
                        }
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("日志", text)
                        )
                    },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, "复制日志", Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = viewModel::clearLogs,
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ClearAll, "清除日志", Modifier.size(18.dp))
                }
            }
        }

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterLevel == null,
                onClick = { filterLevel = null },
                label = { Text("全部") },
                leadingIcon = if (filterLevel == null) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterLevel == LogLevel.Error,
                onClick = {
                    filterLevel = if (filterLevel == LogLevel.Error) null else LogLevel.Error
                },
                label = { Text("错误") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipErrorColor.copy(alpha = 0.15f)
                ),
                leadingIcon = if (filterLevel == LogLevel.Error) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterLevel == LogLevel.Warn,
                onClick = {
                    filterLevel = if (filterLevel == LogLevel.Warn) null else LogLevel.Warn
                },
                label = { Text("警告") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipWarnColor.copy(alpha = 0.15f)
                ),
                leadingIcon = if (filterLevel == LogLevel.Warn) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        // Log list
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Terminal, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (filterLevel != null) "没有匹配的日志" else "暂无日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = filteredLogs,
                        key = { index, entry ->
                            "${entry.timestamp}-${entry.level}-${entry.message.hashCode()}-$index"
                        }
                    ) { _, entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dark = isSystemInDarkTheme()
    val errorColor = if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    val warnColor  = if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)
    val color = when (entry.level) {
        LogLevel.Error -> errorColor
        LogLevel.Warn -> warnColor
        LogLevel.Info -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                when (entry.level) {
                    LogLevel.Error -> errorColor.copy(alpha = if (dark) 0.12f else 0.08f)
                    LogLevel.Warn -> warnColor.copy(alpha = if (dark) 0.10f else 0.05f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = timeFormat.format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 10.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 11.sp
            ),
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}
