package com.example.danmuapiapp.ui.screen.push

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.ui.screen.apitest.ApiAction
import com.example.danmuapiapp.ui.screen.apitest.ApiCard
import com.example.danmuapiapp.ui.screen.apitest.ApiEmpty
import com.example.danmuapiapp.ui.screen.apitest.ApiHint
import com.example.danmuapiapp.ui.screen.apitest.ApiInput
import com.example.danmuapiapp.ui.screen.apitest.ApiLoading
import com.example.danmuapiapp.ui.screen.apitest.ApiPageHeader
import com.example.danmuapiapp.ui.screen.apitest.ApiTag
import com.example.danmuapiapp.ui.screen.tools.ToolError

@Composable
internal fun PushEpisodePage(
    viewModel: PushDanmuViewModel,
    source: String,
    target: String,
    offset: String,
    font: String,
    defaultFont: Int?,
    onCopyResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val anime = viewModel.currentAnime
    val episodes = viewModel.episodeCandidates
    var filter by rememberSaveable(anime?.animeId) { mutableStateOf("") }
    var jump by rememberSaveable { mutableStateOf("") }
    var highlighted by rememberSaveable { mutableStateOf<Long?>(null) }
    var jumpTarget by remember { mutableStateOf<Int?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val visible = remember(episodes, filter) {
        episodes.filter {
            filter.isBlank() ||
                it.title.contains(filter.trim(), ignoreCase = true) ||
                it.episodeNumber.toString() == filter.trim()
        }
    }
    val error = localError ?: viewModel.errorMessage
    val headerCount = 2 + (if (error != null) 1 else 0) + (if (viewModel.statusText.isNotBlank()) 1 else 0)

    LaunchedEffect(jumpTarget, headerCount) {
        val index = jumpTarget ?: return@LaunchedEffect
        withFrameNanos { }
        listState.animateScrollToItem(headerCount + index)
        jumpTarget = null
    }

    Column(Modifier.fillMaxSize()) {
        ApiPageHeader(anime?.title ?: "选择剧集", onBack,
            listOfNotNull(
                anime?.sourceLabel?.takeIf { it.isNotBlank() },
                "${episodes.size} 集"
            ).joinToString(" · "))
        when {
            viewModel.isLoadingEpisodes -> ApiLoading()
            error != null && episodes.isEmpty() -> ApiEmpty("获取剧集失败", error) {
                ApiAction("重试", Icons.Rounded.Refresh, { anime?.let { viewModel.openAnimeDetail(source, it) } })
            }
            anime == null -> ApiEmpty("请返回选择番剧")
            episodes.isEmpty() -> ApiEmpty("该动漫暂无可推送剧集")
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "push-episode-filter") {
                    ApiInput(filter, { filter = it }, "筛选集数（例如 12）", Modifier.padding(vertical = 4.dp),
                        leading = Icons.Rounded.Search, trailing = {
                            if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Rounded.Close, "清空筛选", Modifier.size(16.dp)) }
                        })
                }
                item(key = "push-episode-jump") {
                    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ApiInput(jump, { jump = it }, "跳到第几集（输入集数）", Modifier.weight(1f), leading = Icons.Rounded.MyLocation)
                        ApiAction("定位", Icons.Rounded.MyLocation, {
                            val number = jump.toIntOrNull()
                            if (number == null) localError = "请输入有效集数"
                            else {
                                val index = episodes.indexOfFirst { it.episodeNumber == number }
                                if (index < 0) localError = "找不到第 $number 集"
                                else {
                                    localError = null
                                    filter = ""
                                    highlighted = episodes[index].episodeId
                                    jumpTarget = index
                                }
                            }
                        }, enabled = jump.isNotBlank())
                    }
                }
                if (error != null) item(key = "push-episode-error") {
                    ToolError(error) { localError = null; viewModel.clearError() }
                }
                if (viewModel.statusText.isNotBlank()) item(key = "push-episode-status") { ApiHint(viewModel.statusText) }
                if (visible.isEmpty()) item(key = "push-episode-empty") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        ApiHint("没有匹配的剧集，换个集数或清空筛选")
                    }
                }
                itemsIndexed(visible, key = { index, episode -> "$index:push-episode-${episode.episodeId}" }) { _, episode ->
                    val highlight = episode.episodeId == highlighted || episode.episodeId == viewModel.lastPushedEpisodeId
                    ApiCard(radius = 14, accent = if (highlight) MaterialTheme.colorScheme.primary else null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ApiTag("第${episode.episodeNumber}集")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(episode.title.ifBlank { "弹幕 ${episode.episodeId}" }, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                ApiHint("EID ${episode.episodeId}")
                            }
                            ApiAction("推送", Icons.Rounded.CloudUpload,
                                { viewModel.pushEpisode(target, source, episode, offset, font, defaultFont) },
                                busy = episode.episodeId in viewModel.pushingEpisodeIds)
                        }
                    }
                }
                if (viewModel.resultText.isNotBlank()) item(key = "push-episode-result") {
                    ApiCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("推送结果", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onCopyResult(viewModel.resultText) }) {
                                Icon(Icons.Rounded.ContentCopy, "复制结果", Modifier.size(18.dp))
                            }
                            IconButton(onClick = viewModel::clearResult) {
                                Icon(Icons.Rounded.Close, "清空结果", Modifier.size(18.dp))
                            }
                        }
                        SelectionContainer { Text(viewModel.resultText, fontSize = 11.sp, lineHeight = 16.sp) }
                    }
                }
                if (viewModel.pushHistory.isNotEmpty()) item(key = "push-episode-history") {
                    ApiCard {
                        Text("最近推送", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        viewModel.pushHistory.forEach { ApiHint(it) }
                    }
                }
            }
        }
    }
}
