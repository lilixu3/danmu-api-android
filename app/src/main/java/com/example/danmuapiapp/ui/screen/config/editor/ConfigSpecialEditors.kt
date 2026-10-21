package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecentAnimeCachePanel(
    rememberKey: String,
    currentKey: String,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
    onFillMergeEntity: ((String, String, String) -> Unit)? = null,
    mergeFillRole: String? = null,
    mergeFillLabel: String = "填入",
    onFillOffsetEntity: ((String, String) -> Unit)? = null,
    onAddSourcePair: ((String) -> Unit)? = null,
    compact: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember(rememberKey) { mutableStateOf(false) }
    var loading by remember(rememberKey) { mutableStateOf(false) }
    var error by remember(rememberKey) { mutableStateOf<String?>(null) }
    var cacheItems by remember(rememberKey) { mutableStateOf<List<AnimeCacheItem>>(emptyList()) }

    val helpText = when (currentKey) {
        KEY_CUSTOM_MERGE_RULES -> "缓存卡片可直接填 A / 填 B；最近数据只做辅助，写入后仍可手动调整。"
        KEY_DANMU_OFFSET -> "点击“填入”可把剧名和来源带入偏移规则。"
        KEY_MERGE_SOURCE_PAIRS -> "点击“加入来源”可把来源加入当前组合暂存区。"
        else -> "点击缓存卡片可查看最近 animes 缓存，辅助填写当前配置。"
    }

    fun load(force: Boolean = false) {
        if (loading) return
        if (!force && cacheItems.isNotEmpty()) return
        loading = true
        error = null
        scope.launch {
            val result = onFetchRecentAnimeCache()
            loading = false
            result.onSuccess {
                cacheItems = it
            }.onFailure {
                error = it.message ?: "请求失败"
                cacheItems = emptyList()
            }
        }
    }

    if (compact && !expanded) {
        AppGlassButton(
            onClick = {
                expanded = true
                load(force = true)
            }
        ) {
            Icon(Icons.Rounded.Tune, null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("查看最近数据")
        }
        return
    }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "最近数据",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                AppGlassButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded) load(force = true)
                    }
                ) {
                    Icon(Icons.Rounded.Tune, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (expanded) "收起" else "查看最近数据")
                }
                AppGlassIconButton(onClick = { load(force = true) }, enabled = expanded && !loading) {
                    Icon(Icons.Rounded.Refresh, null)
                }
            }

            Text(
                helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                when {
                    loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("数据加载中…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    error != null -> {
                        Text(
                            error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    cacheItems.isEmpty() -> {
                        Text(
                            "暂无番剧缓存。请先在核心里完成一次搜索/匹配/弹幕请求后再回来查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items(items = cacheItems) { item ->
                                AnimeCacheCard(
                                    item = item,
                                    currentKey = currentKey,
                                    onFillMergeEntity = onFillMergeEntity,
                                    mergeFillRole = mergeFillRole,
                                    mergeFillLabel = mergeFillLabel,
                                    onFillOffsetEntity = onFillOffsetEntity,
                                    onAddSourcePair = onAddSourcePair
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCacheCard(
    item: AnimeCacheItem,
    currentKey: String,
    onFillMergeEntity: ((String, String, String) -> Unit)?,
    mergeFillRole: String? = null,
    mergeFillLabel: String = "填入",
    onFillOffsetEntity: ((String, String) -> Unit)?,
    onAddSourcePair: ((String) -> Unit)?,
    compact: Boolean = false,
) {
    val cleanTitle = cleanCacheAnimeTitle(item.animeTitle)
    val source = item.source.trim()
    val episodeCount = item.episodes.takeIf { it > 0 } ?: item.episodeCount.takeIf { it > 0 } ?: item.links.size
    val imageUrl = item.imageUrl.trim()
    val coverSize = if (compact) 54.dp else 72.dp
    val titleStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall
    val subtitleStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimeCacheCover(imageUrl = imageUrl, size = coverSize)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        cleanTitle.ifBlank { item.animeTitle.ifBlank { "未命名" } },
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "[$source] · $episodeCount 集",
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val buttons = buildList {
                when (currentKey) {
                    KEY_CUSTOM_MERGE_RULES -> {
                        if (mergeFillRole != null) {
                            add(mergeFillLabel to { onFillMergeEntity?.invoke(mergeFillRole, cleanTitle, source) })
                        } else {
                            add("填 A" to { onFillMergeEntity?.invoke("sec", cleanTitle, source) })
                            add("填 B" to { onFillMergeEntity?.invoke("prim", cleanTitle, source) })
                        }
                    }

                    KEY_DANMU_OFFSET -> {
                        add("填入" to { onFillOffsetEntity?.invoke(cleanTitle, source) })
                    }

                    KEY_MERGE_SOURCE_PAIRS -> {
                        onAddSourcePair?.let { callback ->
                            add("加入来源" to { callback(source) })
                        }
                    }
                }
            }

            if (buttons.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { (label, action) ->
                        AppGlassAssistChip(onClick = { action() }, label = { Text(label) })
                    }
                }
            }

            val children = item.mergedChildren.filterNot { it.isHiddenChild }
            if (children.isNotEmpty()) {
                Text(
                    "已合并来源 ${children.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp)) {
                    children.forEach { child ->
                        AnimeCacheCard(
                            item = child,
                            currentKey = currentKey,
                            onFillMergeEntity = onFillMergeEntity,
                            mergeFillRole = mergeFillRole,
                            mergeFillLabel = mergeFillLabel,
                            onFillOffsetEntity = onFillOffsetEntity,
                            onAddSourcePair = onAddSourcePair,
                            compact = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCacheCover(imageUrl: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    if (imageUrl.isBlank()) {
        Box(
            modifier = Modifier
                .size(width = size, height = size * 1.35f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ImageNotSupported, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = size, height = size * 1.35f)
            .clip(RoundedCornerShape(12.dp))
    )
}
