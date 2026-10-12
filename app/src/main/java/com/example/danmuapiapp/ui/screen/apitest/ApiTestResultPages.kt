package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import com.example.danmuapiapp.ui.screen.download.buildAnimeSearchResultPresentation

@Composable
internal fun ApiAnimeResultsPage(viewModel: ApiTestViewModel, keyword: String, onBack: () -> Unit,
    onRetry: () -> Unit, onFavorite: () -> Unit, onSelect: (DownloadAnimeCandidate) -> Unit) {
    val candidates = viewModel.manualAnimeCandidates
    val favorite = viewModel.favoriteForKeyword(keyword)
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    Column(Modifier.fillMaxSize()) {
        ApiPageHeader("搜索结果", onBack, "$keyword · ${candidates.size} 个结果", actions = {
            TextButton(onClick = viewModel::toggleAnimeResultsLayout, enabled = !viewModel.isSavingAnimeResultsLayout,
                modifier = Modifier.size(40.dp), contentPadding = PaddingValues(0.dp)) {
                if (viewModel.isSavingAnimeResultsLayout) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Icon(if (viewModel.animeResultsGrid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                    if (viewModel.animeResultsGrid) "切换为列表布局" else "切换为双列布局", Modifier.size(18.dp))
            }
            TextButton(onClick = onFavorite, enabled = viewModel.favoriteOperation == null && keyword.isNotBlank()) {
                Icon(if (favorite == null) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark, null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (favorite == null) "收藏" else "已收藏", fontSize = 12.sp)
            }
        })
        when {
            viewModel.isSearchingAnime -> ApiLoading()
            viewModel.errorMessage != null && candidates.isEmpty() -> ApiEmpty("搜索失败", viewModel.errorMessage.orEmpty()) {
                ApiAction("重试", Icons.Rounded.Refresh, onRetry)
            }
            candidates.isEmpty() -> ApiEmpty("没有搜索到「$keyword」", "返回搜索页，换个关键词试试")
            viewModel.animeResultsGrid -> LazyVerticalGrid(columns = GridCells.Fixed(2), state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                gridItemsIndexed(candidates, key = { index, anime -> "$index:${anime.animeId}:${anime.directUrl}" }) { _, anime ->
                    ApiAnimeGridResultCard(anime) { onSelect(anime) }
                }
            }
            else -> LazyColumn(state = listState, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(candidates, key = { index, anime -> "$index:${anime.animeId}:${anime.directUrl}" }) { _, anime ->
                    val presentation = buildAnimeSearchResultPresentation(anime)
                    ApiCard(onClick = { onSelect(anime) }) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimePosterThumbnail(anime.imageUrl, anime.title, Modifier.width(66.dp).height(88.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(anime.title.trim(), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                ApiAnimeResultTags(presentation.source, presentation.title)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    ApiHint(presentation.episodeCountText)
                                    Spacer(Modifier.weight(1f))
                                    Text("选择剧集", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiAnimeGridResultCard(anime: DownloadAnimeCandidate, onClick: () -> Unit) {
    val presentation = buildAnimeSearchResultPresentation(anime)
    ApiCard(onClick = onClick) {
        AnimePosterThumbnail(anime.imageUrl, anime.title, Modifier.fillMaxWidth().aspectRatio(0.75f))
        Text(anime.title.trim(), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ApiAnimeResultTags(presentation.source, presentation.title)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(presentation.episodeCountText, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private val animeResultCategoryPattern = Regex("""【([^】]+)】|\[([^\]]+)\]""")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApiAnimeResultTags(source: String, title: String) {
    val sourceName = remember(source) { detailSourceName(source.substringBefore('&').substringBefore('＆')) }
    val category = remember(title) {
        animeResultCategoryPattern.findAll(title).lastOrNull()?.let { match ->
            match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
        }.orEmpty()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ApiAnimeResultTag(sourceName)
        if (category.isNotBlank()) ApiAnimeResultTag(category, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ApiAnimeResultTag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.08f)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = color, maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp,
                fontWeight = FontWeight.Medium, platformStyle = PlatformTextStyle(includeFontPadding = false)))
    }
}

@Composable
internal fun ApiEpisodePage(viewModel: ApiTestViewModel, onBack: () -> Unit, onRetry: () -> Unit,
    onSelect: (DownloadAnimeCandidate, DownloadEpisodeCandidate) -> Unit) {
    val anime = viewModel.manualCurrentAnime
    val episodes = viewModel.manualEpisodeCandidates
    var filter by rememberSaveable(anime?.animeId, anime?.directUrl) { mutableStateOf("") }
    val visible = remember(episodes, filter) {
        episodes.filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) ||
            it.episodeNumber.toString() == filter.trim() }
    }
    Column(Modifier.fillMaxSize()) {
        ApiPageHeader(anime?.title ?: "选择剧集", onBack,
            listOfNotNull(anime?.sourceLabel?.ifBlank { "未知来源" }, "${episodes.size} 集").joinToString(" · "))
        when {
            viewModel.isLoadingEpisodes -> ApiLoading()
            viewModel.errorMessage != null && episodes.isEmpty() -> ApiEmpty("获取剧集失败", viewModel.errorMessage.orEmpty()) {
                ApiAction("重试", Icons.Rounded.Refresh, onRetry)
            }
            anime == null -> ApiEmpty("请返回选择番剧")
            episodes.isEmpty() -> ApiEmpty("该动漫暂无剧集")
            else -> {
                ApiInput(filter, { filter = it }, "筛选集数（例如 12）", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    leading = Icons.Rounded.Search, trailing = {
                        if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Rounded.Close, "清空筛选", Modifier.size(16.dp)) }
                    })
                if (visible.isEmpty()) ApiEmpty("没有匹配的剧集", "换个集数或清空筛选")
                else LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(visible, key = { index, episode -> "$index:${episode.episodeId}:${episode.directUrl}" }) { _, episode ->
                        ApiCard(radius = 14, onClick = { if (!viewModel.isLoadingManualDanmu) onSelect(anime, episode) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(episode.title.ifBlank { "第 ${episode.episodeNumber} 集" }, Modifier.weight(1f),
                                    fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(8.dp))
                                Text("弹幕", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
