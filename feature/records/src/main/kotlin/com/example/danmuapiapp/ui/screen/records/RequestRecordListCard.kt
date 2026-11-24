package com.example.danmuapiapp.ui.screen.records

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.FileNameMatchSummary
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.ui.component.AppGlassSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RequestRecordListCard(
    record: RequestRecord,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenDetail: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val time = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val accent = when {
        (record.statusCode ?: 0) in 300..399 -> colors.tertiary
        record.success -> Color(0xFF18865B)
        else -> colors.error
    }
    val params = record.requestParamsText()

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MetaChip(record.method, colors.primary, colors.primaryContainer)
                MetaChip(record.statusCode?.toString() ?: if (record.success) "完成" else "失败",
                    accent, accent.copy(alpha = 0.12f))
                record.fileNameMatch?.let { MetaChip("文件名匹配", colors.secondary, colors.secondaryContainer) }
                Spacer(Modifier.weight(1f))
                Text(time.format(Date(record.timestamp)), style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant, maxLines = 1)
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = if (expanded) "收起请求参数" else "展开请求参数", onClick = onToggleExpanded),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(RequestRecordInsights.endpoint(record.url), style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                record.fileNameMatch?.let { FileMatchTitle(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("客户端 · " + record.scene.removePrefix("外部调用/"), style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("·", color = colors.outline)
                    Text(record.durationMs?.let { "$it ms" } ?: "耗时未记录",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }

            if (expanded) {
                record.errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Surface(shape = RoundedCornerShape(9.dp), color = colors.errorContainer) {
                        Text(it, Modifier.fillMaxWidth().padding(10.dp), color = colors.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (params.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("请求参数", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            Text(params, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                maxLines = 12, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else if (!record.responseSnippet.isNullOrBlank()) {
                    Text(record.responseSnippet.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = onToggleExpanded, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (expanded) "收起参数" else "查看参数")
                }
                IconButton(onClick = onCopyUrl) {
                    Icon(Icons.Rounded.ContentCopy, "复制接口地址", Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                if (onOpenDetail != null) {
                    FilledTonalButton(onClick = onOpenDetail,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("处理链路")
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileMatchTitle(match: FileNameMatchSummary) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(11.dp), color = colors.secondaryContainer.copy(alpha = 0.58f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Rounded.Movie, null, Modifier.size(19.dp), tint = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("按文件名匹配", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(match.fileName, style = MaterialTheme.typography.titleSmall,
                    color = colors.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, foreground: Color, container: Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = container) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = foreground,
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun RequestRecord.requestParamsText(): String {
    val snippet = responseSnippet.orEmpty()
    val marker = "参数:\n"
    val start = snippet.indexOf(marker)
    return if (start >= 0) snippet.substring(start + marker.length).trim()
    else snippet.takeUnless { it.startsWith("客户端IP:") }.orEmpty()
}
