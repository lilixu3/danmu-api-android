package com.example.danmuapiapp.ui.screen.push

import com.example.danmuapiapp.ui.component.AppDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import android.content.ClipData
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
import com.example.danmuapiapp.ui.screen.tools.*
import kotlinx.coroutines.launch
import java.net.URI

@Composable fun PushDanmuScreen(onBack: () -> Unit, viewModel: PushDanmuViewModel = hiltViewModel()) {
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()
    val env by viewModel.envVars.collectAsStateWithLifecycle()
    val defaultFont = env["DANMU_FONT_SIZE"]?.toIntOrNull()
    val localBase = RuntimeUrlParser.extractBase(runtime.localUrl)
    val lanBase = RuntimeUrlParser.extractBase(runtime.lanUrl)
    val lanIp = remember(runtime.lanUrl) { PushLanScanner.resolveSelfLanIpv4(runtime.lanUrl).orEmpty() }
    var target by rememberSaveable { mutableStateOf(defaultPushTarget(lanIp)) }
    var source by rememberSaveable { mutableStateOf(lanBase.ifBlank { localBase }) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableStateOf("0") }
    var font by rememberSaveable { mutableStateOf("") }
    var showTarget by rememberSaveable { mutableStateOf(false) }
    var jump by rememberSaveable { mutableStateOf("") }
    var highlighted by rememberSaveable { mutableStateOf<Long?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    LaunchedEffect(localBase, lanBase) { if (source.isBlank()) source = lanBase.ifBlank { localBase } }
    BackHandler(viewModel.currentAnime != null) { viewModel.backToAnimeList() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolHeader("弹幕推送", onBack) {
            if (viewModel.resultText.isNotEmpty()) ToolIcon(Icons.Outlined.CopyAll, "复制推送结果", {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("推送结果", viewModel.resultText))) }
            }, size = 40)
            ToolIcon(Icons.Outlined.SettingsInputAntenna, "推送目标与局域网", { showTarget = true }, size = 40)
        }
        LazyColumn(state = list, contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolTitle("搜索动漫", Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                            Text("仅支持 OK影视", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    ToolInput(keyword, { keyword = it }, "例如：凡人修仙传", icon = Icons.Outlined.Search,
                        onSubmit = { viewModel.searchAnime(source, keyword) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ToolHint("时间偏移"); ToolInput(offset, { offset = it }, "0", number = true); ToolHint("秒，可负数")
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ToolHint("弹幕大小"); ToolInput(font, { font = it }, defaultFont?.toString() ?: "默认", number = true)
                            ToolHint(defaultFont?.let { "默认 $it" } ?: "留空=默认")
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.DevicesOther, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("目标：${summarizePushHost(target)}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                ToolHint("源：${summarizePushHost(source)}", maxLines = 1)
                            }
                            TextButton({ showTarget = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("更改", fontSize = 12.sp) }
                        }
                    }
                    ToolButton("搜索", { viewModel.searchAnime(source, keyword) }, Modifier.fillMaxWidth(), Icons.Outlined.Search,
                        busy = viewModel.isSearching)
                }
            }
            val error = localError ?: viewModel.errorMessage
            if (error != null) item { ToolError(error) { localError = null; viewModel.clearError() } }
            if (viewModel.statusText.isNotBlank()) item { ToolHint(viewModel.statusText) }
            if (viewModel.isLoadingEpisodes) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val anime = viewModel.currentAnime
            if (anime == null) {
                if (viewModel.animeCandidates.isEmpty()) item { ToolEmpty(if (viewModel.hasSearchedAnime) "未找到匹配动漫" else "搜索后选择番剧与剧集",
                    Icons.Outlined.Cast, "选择剧集后即可推送到 OK影视") }
                itemsIndexed(viewModel.animeCandidates, key = { _, item -> item.animeId }) { _, candidate ->
                    ToolCard(onClick = { viewModel.openAnimeDetail(source, candidate) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnimePosterThumbnail(candidate.imageUrl, candidate.title, Modifier.size(58.dp, 82.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ToolTitle(candidate.title, size = 14)
                                ToolHint(listOf(candidate.source, candidate.typeDescription).filter { it.isNotBlank() }.joinToString(" · "))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ToolHint(if (candidate.episodeCount > 0) "${candidate.episodeCount} 集" else "集数未知", Modifier.weight(1f))
                                    Text("选择剧集", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                item { ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ToolTitle(anime.title, size = 14)
                            ToolHint("共 ${viewModel.episodeCandidates.size} 集 · 目标 ${summarizePushHost(target)}")
                        }
                        TextButton(viewModel::backToAnimeList) { Text("返回结果", fontSize = 12.sp) }
                    }
                } }
                item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolInput(jump, { jump = it }, "跳到第几集（输入集数）", Modifier.weight(1f), Icons.Outlined.MyLocation, number = true)
                    ToolButton("定位", {
                        val number = jump.toIntOrNull()
                        val index = viewModel.episodeCandidates.indexOfFirst { it.episodeNumber == number }
                        if (index < 0) localError = "找不到第 ${jump.ifBlank { "-" }} 集，请输入有效集数"
                        else {
                            highlighted = viewModel.episodeCandidates[index].episodeId
                            // Use the actual item key: conditional notices add/remove items above the episodes.
                            scope.launch {
                                val targetKey = "push-episode-${viewModel.episodeCandidates[index].episodeId}"
                                val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == targetKey }
                                if (visible != null) list.animateScrollToItem(visible.index)
                                else {
                                    val headerCount = 1 + (if (error != null) 1 else 0) + (if (viewModel.statusText.isNotBlank()) 1 else 0) +
                                        (if (viewModel.isLoadingEpisodes) 1 else 0) + 2
                                    list.animateScrollToItem(headerCount + index)
                                }
                            }
                        }
                    }, enabled = jump.isNotBlank())
                } }
                itemsIndexed(viewModel.episodeCandidates, key = { _, episode -> "push-episode-${episode.episodeId}" }) { _, episode ->
                    val highlight = episode.episodeId == highlighted || episode.episodeId == viewModel.lastPushedEpisodeId
                    Surface(shape = RoundedCornerShape(12.dp), color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f)
                        else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                                Text("第${episode.episodeNumber}集", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(episode.title.ifBlank { "弹幕 ${episode.episodeId}" }, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                ToolHint("EID ${episode.episodeId}")
                            }
                            ToolButton("推送", { viewModel.pushEpisode(target, source, episode, offset, font, defaultFont) },
                                icon = Icons.Outlined.CloudUpload, busy = episode.episodeId in viewModel.pushingEpisodeIds)
                        }
                    }
                }
            }
            if (viewModel.resultText.isNotBlank()) item { ToolCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolTitle("推送结果", Modifier.weight(1f))
                    ToolIcon(Icons.Outlined.ContentCopy, "复制结果", { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("推送结果", viewModel.resultText))) } })
                    ToolIcon(Icons.Outlined.Close, "清空结果", viewModel::clearResult)
                }
                androidx.compose.foundation.text.selection.SelectionContainer { Text(viewModel.resultText, fontSize = 11.sp, lineHeight = 16.sp) }
            } }
            if (viewModel.pushHistory.isNotEmpty()) item { ToolCard {
                ToolTitle("最近推送")
                viewModel.pushHistory.forEach { ToolHint(it) }
            } }
        }
    }
    if (showTarget) AppDialog(onDismissRequest = { showTarget = false }, scrollContent = false, title = { ToolTitle("推送目标", size = 16) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolHint("推送地址")
            ToolInput(target, { target = it }, "192.168.1.8:9978 或带 path= 的完整地址")
            ToolHint("只填 IP:端口会自动补齐推送接口；完整地址中的 path 参数会替换为弹幕地址。当前支持 OK影视。")
            ToolTitle("弹幕源地址", size = 12)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip("本机 127.0.0.1", source == localBase, { source = localBase })
                ToolChip("局域网 ${lanIp.ifBlank { "无 IP" }}", source == lanBase, { if (lanBase.isNotBlank()) source = lanBase })
            }
            ToolInput(source, { source = it }, "弹幕源 Base URL")
            ToolHint("推送到盒子时请使用盒子能够访问的局域网源地址。")
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolTitle("局域网设备", Modifier.weight(1f), size = 12)
                ToolButton("扫描", { viewModel.scanLanDevices(runtime.lanUrl) }, icon = Icons.Outlined.Radar, busy = viewModel.isScanningLan)
            }
            ToolHint("本机 ${lanIp.ifBlank { "未获取 IP，请连接 Wi-Fi" }}\n${viewModel.lanScanStatus}")
            if (viewModel.lanDevices.isNotEmpty()) viewModel.lanDevices.forEach { device ->
                ToolListRow(if (device.isSelf) "本机" else "设备", device.latencyMs?.let { "${it}ms" }.orEmpty(), device.ip,
                    device.supportLabel, { target = device.targetTemplate }, actions = {
                        ToolIcon(Icons.Outlined.ContentCopy, "复制设备地址", { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("设备地址", device.targetTemplate))) } })
                    })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ target = defaultPushTarget(lanIp) }) { Text("填入默认", fontSize = 11.sp) }
                TextButton(viewModel::clearLanScan) { Text("清空扫描", fontSize = 11.sp) }
            }
        } }, confirmButton = { ToolButton("完成", { showTarget = false }) })
}
private fun defaultPushTarget(ip: String): String = "http://${ip.ifBlank { "127.0.0.1" }}:9978/action?do=refresh&type=danmaku&path="
private fun summarizePushHost(raw: String): String = runCatching { URI(if (raw.contains("://")) raw else "http://$raw").authority }
    .getOrNull()?.takeIf { it.isNotBlank() } ?: raw.take(32).ifBlank { "未设置" }
