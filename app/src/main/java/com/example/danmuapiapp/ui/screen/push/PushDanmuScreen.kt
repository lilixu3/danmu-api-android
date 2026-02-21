package com.example.danmuapiapp.ui.screen.push

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.RuntimeUrlParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushDanmuScreen(
    onBack: () -> Unit,
    viewModel: PushDanmuViewModel = hiltViewModel()
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val envVars by viewModel.envVars.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    val envDanmuFontSize = envVars["DANMU_FONT_SIZE"]?.trim()?.toIntOrNull()

    var targetPrefix by rememberSaveable {
        mutableStateOf("http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=")
    }
    var sourceBase by rememberSaveable(runtimeState.localUrl, runtimeState.lanUrl) {
        mutableStateOf(RuntimeUrlParser.extractBase(runtimeState.lanUrl))
    }
    var keyword by rememberSaveable { mutableStateOf("") }
    var danmuOffsetRaw by rememberSaveable { mutableStateOf("0") }
    var danmuFontSizeRaw by rememberSaveable { mutableStateOf("") }
    var danmuFontSizeInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(envDanmuFontSize) {
        if (!danmuFontSizeInitialized) {
            danmuFontSizeRaw = envDanmuFontSize?.toString().orEmpty()
            danmuFontSizeInitialized = true
        }
    }

    val inEpisodeDetail = viewModel.currentAnime != null

    BackHandler(enabled = inEpisodeDetail) {
        viewModel.backToAnimeList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (inEpisodeDetail) viewModel.backToAnimeList() else onBack()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                }
                Column {
                    Text(
                        if (inEpisodeDetail) viewModel.currentAnime?.title ?: "剧集详情"
                        else "弹幕推送",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1
                    )
                    Text(
                        if (inEpisodeDetail) {
                            "共 ${viewModel.episodeCandidates.size} 集 · ${viewModel.pushingEpisodeIds.size} 推送中"
                        } else {
                            buildString {
                                append("搜索动漫并推送弹幕")
                                if (viewModel.animeCandidates.isNotEmpty()) {
                                    append(" · ${viewModel.animeCandidates.size} 个结果")
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (viewModel.resultText.isNotBlank()) {
                FilledTonalIconButton(
                    onClick = { viewModel.clearResult() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ClearAll, "清空结果", Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Config section
            item {
                PushConfigSection(
                    targetPrefix = targetPrefix,
                    onTargetChange = { targetPrefix = it },
                    sourceBase = sourceBase,
                    onSourceBaseChange = { sourceBase = it },
                    onUseLocal = { sourceBase = RuntimeUrlParser.extractBase(runtimeState.localUrl) },
                    onUseLan = { sourceBase = RuntimeUrlParser.extractBase(runtimeState.lanUrl) },
                    danmuOffsetRaw = danmuOffsetRaw,
                    onDanmuOffsetChange = { danmuOffsetRaw = it },
                    danmuFontSizeRaw = danmuFontSizeRaw,
                    onDanmuFontSizeChange = { danmuFontSizeRaw = it },
                    envDanmuFontSize = envDanmuFontSize,
                    onResetDanmuOptions = {
                        danmuOffsetRaw = "0"
                        danmuFontSizeRaw = envDanmuFontSize?.toString().orEmpty()
                    }
                )
            }

            // Search or episode detail
            if (!inEpisodeDetail) {
                // Search section
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "搜索动漫",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                label = { Text("关键词") },
                                placeholder = { Text("示例：凡人修仙传") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = { viewModel.searchAnime(sourceBase, keyword) },
                                enabled = !viewModel.isSearching,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (viewModel.isSearching) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("搜索中...")
                                } else {
                                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("搜索")
                                }
                            }
                            if (viewModel.isSearching) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                )
                            } else if (viewModel.hasSearchedAnime && viewModel.animeCandidates.isEmpty()) {
                                Text(
                                    "没有搜索到匹配动漫",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                // Anime results
                if (viewModel.animeCandidates.isNotEmpty()) {
                    item {
                        Text(
                            "搜索结果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
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
            } else {
                // Episode detail
                val anime = viewModel.currentAnime
                if (anime != null) {
                    item {
                        AnimeDetailHeader(anime = anime)
                    }

                    if (viewModel.isLoadingEpisodes) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                )
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                )
                            }
                        }
                    } else if (viewModel.episodeCandidates.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Rounded.CloudOff, null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "该动漫暂无可推送剧集",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
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

            // Result display
            if (viewModel.resultText.isNotBlank()) {
                item {
                    ResultSection(
                        text = viewModel.resultText,
                        onCopy = {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("推送结果", viewModel.resultText)
                            )
                        }
                    )
                }
            }
        }
    }

    // Error dialog
    if (viewModel.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun PushConfigSection(
    targetPrefix: String,
    onTargetChange: (String) -> Unit,
    sourceBase: String,
    onSourceBaseChange: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    danmuOffsetRaw: String,
    onDanmuOffsetChange: (String) -> Unit,
    danmuFontSizeRaw: String,
    onDanmuFontSizeChange: (String) -> Unit,
    envDanmuFontSize: Int?,
    onResetDanmuOptions: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "推送配置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "弹幕参数仅本次推送生效，不写入 .env。兼容 OK 影视参数：offset / fontSize。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = targetPrefix,
                onValueChange = onTargetChange,
                label = { Text("推送目标地址") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            OutlinedTextField(
                value = sourceBase,
                onValueChange = onSourceBaseChange,
                label = { Text("弹幕源 Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onUseLocal, label = { Text("本机") })
                AssistChip(onClick = onUseLan, label = { Text("局域网") })
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            OutlinedTextField(
                value = danmuOffsetRaw,
                onValueChange = onDanmuOffsetChange,
                label = { Text("时间偏移（秒，可为负）") },
                supportingText = { Text("正数=弹幕延后；负数=提前（示例：-1.5）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            OutlinedTextField(
                value = danmuFontSizeRaw,
                onValueChange = onDanmuFontSizeChange,
                label = { Text("弹幕大小（fontSize）") },
                supportingText = {
                    Text(
                        if (envDanmuFontSize != null) {
                            "当前核心默认 DANMU_FONT_SIZE=$envDanmuFontSize，留空表示不覆盖"
                        } else {
                            "留空表示不覆盖核心默认值"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResetDanmuOptions) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重置弹幕参数")
                }
            }
        }
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !loading, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(anime.title, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "ID:${anime.animeId}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        "${anime.episodeCount} 集",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward, null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun AnimeDetailHeader(anime: PushAnimeCandidate) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(anime.title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        "AnimeID: ${anime.animeId}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Text(
                    "官方标注 ${anime.episodeCount} 集",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode number badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    "${episode.episodeNumber}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(episode.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    "EID: ${episode.episodeId}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            FilledTonalIconButton(
                onClick = onPush,
                enabled = !pushing,
                modifier = Modifier.size(36.dp)
            ) {
                if (pushing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(Icons.Rounded.CloudUpload, "推送", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultSection(
    text: String,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "最近推送结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, "复制", Modifier.size(14.dp))
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
