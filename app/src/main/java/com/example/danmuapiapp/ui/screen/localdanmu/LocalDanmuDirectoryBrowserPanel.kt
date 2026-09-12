package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SnippetFolder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.repository.LocalDanmuFileBrowser
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

/**
 * 目录直读模式的浏览面板：直接列出“所有文件访问”范围内的目录与弹幕文件；
 * 文件始终可以自由勾选一个或多个，底部统一「导入选中」。
 */
@Composable
internal fun LocalDanmuDirectoryBrowserPanel(
    state: LocalDanmuBrowserState,
    onBack: () -> Unit,
    onOpenParent: () -> Unit,
    onOpenDefaultDirectory: () -> Unit,
    onUseSystemPicker: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onSetDefaultDirectory: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onImportSelected: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDirectory: (String) -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppGlassIconButton(onClick = onBack, size = 36.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("选择弹幕文件", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        state.path.ifBlank { "正在读取目录…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppGlassIconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading,
                    size = 36.dp
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppGlassButton(
                    onClick = onOpenParent,
                    enabled = state.parent != null && !state.isLoading
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("上一级")
                }
                AppGlassButton(
                    onClick = onOpenDefaultDirectory,
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Rounded.SnippetFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("弹幕目录")
                }
                if (!state.isDefaultDirectory) {
                    AppGlassButton(
                        onClick = onSetDefaultDirectory,
                        enabled = !state.isLoading && state.path.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("设为默认")
                    }
                }
                AppGlassButton(
                    onClick = onUseSystemPicker,
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("系统选择器")
                }
            }

            state.error?.let { error ->
                AppGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (state.rows.isEmpty() && !state.isLoading && state.error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.SnippetFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        "该目录下没有目录或弹幕文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "支持 XML / JSON / ASS / SSA / CSV / TXT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(items = state.rows, key = { it.entry.path }) { row ->
                    DirectoryEntryRow(
                        row = row,
                        enabled = !state.isLoading,
                        onClick = {
                            if (row.entry.isDirectory) {
                                onOpenDirectory(row.entry.path)
                            } else {
                                onToggleSelection(row.entry.path)
                            }
                        }
                    )
                }
                if (state.truncated) {
                    item(key = "truncated") {
                        Text(
                            "目录条目过多，仅显示前 ${LocalDanmuFileBrowser.MAX_VISIBLE_ENTRIES} 项，请进入子目录查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "已选 ${state.selectedCount} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppGlassButton(
                    onClick = onToggleSelectAll,
                    enabled = !state.isLoading && state.selectablePaths.isNotEmpty()
                ) {
                    Icon(
                        Icons.Rounded.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (state.allSelected) "取消全选" else "全选")
                }
                Spacer(Modifier.weight(1f))
                AppGlassButton(
                    onClick = onImportSelected,
                    enabled = state.selectedCount > 0 && !state.isLoading
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("导入选中 ${state.selectedCount} 个")
                }
            }
        }
    }
}

@Composable
private fun DirectoryEntryRow(
    row: LocalDanmuBrowserRow,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val entry = row.entry
    val clickable = enabled
    AppGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                when {
                    entry.isDirectory -> Icons.Rounded.Folder
                    row.selected -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    entry.isDirectory -> MaterialTheme.colorScheme.primary
                    row.selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (entry.isDirectory) "文件夹" else formatLocalDanmuSize(entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                row.importedLabel != null -> Text(
                    row.importedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                !entry.isDirectory -> Icon(
                    Icons.Rounded.LockOpen,
                    contentDescription = "点击勾选",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
