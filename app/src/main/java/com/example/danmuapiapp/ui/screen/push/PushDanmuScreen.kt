package com.example.danmuapiapp.ui.screen.push

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.RuntimeUrlParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PushDanmuScreen(
    onBack: () -> Unit,
    viewModel: PushDanmuViewModel = hiltViewModel()
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val envVars by viewModel.envVars.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val envDanmuFontSize = envVars["DANMU_FONT_SIZE"]?.trim()?.toIntOrNull()

    val localLanIp = remember(runtimeState.lanUrl) {
        PushLanScanner.resolveSelfLanIpv4(runtimeState.lanUrl).orEmpty()
    }
    val defaultTarget = remember(localLanIp) { buildDefaultTargetTemplate(localLanIp) }

    var targetPrefix by rememberSaveable(defaultTarget) { mutableStateOf(defaultTarget) }
    var sourceBase by rememberSaveable(runtimeState.localUrl, runtimeState.lanUrl) {
        mutableStateOf(RuntimeUrlParser.extractBase(runtimeState.lanUrl))
    }
    var keyword by rememberSaveable { mutableStateOf("") }
    var danmuOffsetRaw by rememberSaveable { mutableStateOf("0") }
    var danmuFontSizeRaw by rememberSaveable { mutableStateOf("") }
    var danmuFontSizeInitialized by rememberSaveable { mutableStateOf(false) }
    var showClientDialog by rememberSaveable { mutableStateOf(false) }
    var clientDialogHint by rememberSaveable { mutableStateOf<String?>(null) }

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
                        text = if (inEpisodeDetail) {
                            viewModel.currentAnime?.title ?: "剧集详情"
                        } else {
                            "弹幕推送工作台"
                        },
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = if (inEpisodeDetail) {
                            "共 ${viewModel.episodeCandidates.size} 集 · ${viewModel.pushingEpisodeIds.size} 推送中"
                        } else {
                            "设备发现 + 目标配置 + 动漫推送"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (viewModel.resultText.isNotBlank()) {
                IconButton(onClick = { viewModel.clearResult() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.RestartAlt, "清空结果", Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            item {
                PushConfigSection(
                    targetPrefix = targetPrefix,
                    onTargetChange = { targetPrefix = it },
                    sourceBase = sourceBase,
                    onSourceBaseChange = { sourceBase = it },
                    onUseLocal = { sourceBase = RuntimeUrlParser.extractBase(runtimeState.localUrl) },
                    onUseLan = { sourceBase = RuntimeUrlParser.extractBase(runtimeState.lanUrl) },
                    localLanIp = localLanIp,
                    onFillDefaultTarget = { targetPrefix = buildDefaultTargetTemplate(localLanIp) },
                    onCopyLocalIp = {
                        if (localLanIp.isNotBlank()) {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("本机局域网IP", localLanIp)
                            )
                        }
                    },
                    danmuOffsetRaw = danmuOffsetRaw,
                    onDanmuOffsetChange = { danmuOffsetRaw = it },
                    danmuFontSizeRaw = danmuFontSizeRaw,
                    onDanmuFontSizeChange = { danmuFontSizeRaw = it },
                    envDanmuFontSize = envDanmuFontSize,
                    onShowClientSupports = { showClientDialog = true },
                    onResetDanmuOptions = {
                        danmuOffsetRaw = "0"
                        danmuFontSizeRaw = envDanmuFontSize?.toString().orEmpty()
                    }
                )
            }

            item {
                LanScanSection(
                    isScanning = viewModel.isScanningLan,
                    status = viewModel.lanScanStatus,
                    devices = viewModel.lanDevices,
                    onScan = { viewModel.scanLanDevices(runtimeState.lanUrl) },
                    onClear = viewModel::clearLanScan,
                    onUseDevice = { targetPrefix = it.targetTemplate },
                    onCopyDevice = { device ->
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("推送目标", device.targetTemplate)
                        )
                    }
                )
            }

            if (!inEpisodeDetail) {
                item {
                    SearchSection(
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        isSearching = viewModel.isSearching,
                        hasSearched = viewModel.hasSearchedAnime,
                        hasResult = viewModel.animeCandidates.isNotEmpty(),
                        onSearch = { viewModel.searchAnime(sourceBase, keyword) }
                    )
                }

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
                val anime = viewModel.currentAnime
                if (anime != null) {
                    item { AnimeDetailHeader(anime = anime) }

                    if (viewModel.isLoadingEpisodes) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
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
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "该动漫暂无可推送剧集",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

    if (viewModel.errorMessage != null) {
        AppBottomSheetDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("知道了") }
            }
        )
    }

    if (showClientDialog) {
        SupportClientsDialog(
            supports = viewModel.clientSupports,
            hintText = clientDialogHint,
            onDismiss = { showClientDialog = false },
            onOpenDoc = { url ->
                val opened = runCatching {
                    uriHandler.openUri(url)
                    true
                }.getOrDefault(false)
                if (!opened) {
                    clipboardManager.nativeClipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("客户端文档", url)
                    )
                    clientDialogHint = "当前设备无法直接跳转，已复制链接"
                } else {
                    clientDialogHint = null
                }
            },
            onCopyDoc = { url ->
                clipboardManager.nativeClipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("客户端文档", url)
                )
                clientDialogHint = "链接已复制"
            }
        )
    }
}

@Composable
private fun SupportClientsDialog(
    supports: List<PushClientSupport>,
    hintText: String?,
    onDismiss: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onCopyDoc: (String) -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        title = { Text("支持客户端") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "点击“打开链接”可直接跳转浏览器；若跳转失败可用“复制链接”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                supports.forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, style = MaterialTheme.typography.labelLarge)
                                AssistChip(
                                    onClick = {},
                                    label = { Text(item.level.label) }
                                )
                            }
                            Text(
                                item.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                item.endpointExample,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onOpenDoc(item.docUrl) }) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("打开链接")
                                }
                                TextButton(onClick = { onCopyDoc(item.docUrl) }) {
                                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("复制链接")
                                }
                            }
                        }
                    }
                }
                hintText?.takeIf { it.isNotBlank() }?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private val PushClientSupportLevel.label: String
    get() = when (this) {
        PushClientSupportLevel.OfficialDoc -> "官方文档"
        PushClientSupportLevel.RepoDoc -> "仓库文档"
        PushClientSupportLevel.CompatibilityVerified -> "兼容验证"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PushConfigSection(
    targetPrefix: String,
    onTargetChange: (String) -> Unit,
    sourceBase: String,
    onSourceBaseChange: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    localLanIp: String,
    onFillDefaultTarget: () -> Unit,
    onCopyLocalIp: () -> Unit,
    danmuOffsetRaw: String,
    onDanmuOffsetChange: (String) -> Unit,
    danmuFontSizeRaw: String,
    onDanmuFontSizeChange: (String) -> Unit,
    envDanmuFontSize: Int?,
    onShowClientSupports: () -> Unit,
    onResetDanmuOptions: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.SettingsEthernet, null, tint = MaterialTheme.colorScheme.primary)
                Text("推送配置", style = MaterialTheme.typography.titleSmall)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onCopyLocalIp,
                    label = { Text(if (localLanIp.isBlank()) "本机局域网IP: 未识别" else "本机局域网IP: $localLanIp") },
                    leadingIcon = { Icon(Icons.Rounded.Wifi, null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onFillDefaultTarget,
                    label = { Text("填入默认目标模板") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onShowClientSupports,
                    label = { Text("支持客户端") },
                    leadingIcon = { Icon(Icons.Rounded.Verified, null, modifier = Modifier.size(16.dp)) }
                )
            }

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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = onUseLocal, label = { Text("源：本机") })
                AssistChip(onClick = onUseLan, label = { Text("源：局域网") })
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            OutlinedTextField(
                value = danmuOffsetRaw,
                onValueChange = onDanmuOffsetChange,
                label = { Text("时间偏移（秒，可负数）") },
                supportingText = { Text("正数=弹幕延后；负数=弹幕提前（例：-1.5）") },
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
                            "当前核心默认 DANMU_FONT_SIZE=$envDanmuFontSize；留空表示不覆盖"
                        } else {
                            "留空表示不覆盖核心默认值"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedButton(onClick = onResetDanmuOptions) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("重置弹幕参数")
            }
        }
    }
}

@Composable
private fun LanScanSection(
    isScanning: Boolean,
    status: String,
    devices: List<PushLanDeviceCandidate>,
    onScan: () -> Unit,
    onClear: () -> Unit,
    onUseDevice: (PushLanDeviceCandidate) -> Unit,
    onCopyDevice: (PushLanDeviceCandidate) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.primary)
                    Text("局域网设备扫描", style = MaterialTheme.typography.titleSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClear, enabled = !isScanning) { Text("清空") }
                    FilledTonalButton(onClick = onScan, enabled = !isScanning) {
                        if (isScanning) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("扫描中")
                        } else {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("开始扫描")
                        }
                    }
                }
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (devices.isNotEmpty()) {
                devices.forEach { device ->
                    LanDeviceCard(
                        device = device,
                        onUseDevice = { onUseDevice(device) },
                        onCopyDevice = { onCopyDevice(device) }
                    )
                }
            } else if (!isScanning) {
                Text(
                    "暂无可用设备。点击“开始扫描”后会检测 9978 推送接口，并标记 OK/FongMi 兼容性。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanDeviceCard(
    device: PushLanDeviceCandidate,
    onUseDevice: () -> Unit,
    onCopyDevice: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.ip,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
                )
                if (device.latencyMs != null) {
                    Text(
                        "${device.latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (device.isSelf) {
                    AssistChip(onClick = {}, label = { Text("本机") })
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
                device.ifName?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUseDevice,
                    enabled = device.port9978Open,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("用作推送目标")
                }
                OutlinedButton(
                    onClick = onCopyDevice,
                    enabled = device.port9978Open,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制目标")
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    isSearching: Boolean,
    hasSearched: Boolean,
    hasResult: Boolean,
    onSearch: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "搜索动漫",
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeywordChange,
                label = { Text("关键词") },
                placeholder = { Text("示例：凡人修仙传") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
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
                    Text("搜索")
                }
            }
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (hasSearched && !hasResult) {
                Text(
                    "未找到匹配动漫",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            .clickable(enabled = !loading, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
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
                Text(
                    "ID:${anime.animeId} · ${anime.episodeCount} 集",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(anime.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "AnimeID: ${anime.animeId} · 官方标注 ${anime.episodeCount} 集",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(onClick = {}, label = { Text("第${episode.episodeNumber}集") })
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(episode.title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "EID:${episode.episodeId}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = onPush,
                enabled = !pushing,
                modifier = Modifier.size(36.dp)
            ) {
                if (pushing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

private fun buildDefaultTargetTemplate(localLanIp: String): String {
    val host = localLanIp.trim().takeIf { it.isNotBlank() && !it.startsWith("127.") } ?: "127.0.0.1"
    return "http://$host:9978/action?do=refresh&type=danmaku&path="
}
