package com.example.danmuapiapp.ui.screen.push

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone
import java.net.URI

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PushDanmuScreen(
    onBack: () -> Unit,
    viewModel: PushDanmuViewModel = hiltViewModel()
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val envVars by viewModel.envVars.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    val envDanmuFontSize = envVars["DANMU_FONT_SIZE"]?.trim()?.toIntOrNull()

    val localLanIp = remember(runtimeState.lanUrl) {
        PushLanScanner.resolveSelfLanIpv4(runtimeState.lanUrl).orEmpty()
    }
    val localBase = remember(runtimeState.localUrl) {
        RuntimeUrlParser.extractBase(runtimeState.localUrl)
    }
    val lanBase = remember(runtimeState.lanUrl) {
        RuntimeUrlParser.extractBase(runtimeState.lanUrl)
    }
    val defaultTarget = remember(localLanIp) { buildDefaultTargetTemplate(localLanIp) }

    var targetPrefix by rememberSaveable(defaultTarget) { mutableStateOf(defaultTarget) }
    var sourceBase by rememberSaveable(runtimeState.localUrl, runtimeState.lanUrl) {
        mutableStateOf(lanBase)
    }
    var keyword by rememberSaveable { mutableStateOf("") }
    var danmuOffsetRaw by rememberSaveable { mutableStateOf("0") }
    var danmuFontSizeRaw by rememberSaveable { mutableStateOf("") }
    var danmuFontSizeInitialized by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(envDanmuFontSize) {
        if (!danmuFontSizeInitialized) {
            danmuFontSizeRaw = envDanmuFontSize?.toString().orEmpty()
            danmuFontSizeInitialized = true
        }
    }

    val currentAnime = viewModel.currentAnime
    val inEpisodeDetail = currentAnime != null

    BackHandler(enabled = inEpisodeDetail) {
        viewModel.backToAnimeList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    if (inEpisodeDetail) viewModel.backToAnimeList() else onBack()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (inEpisodeDetail) checkNotNull(currentAnime).title else "弹幕推送",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (inEpisodeDetail) {
                        "可返回搜索结果重新选择动漫"
                    } else {
                        "搜索优先，参数与目标也始终可见"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Settings, "目标与设置", Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (!inEpisodeDetail) {
                item {
                    SearchControlCard(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        targetPrefix = targetPrefix,
                        sourceBase = sourceBase,
                        danmuOffsetRaw = danmuOffsetRaw,
                        onDanmuOffsetChange = { danmuOffsetRaw = it },
                        danmuFontSizeRaw = danmuFontSizeRaw,
                        onDanmuFontSizeChange = { danmuFontSizeRaw = it },
                        envDanmuFontSize = envDanmuFontSize,
                        isSearching = viewModel.isSearching,
                        onSearch = { viewModel.searchAnime(sourceBase, keyword) },
                        onOpenSettings = { showSettingsSheet = true }
                    )
                }
            } else {
                val anime = checkNotNull(currentAnime)
                item {
                    DetailControlCard(
                        anime = anime,
                        targetPrefix = targetPrefix,
                        sourceBase = sourceBase,
                        danmuOffsetRaw = danmuOffsetRaw,
                        onDanmuOffsetChange = { danmuOffsetRaw = it },
                        danmuFontSizeRaw = danmuFontSizeRaw,
                        onDanmuFontSizeChange = { danmuFontSizeRaw = it },
                        envDanmuFontSize = envDanmuFontSize,
                        episodeCount = viewModel.episodeCandidates.size,
                        onBackToResults = viewModel::backToAnimeList,
                        onOpenSettings = { showSettingsSheet = true }
                    )
                }
            }

            if (!inEpisodeDetail) {
                when {
                    viewModel.isSearching -> {
                        item {
                            LoadingCard(
                                title = "正在搜索动漫",
                                subtitle = "请稍等，搜索结果马上出现"
                            )
                        }
                    }

                    viewModel.animeCandidates.isNotEmpty() -> {
                        item {
                            SectionHeader(
                                title = "搜索结果",
                                subtitle = "点击动漫进入剧集列表，之后仍可返回这里切换结果"
                            )
                        }
                        items(viewModel.animeCandidates, key = { it.animeId }) { anime ->
                            val loading = viewModel.isLoadingEpisodes && viewModel.loadingAnimeId == anime.animeId
                            AnimeResultRow(
                                anime = anime,
                                loading = loading,
                                onClick = { viewModel.openAnimeDetail(sourceBase, anime) }
                            )
                        }
                    }

                    viewModel.hasSearchedAnime -> {
                        item {
                            EmptyStateCard(
                                title = "没有找到匹配动漫",
                                subtitle = "换一个关键词试试，或确认当前源地址与目标设置是否正确"
                            )
                        }
                    }

                    else -> {
                        item {
                            EmptyStateCard(
                                title = "输入动漫名开始搜索",
                                subtitle = "推送仅支持 OK影视，时间偏移和弹幕大小已放到首页，避免被忽略"
                            )
                        }
                    }
                }
            } else {
                when {
                    viewModel.isLoadingEpisodes -> {
                        item {
                            LoadingCard(
                                title = "正在加载剧集",
                                subtitle = "请稍等，剧集列表马上出现"
                            )
                        }
                    }

                    viewModel.episodeCandidates.isEmpty() -> {
                        item {
                            EmptyStateCard(
                                title = "该动漫暂无可推送剧集",
                                subtitle = "可以返回搜索结果后重新选择其他动漫"
                            )
                        }
                    }

                    else -> {
                        items(viewModel.episodeCandidates, key = { it.episodeId }) { episode ->
                            val pushing = viewModel.pushingEpisodeIds.contains(episode.episodeId)
                            EpisodePushRow(
                                episode = episode,
                                pushing = pushing,
                                onPush = {
                                    viewModel.pushEpisode(
                                        targetInput = targetPrefix,
                                        baseUrl = sourceBase,
                                        episode = episode,
                                        offsetRaw = danmuOffsetRaw,
                                        fontRaw = danmuFontSizeRaw,
                                        envDanmuFontSize = envDanmuFontSize
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (viewModel.resultText.isNotBlank()) {
                item {
                    ResultCard(
                        text = viewModel.resultText,
                        onCopy = {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("推送结果", viewModel.resultText)
                            )
                        },
                        onClear = viewModel::clearResult
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsSheet(
            targetPrefix = targetPrefix,
            onTargetChange = { targetPrefix = it },
            sourceBase = sourceBase,
            onSourceBaseChange = { sourceBase = it },
            localBase = localBase,
            lanBase = lanBase,
            localLanIp = localLanIp,
            isScanning = viewModel.isScanningLan,
            status = viewModel.lanScanStatus,
            devices = viewModel.lanDevices,
            selectedTarget = targetPrefix,
            onFillDefaultTarget = { targetPrefix = buildDefaultTargetTemplate(localLanIp) },
            onCopyLocalIp = {
                if (localLanIp.isNotBlank()) {
                    clipboardManager.nativeClipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("本机局域网IP", localLanIp)
                    )
                }
            },
            onUseLocal = { sourceBase = localBase },
            onUseLan = { sourceBase = lanBase },
            onScan = { viewModel.scanLanDevices(runtimeState.lanUrl) },
            onClearScan = viewModel::clearLanScan,
            onUseDevice = { targetPrefix = it.targetTemplate },
            onCopyDevice = { device ->
                clipboardManager.nativeClipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("推送目标", device.targetTemplate)
                )
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (viewModel.errorMessage != null) {
        AppBottomSheetDialog(
            onDismissRequest = { viewModel.clearError() },
            style = AppBottomSheetStyle.Confirm,
            tone = AppBottomSheetTone.Danger,
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun SearchControlCard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    targetPrefix: String,
    sourceBase: String,
    danmuOffsetRaw: String,
    onDanmuOffsetChange: (String) -> Unit,
    danmuFontSizeRaw: String,
    onDanmuFontSizeChange: (String) -> Unit,
    envDanmuFontSize: Int?,
    isSearching: Boolean,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("搜索动漫", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "先搜动漫，再进入剧集逐集推送",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("仅支持 OK影视") },
                    leadingIcon = { Icon(Icons.Rounded.Verified, null, modifier = Modifier.size(16.dp)) }
                )
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                label = { Text("关键词") },
                placeholder = { Text("例如：凡人修仙传") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactParamField(
                    title = "时间偏移",
                    value = danmuOffsetRaw,
                    onValueChange = onDanmuOffsetChange,
                    placeholder = "0",
                    supporting = "秒，可负数",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                CompactParamField(
                    title = "弹幕大小",
                    value = danmuFontSizeRaw,
                    onValueChange = onDanmuFontSizeChange,
                    placeholder = envDanmuFontSize?.toString() ?: "默认",
                    supporting = if (envDanmuFontSize != null) {
                        "默认 $envDanmuFontSize"
                    } else {
                        "留空=默认"
                    },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("当前目标", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = summarizeTarget(targetPrefix),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "源：${summarizeBase(sourceBase)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Text("更改")
                    }
                }
            }

            Button(
                onClick = onSearch,
                enabled = !isSearching,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("搜索中...")
                } else {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("搜索动漫")
                }
            }

            Text(
                text = "推送仅适配 OK影视；时间偏移与弹幕大小会随每次推送一起生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailControlCard(
    anime: PushAnimeCandidate,
    targetPrefix: String,
    sourceBase: String,
    danmuOffsetRaw: String,
    onDanmuOffsetChange: (String) -> Unit,
    danmuFontSizeRaw: String,
    onDanmuFontSizeChange: (String) -> Unit,
    envDanmuFontSize: Int?,
    episodeCount: Int,
    onBackToResults: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "共 $episodeCount 集 · 当前仅支持 OK影视推送",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactParamField(
                    title = "时间偏移",
                    value = danmuOffsetRaw,
                    onValueChange = onDanmuOffsetChange,
                    placeholder = "0",
                    supporting = "秒，可负数",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                CompactParamField(
                    title = "弹幕大小",
                    value = danmuFontSizeRaw,
                    onValueChange = onDanmuFontSizeChange,
                    placeholder = envDanmuFontSize?.toString() ?: "默认",
                    supporting = if (envDanmuFontSize != null) {
                        "默认 $envDanmuFontSize"
                    } else {
                        "留空=默认"
                    },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("当前目标", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = summarizeTarget(targetPrefix),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "源：${summarizeBase(sourceBase)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBackToResults, modifier = Modifier.weight(1f)) {
                    Text("返回搜索结果")
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                    Text("目标与设置")
                }
            }
        }
    }
}

@Composable
private fun CompactParamField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    supporting: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(title) },
        placeholder = { Text(placeholder) },
        supportingText = { Text(supporting) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnimeResultRow(
    anime: PushAnimeCandidate,
    loading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${anime.episodeCount} 集 · Anime ID ${anime.animeId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EpisodePushRow(
    episode: PushEpisodeCandidate,
    pushing: Boolean,
    onPush: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "第${episode.episodeNumber}集",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "EID ${episode.episodeId}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onPush, enabled = !pushing) {
                if (pushing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("推送中")
                } else {
                    Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("推送")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(
    targetPrefix: String,
    onTargetChange: (String) -> Unit,
    sourceBase: String,
    onSourceBaseChange: (String) -> Unit,
    localBase: String,
    lanBase: String,
    localLanIp: String,
    isScanning: Boolean,
    status: String,
    devices: List<PushLanDeviceCandidate>,
    selectedTarget: String,
    onFillDefaultTarget: () -> Unit,
    onCopyLocalIp: () -> Unit,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    onScan: () -> Unit,
    onClearScan: () -> Unit,
    onUseDevice: (PushLanDeviceCandidate) -> Unit,
    onCopyDevice: (PushLanDeviceCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Form,
        tone = AppBottomSheetTone.Brand,
        title = { Text("目标与设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "当前推送仅支持 OK影视，其他播放器暂不保证兼容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                SheetBlock(title = "推送目标") {
                    OutlinedTextField(
                        value = targetPrefix,
                        onValueChange = onTargetChange,
                        label = { Text("推送目标模板") },
                        supportingText = { Text("剧集链接会自动拼接到 path= 后面") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(onClick = onFillDefaultTarget, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("默认模板")
                        }
                        OutlinedButton(onClick = onCopyLocalIp, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Wifi, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (localLanIp.isBlank()) "本机 IP" else localLanIp)
                        }
                    }
                }

                SheetBlock(title = "局域网设备") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onScan,
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("扫描中")
                            } else {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("扫描设备")
                            }
                        }
                        OutlinedButton(
                            onClick = onClearScan,
                            enabled = !isScanning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空")
                        }
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (devices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            devices.forEach { device ->
                                DeviceChoiceRow(
                                    device = device,
                                    isSelected = selectedTarget == device.targetTemplate,
                                    onUseDevice = { onUseDevice(device) },
                                    onCopyDevice = { onCopyDevice(device) }
                                )
                            }
                        }
                    }
                }

                SheetBlock(title = "弹幕源") {
                    OutlinedTextField(
                        value = sourceBase,
                        onValueChange = onSourceBaseChange,
                        label = { Text("弹幕源 Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = sourceBase == localBase,
                            onClick = onUseLocal,
                            label = { Text("本机") }
                        )
                        FilterChip(
                            selected = sourceBase == lanBase,
                            onClick = onUseLan,
                            label = { Text("局域网") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun SheetBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceChoiceRow(
    device: PushLanDeviceCandidate,
    isSelected: Boolean,
    onUseDevice: () -> Unit,
    onCopyDevice: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = device.ip,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        text = when {
                            device.verifiedPushApi -> "已验证支持 OK影视 推送接口"
                            device.port9978Open -> "9978 端口可达"
                            else -> "未检测到可用推送接口"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    AssistChip(onClick = {}, label = { Text("当前目标") })
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (device.isSelf) {
                    AssistChip(onClick = {}, label = { Text("本机") })
                }
                if (device.latencyMs != null) {
                    AssistChip(onClick = {}, label = { Text("${device.latencyMs}ms") })
                }
                AssistChip(
                    onClick = {},
                    label = { Text(device.supportLabel) },
                    leadingIcon = {
                        Icon(
                            if (device.verifiedPushApi) Icons.Rounded.Verified else Icons.Rounded.Language,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUseDevice,
                    enabled = device.port9978Open,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSelected) "已选中" else "选为目标")
                }
                OutlinedButton(
                    onClick = onCopyDevice,
                    enabled = device.port9978Open,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制")
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultCard(
    text: String,
    onCopy: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近结果",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.ContentCopy, "复制", Modifier.size(16.dp))
                    }
                    IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.RestartAlt, "清空", Modifier.size(16.dp))
                    }
                }
            }
            Text(
                text = compactResultText(text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildDefaultTargetTemplate(localLanIp: String): String {
    val host = localLanIp.trim().takeIf { it.isNotBlank() && !it.startsWith("127.") } ?: "127.0.0.1"
    return "http://$host:9978/action?do=refresh&type=danmaku&path="
}

private fun summarizeTarget(targetPrefix: String): String {
    val trimmed = targetPrefix.trim()
    if (trimmed.isBlank()) return "未设置推送目标"
    val host = runCatching { URI(trimmed).host }.getOrNull()
    return when {
        !host.isNullOrBlank() -> host
        trimmed.length <= 32 -> trimmed
        else -> trimmed.take(32) + "…"
    }
}

private fun summarizeBase(sourceBase: String): String {
    val trimmed = sourceBase.trim()
    if (trimmed.isBlank()) return "未设置"
    val host = runCatching { URI(trimmed).host }.getOrNull()
    return when {
        !host.isNullOrBlank() -> host
        trimmed.length <= 28 -> trimmed
        else -> trimmed.take(28) + "…"
    }
}

private fun compactResultText(text: String): String {
    val compact = text.trim().replace("\n", " · ")
    return if (compact.length <= 140) compact else compact.take(140) + "…"
}
