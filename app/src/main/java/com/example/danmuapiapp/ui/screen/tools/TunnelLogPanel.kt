package com.example.danmuapiapp.ui.screen.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun TunnelLogs(log: String, expanded: Boolean, onExpand: () -> Unit, onRefresh: () -> Unit, onClear: () -> Unit) {
    val entries = remember(log) { parseTunnelLog(log) }
    val context = LocalContext.current
    var issuesOnly by rememberSaveable { mutableStateOf(false) }
    val issues = entries.count { it.isIssue }
    val visible = remember(entries, issuesOnly) { entries.asReversed().filter { !issuesOnly || it.isIssue } }
    TunnelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("运行日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "收起日志" else "展开日志",
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("frpc 日志", entries.joinToString("\n") { it.raw }))
                    if (clipboard != null) Toast.makeText(context, "已复制整理后的日志", Toast.LENGTH_SHORT).show()
                }, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Rounded.ContentCopy, "复制日志", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "刷新日志", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onClear) { Icon(Icons.Rounded.DeleteOutline, "清空日志", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (issues > 0) TunnelBadge("$issues 条异常", MaterialTheme.colorScheme.error)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${visible.size} 条 · 最新在前", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { issuesOnly = !issuesOnly }) { Text(if (issuesOnly) "显示全部" else "只看异常") }
                }
                if (visible.isEmpty()) {
                    Text(if (issuesOnly) "没有警告或错误记录" else "暂无日志，启动隧道后显示连接记录。",
                        Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(visible) { entry -> TunnelLogRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TunnelLogRow(entry: TunnelLogEntry) {
    var details by remember(entry) { mutableStateOf(false) }
    val color = when (entry.level) {
        TunnelLogLevel.Error -> MaterialTheme.colorScheme.error
        TunnelLogLevel.Warning -> MaterialTheme.colorScheme.tertiary
        TunnelLogLevel.Session -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = { details = !details }),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.width(62.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.time.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.level.label, style = MaterialTheme.typography.labelSmall, color = color)
            }
            Text(entry.summary, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (entry.isIssue) color else MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Icon(if (details) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                if (details) "收起详情" else "展开日志详情", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (details) {
            SelectionContainer {
                Text(entry.raw, Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }
}
