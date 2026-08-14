package com.example.danmuapiapp.ui.screen.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CoreDiffLine
import com.example.danmuapiapp.domain.model.CoreDiffLineType
import com.example.danmuapiapp.domain.model.CoreRevision
import com.example.danmuapiapp.domain.model.CoreRevisionDetails
import com.example.danmuapiapp.domain.model.CoreRevisionFileChange
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppModalPanel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun CoreRevisionHistoryPanel(
    viewModel: CoreViewModel
) {
    val details = viewModel.selectedRevisionDetails
    val selected = viewModel.selectedRevision

    AppModalPanel(
        onDismissRequest = viewModel::dismissRollbackDialog,
        maxWidth = 980.dp,
        expanded = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RevisionHeader(
                showingDetails = selected != null,
                onBack = if (selected != null) viewModel::closeRevisionDetails else null,
                onClose = viewModel::dismissRollbackDialog
            )

            if (selected == null) {
                RevisionList(
                    revisions = viewModel.revisionHistory,
                    query = viewModel.revisionSearchQuery,
                    appliedQuery = viewModel.appliedRevisionSearchQuery,
                    page = viewModel.revisionPage,
                    hasNextPage = viewModel.revisionHasNextPage,
                    isLoading = viewModel.isLoadingHistory,
                    error = viewModel.revisionHistoryError,
                    onQueryChange = viewModel::updateRevisionSearchQuery,
                    onSearch = viewModel::submitRevisionSearch,
                    onPreviousPage = viewModel::previousRevisionPage,
                    onNextPage = viewModel::nextRevisionPage,
                    onOpen = viewModel::openRevisionDetails,
                    onRollback = viewModel::requestRollback
                )
            } else {
                RevisionDetails(
                    revision = selected,
                    details = details,
                    isLoading = viewModel.isLoadingRevisionDetails,
                    error = viewModel.revisionDetailsError,
                    onRollback = { viewModel.requestRollback(selected) }
                )
            }
        }
    }

    viewModel.pendingRollbackRevision?.let { revision ->
        AppDialog(
            onDismissRequest = viewModel::cancelRollbackRequest,
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text("回退到 ${revision.displayVersion}？") },
            text = {
                Text("将用提交 ${revision.shortSha} 替换当前核心。运行中的对应核心会先安全停止，完成后自动重启。")
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRollback) { Text("确认回退") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRollbackRequest) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RevisionHeader(
    showingDetails: Boolean,
    onBack: (() -> Unit)?,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回提交列表")
            }
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (showingDetails) "提交变动详情" else "版本时间线",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (showingDetails) "逐文件查看增加与删除的代码" else "搜索提交、检查内容并选择回退点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Close, "关闭")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RevisionList(
    revisions: List<CoreRevision>,
    query: String,
    appliedQuery: String,
    page: Int,
    hasNextPage: Boolean,
    isLoading: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpen: (CoreRevision) -> Unit,
    onRollback: (CoreRevision) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                IconButton(onClick = onSearch, enabled = !isLoading) {
                    Icon(Icons.Rounded.Search, "执行 GitHub 提交搜索")
                }
            },
            placeholder = { Text("搜索说明、SHA 或 @作者") },
            supportingText = {
                Text(
                    if (appliedQuery.isBlank()) "每页从 GitHub 加载 15 条提交"
                    else "GitHub 搜索：$appliedQuery · 作者可用 @用户名"
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = RoundedCornerShape(8.dp)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> EmptyRevisionState(error)
                revisions.isEmpty() -> EmptyRevisionState(
                    if (appliedQuery.isBlank()) "没有找到提交记录" else "GitHub 没有找到匹配提交"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(revisions, key = { it.commitSha }) { revision ->
                        RevisionRow(revision, onOpen, onRollback)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = onPreviousPage,
                enabled = !isLoading && page > 1,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("上一页")
            }
            Text("第 $page 页", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = onNextPage,
                enabled = !isLoading && hasNextPage,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("下一页")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.NavigateNext, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyRevisionState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RevisionRow(
    revision: CoreRevision,
    onOpen: (CoreRevision) -> Unit,
    onRollback: (CoreRevision) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(revision) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    formatRevisionVersion(revision),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    revision.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${revision.shortSha} · ${revision.author} · ${formatCommitTime(revision.committedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { onRollback(revision) }, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Rounded.Restore, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("回退")
            }
        }
    }
}

@Composable
private fun RevisionDetails(
    revision: CoreRevision,
    details: CoreRevisionDetails?,
    isLoading: Boolean,
    error: String?,
    onRollback: () -> Unit
) {
    val expandedFiles = remember(revision.commitSha) {
        mutableStateMapOf<String, Boolean>()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            details != null -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "commit-summary") {
                    RevisionCommitSummary(revision = revision, details = details)
                }
                item(key = "file-heading") {
                    Text(
                        "变更文件",
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(details.files, key = { "${it.status}:${it.path}" }) { file ->
                    CoreFileDiff(
                        file = file,
                        expanded = expandedFiles[file.path] == true,
                        onExpandedChange = { expanded ->
                            if (expanded) {
                                expandedFiles[file.path] = true
                            } else {
                                expandedFiles.remove(file.path)
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onRollback, enabled = !isLoading) {
                Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("回退到此版本")
            }
        }
    }
}

@Composable
private fun RevisionCommitSummary(
    revision: CoreRevision,
    details: CoreRevisionDetails
) {
    val body = revision.message
        .lineSequence()
        .drop(1)
        .joinToString("\n")
        .trim()
    val addedColor = diffAddedColor()
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatRevisionVersion(revision),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(revision.shortSha, style = MaterialTheme.typography.labelSmall)
        }
        Text(revision.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "${revision.author} · ${formatCommitTime(revision.committedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (body.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        "提交说明",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DiffMetric(Icons.Rounded.Code, "${details.changedFiles} 个文件", MaterialTheme.colorScheme.primary)
            DiffMetric(Icons.Rounded.Add, "+${details.additions}", addedColor)
            DiffMetric(Icons.Rounded.DeleteOutline, "-${details.deletions}", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun DiffMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun CoreFileDiff(
    file: CoreRevisionFileChange,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val addedColor = diffAddedColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    if (expanded) "收起文件变动" else "展开文件变动",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        file.path,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    file.previousPath?.takeIf { it.isNotBlank() && it != file.path }?.let { previousPath ->
                        Text(
                            "原路径 $previousPath",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(fileStatusText(file.status), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text("+${file.additions}", color = addedColor, style = MaterialTheme.typography.labelSmall)
                        Text("-${file.deletions}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (file.lines.isEmpty()) {
                    Text(
                        file.patchUnavailableReason ?: "没有文本变动",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        file.lines.forEach { DiffLineRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(line: CoreDiffLine) {
    val addedColor = diffAddedColor()
    val background = when (line.type) {
        CoreDiffLineType.Added -> Color(0xFF2EA043).copy(alpha = 0.16f)
        CoreDiffLineType.Removed -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        CoreDiffLineType.Header -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        CoreDiffLineType.Context -> Color.Transparent
    }
    val foreground = when (line.type) {
        CoreDiffLineType.Added -> addedColor
        CoreDiffLineType.Removed -> MaterialTheme.colorScheme.error
        CoreDiffLineType.Header -> MaterialTheme.colorScheme.primary
        CoreDiffLineType.Context -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            line.newLineNumber?.toString().orEmpty(),
            modifier = Modifier.width(44.dp).padding(start = 5.dp, end = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            line.content.ifEmpty { " " },
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = foreground,
            softWrap = true
        )
    }
}

@Composable
internal fun diffAddedColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF56D364)
    } else {
        Color(0xFF1A7F37)
    }
}

private fun fileStatusText(status: String): String = when (status.lowercase()) {
    "added" -> "新增"
    "removed" -> "删除"
    "renamed" -> "重命名"
    "copied" -> "复制"
    "changed" -> "变更"
    else -> "修改"
}

private fun formatCommitTime(raw: String): String {
    if (raw.isBlank()) return "时间未知"
    return runCatching {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(raw))
    }.getOrDefault(raw.take(16).replace('T', ' '))
}

private fun formatRevisionVersion(revision: CoreRevision): String {
    return revision.version.trim().takeIf { it.isNotBlank() }?.let { "v$it" }
        ?: revision.displayVersion
}
