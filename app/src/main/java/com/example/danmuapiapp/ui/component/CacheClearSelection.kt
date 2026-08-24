package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CacheClearCapability
import com.example.danmuapiapp.domain.model.CacheClearItem
import com.example.danmuapiapp.domain.model.CacheClearSupport
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.theme.LocalAppDarkTheme
import com.example.danmuapiapp.ui.theme.LocalAppDialogContext
import com.example.danmuapiapp.ui.theme.LocalGlassMaterial

internal data class CacheClearItemPresentation(
    val title: String,
    val detail: String,
    val icon: ImageVector
)

internal fun cacheClearItemPresentation(item: CacheClearItem): CacheClearItemPresentation = when (item) {
    CacheClearItem.SearchCache -> CacheClearItemPresentation(
        title = "搜索结果缓存",
        detail = "搜索匹配与解析结果 · searchCache",
        icon = Icons.Rounded.Search
    )
    CacheClearItem.CommentCache -> CacheClearItemPresentation(
        title = "弹幕内容缓存",
        detail = "已加载的弹幕内容 · commentCache",
        icon = Icons.Rounded.ChatBubbleOutline
    )
    CacheClearItem.RequestHistory -> CacheClearItemPresentation(
        title = "请求历史记录",
        detail = "请求记录与今日计数 · requestHistory",
        icon = Icons.Rounded.History
    )
    CacheClearItem.Animes -> CacheClearItemPresentation(
        title = "动漫搜索缓存",
        detail = "动漫搜索结果 · animes",
        icon = Icons.Rounded.Movie
    )
    CacheClearItem.BangumiData -> CacheClearItemPresentation(
        title = "动画元数据缓存",
        detail = "内存与磁盘元数据 · bangumiData",
        icon = Icons.Rounded.Storage
    )
    CacheClearItem.EpisodeIds -> CacheClearItemPresentation(
        title = "剧集 ID 缓存",
        detail = "剧集标识映射 · episodeIds",
        icon = Icons.Rounded.Link
    )
    CacheClearItem.EpisodeNum -> CacheClearItemPresentation(
        title = "剧集编号缓存",
        detail = "剧集编号状态 · episodeNum",
        icon = Icons.AutoMirrored.Rounded.ListAlt
    )
    CacheClearItem.LastSelectMap -> CacheClearItemPresentation(
        title = "最后选择映射",
        detail = "最近选择关系 · lastSelectMap",
        icon = Icons.Rounded.TouchApp
    )
}

@Composable
internal fun CacheClearSelectionToolbar(
    selectedCount: Int,
    selectionEnabled: Boolean,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "已选 $selectedCount / ${CacheClearItem.entries.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AppGlassButton(
                onClick = onSelectAll,
                enabled = selectionEnabled,
                modifier = Modifier.height(34.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) { Text("全选") }
            AppGlassButton(
                onClick = onSelectNone,
                enabled = selectionEnabled,
                modifier = Modifier.height(34.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) { Text("全不选") }
        }
    }
}

@Composable
internal fun CacheClearSelectionPanel(
    selectedItems: Set<CacheClearItem>,
    selectionEnabled: Boolean,
    onToggle: (CacheClearItem) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val liquidDialog = LocalGlassMaterial.current.enabled && LocalAppDialogContext.current
    val panelContent: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (liquidDialog) 0.dp else 10.dp)
        ) {
            CacheClearSelectionToolbar(
                selectedCount = selectedItems.size,
                selectionEnabled = selectionEnabled,
                onSelectAll = onSelectAll,
                onSelectNone = onSelectNone,
                modifier = if (liquidDialog) {
                    Modifier.padding(start = 10.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                } else {
                    Modifier
                }
            )
            CacheClearSelectionList(
                selectedItems = selectedItems,
                selectionEnabled = selectionEnabled,
                onToggle = onToggle,
                compact = compact,
                framed = !liquidDialog
            )
        }
    }

    if (liquidDialog) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.40f)),
            content = panelContent
        )
    } else {
        Column(modifier = modifier, content = { panelContent() })
    }
}

@Composable
internal fun CacheClearSelectionList(
    selectedItems: Set<CacheClearItem>,
    selectionEnabled: Boolean,
    onToggle: (CacheClearItem) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    framed: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val liquidDialog = LocalGlassMaterial.current.enabled && LocalAppDialogContext.current
    val darkTheme = LocalAppDarkTheme.current
    val listShape = RoundedCornerShape(14.dp)
    val listBorder = BorderStroke(
        1.dp,
        colors.outlineVariant.copy(alpha = if (liquidDialog) 0.40f else 0.55f)
    )
    val listContent: @Composable () -> Unit = {
        Column {
            CacheClearItem.entries.forEachIndexed { index, item ->
                val presentation = cacheClearItemPresentation(item)
                val selected = item in selectedItems
                val rowContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (compact) 10.dp else 13.dp,
                                vertical = if (compact) 7.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppGlassSurface(
                            modifier = Modifier.size(if (compact) 32.dp else 36.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected && liquidDialog) {
                                colors.primary.copy(alpha = 0.12f)
                            } else {
                                colors.surfaceContainerHigh
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    presentation.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (compact) 17.dp else 19.dp),
                                    tint = if (selected) colors.primary else colors.onSurfaceVariant
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                presentation.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!compact) {
                                Text(
                                    presentation.detail,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            enabled = selectionEnabled
                        )
                    }
                }
                if (liquidDialog) {
                    Surface(
                        onClick = { onToggle(item) },
                        enabled = selectionEnabled,
                        shape = RectangleShape,
                        color = if (selected) {
                            colors.primary.copy(alpha = if (darkTheme) 0.16f else 0.13f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = colors.onSurface,
                        content = rowContent
                    )
                } else {
                    AppGlassSurface(
                        onClick = { onToggle(item) },
                        enabled = selectionEnabled,
                        shape = RectangleShape,
                        color = if (selected) {
                            colors.primaryContainer.copy(
                                alpha = if (LocalGlassMaterial.current.enabled) 0.72f else 0.16f
                            )
                        } else {
                            colors.surfaceContainerLow
                        },
                        content = rowContent
                    )
                }
                if (index != CacheClearItem.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = if (compact) 52.dp else 59.dp),
                        color = colors.outlineVariant.copy(alpha = if (liquidDialog) 0.34f else 0.45f)
                    )
                }
            }
        }
    }

    if (liquidDialog && !framed) {
        listContent()
    } else if (liquidDialog) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = listShape,
            color = Color.Transparent,
            border = listBorder,
            content = listContent
        )
    } else {
        AppGlassSurface(
            modifier = modifier.fillMaxWidth(),
            shape = listShape,
            color = colors.surfaceContainerLow,
            border = listBorder,
            content = listContent
        )
    }
}

@Composable
internal fun CacheClearCapabilityNotice(
    capability: CacheClearCapability,
    modifier: Modifier = Modifier
) {
    val text = when (capability.support) {
        CacheClearSupport.Selective -> "当前核心支持按项清理，未勾选的数据会保留。"
        CacheClearSupport.LegacyAllOnly -> "当前核心版本仅支持全部清理，已锁定全选。更新核心后可按项选择。"
        CacheClearSupport.Unknown -> "无法确认当前核心的按项清理能力，将使用兼容全量清理并锁定全选。"
    }
    val attention = capability.support != CacheClearSupport.Selective
    AppGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (attention) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (attention) MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (attention) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
