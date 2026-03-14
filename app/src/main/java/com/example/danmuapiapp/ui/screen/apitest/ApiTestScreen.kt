package com.example.danmuapiapp.ui.screen.apitest

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApiTestScreen(
    onBack: () -> Unit,
    viewModel: ApiTestViewModel = hiltViewModel()
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current

    var primaryTab by rememberSaveable { mutableIntStateOf(0) }
    var danmuTab by rememberSaveable { mutableIntStateOf(0) }
    var endpointExpanded by remember { mutableStateOf(false) }
    var endpointIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var baseUrl by rememberSaveable(runtimeState.localUrl, runtimeState.lanUrl) {
        mutableStateOf(
            RuntimeUrlParser.extractBase(runtimeState.localUrl).ifBlank {
                RuntimeUrlParser.extractBase(runtimeState.lanUrl)
            }
        )
    }
    var rawBody by rememberSaveable {
        mutableStateOf("{\n  \"type\": \"qq\",\n  \"segment_start\": 0,\n  \"segment_end\": 30000,\n  \"url\": \"https://...\"\n}")
    }
    var autoFileName by rememberSaveable { mutableStateOf("") }
    var manualQuery by rememberSaveable { mutableStateOf("") }
    val paramValues = remember { mutableStateMapOf<String, String>() }
    val endpoint = viewModel.endpoints.getOrNull(endpointIndex) ?: viewModel.endpoints.first()

    LaunchedEffect(endpoint.key) {
        paramValues.clear()
        if (endpoint.key == "getComment" || endpoint.key == "getSegmentComment") {
            paramValues["format"] = "json"
        }
    }

    val canStepBack = when {
        primaryTab == 1 && danmuTab == 0 && viewModel.autoMatchResult != null -> true
        primaryTab == 1 && danmuTab == 1 && (viewModel.manualResult != null || viewModel.manualCurrentAnime != null) -> true
        else -> false
    }
    val backAction = {
        when {
            primaryTab == 1 && danmuTab == 0 && viewModel.autoMatchResult != null -> viewModel.clearAutoResult()
            primaryTab == 1 && danmuTab == 1 && (viewModel.manualResult != null || viewModel.manualCurrentAnime != null) -> viewModel.backManualStep()
            else -> onBack()
        }
    }

    BackHandler(enabled = canStepBack, onBack = backAction)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = backAction, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "接口调试",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = baseUrl.ifBlank { "未配置 Base URL" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalIconButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Rounded.Settings, "连接设置", Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
        ) {
            SecondaryTabRow(
                selectedTabIndex = primaryTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = primaryTab == 0,
                    onClick = { primaryTab = 0 },
                    text = { Text("接口调试") },
                    icon = { Icon(Icons.Rounded.Tune, null) }
                )
                Tab(
                    selected = primaryTab == 1,
                    onClick = { primaryTab = 1 },
                    text = { Text("弹幕测试") },
                    icon = { Icon(Icons.Rounded.Movie, null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(targetState = primaryTab, label = "primaryTab") { tab ->
                if (tab == 0) {
                    ApiDebugPane(
                        endpoint = endpoint,
                        endpoints = viewModel.endpoints,
                        endpointExpanded = endpointExpanded,
                        onExpandedChange = { endpointExpanded = it },
                        endpointIndex = endpointIndex,
                        onEndpointSelect = {
                            endpointIndex = it
                            endpointExpanded = false
                        },
                        paramValues = paramValues,
                        rawBody = rawBody,
                        onRawBodyChange = { rawBody = it },
                        onSend = {
                            viewModel.sendRequest(
                                endpoint = endpoint,
                                baseUrl = baseUrl,
                                paramValues = paramValues,
                                rawBody = rawBody
                            )
                        },
                        isLoading = viewModel.isLoading,
                        requestUrl = viewModel.requestUrl,
                        curlCommand = viewModel.curlCommand,
                        response = viewModel.debugResponse,
                        onCopyCurl = {
                            clipboard.nativeClipboard.setPrimaryClip(
                                ClipData.newPlainText("curl", viewModel.curlCommand)
                            )
                        },
                        onCopyResponse = {
                            viewModel.debugResponse?.let { response ->
                                clipboard.nativeClipboard.setPrimaryClip(
                                    ClipData.newPlainText("接口响应", response.fullText)
                                )
                            }
                        },
                        onClearResponse = viewModel::clearDebugResponse
                    )
                } else {
                    DanmuTestPane(
                        danmuTab = danmuTab,
                        onDanmuTabChange = { danmuTab = it },
                        autoFileName = autoFileName,
                        onAutoFileNameChange = { autoFileName = it },
                        isAutoMatching = viewModel.isAutoMatching,
                        autoMatchResult = viewModel.autoMatchResult,
                        onAutoMatch = { viewModel.runAutoMatch(baseUrl, autoFileName) },
                        onClearAuto = viewModel::clearAutoResult,
                        manualQuery = manualQuery,
                        onManualQueryChange = { manualQuery = it },
                        onManualSearch = { viewModel.searchAnime(baseUrl, manualQuery) },
                        isSearchingAnime = viewModel.isSearchingAnime,
                        manualHasSearched = viewModel.manualHasSearched,
                        animeCandidates = viewModel.manualAnimeCandidates,
                        currentAnime = viewModel.manualCurrentAnime,
                        episodeCandidates = viewModel.manualEpisodeCandidates,
                        loadingAnimeId = viewModel.loadingAnimeId,
                        isLoadingEpisodes = viewModel.isLoadingEpisodes,
                        isLoadingManualDanmu = viewModel.isLoadingManualDanmu,
                        manualResult = viewModel.manualResult,
                        onOpenAnime = { anime -> viewModel.openManualAnimeDetail(baseUrl, anime) },
                        onPickEpisode = { anime, episode -> viewModel.loadManualDanmu(baseUrl, anime, episode) },
                        onBackManual = viewModel::backManualStep
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSettingsSheet) {
        ConnectionSettingsSheet(
            baseUrl = baseUrl,
            onBaseUrlChange = { baseUrl = it },
            localBaseUrl = RuntimeUrlParser.extractBase(runtimeState.localUrl),
            lanBaseUrl = RuntimeUrlParser.extractBase(runtimeState.lanUrl),
            onUseLocal = {
                baseUrl = RuntimeUrlParser.extractBase(runtimeState.localUrl)
            },
            onUseLan = {
                baseUrl = RuntimeUrlParser.extractBase(runtimeState.lanUrl)
            },
            onDismissRequest = { showSettingsSheet = false }
        )
    }

    if (viewModel.errorMessage != null) {
        AppBottomSheetDialog(
            onDismissRequest = viewModel::dismissError,
            style = AppBottomSheetStyle.Confirm,
            tone = AppBottomSheetTone.Danger,
            title = { Text("请求失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun ConnectionSettingsSheet(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    localBaseUrl: String,
    lanBaseUrl: String,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismissRequest,
        style = AppBottomSheetStyle.Form,
        tone = AppBottomSheetTone.Brand,
        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
        title = {
            Text(
                text = "连接设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Text(
                text = "这里修改后，接口调试和弹幕测试会立即同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL") },
                placeholder = { Text("例如：http://127.0.0.1:9321") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            if (localBaseUrl.isNotBlank()) {
                SettingsPresetButton(
                    label = "本机地址",
                    value = localBaseUrl,
                    onClick = onUseLocal
                )
            }
            if (lanBaseUrl.isNotBlank()) {
                SettingsPresetButton(
                    label = "局域网地址",
                    value = lanBaseUrl,
                    onClick = onUseLan
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun SettingsPresetButton(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "使用",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiDebugPane(
    endpoint: ApiEndpointConfig,
    endpoints: List<ApiEndpointConfig>,
    endpointExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    endpointIndex: Int,
    onEndpointSelect: (Int) -> Unit,
    paramValues: MutableMap<String, String>,
    rawBody: String,
    onRawBodyChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    requestUrl: String,
    curlCommand: String,
    response: ApiDebugResponse?,
    onCopyCurl: () -> Unit,
    onCopyResponse: () -> Unit,
    onClearResponse: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkbenchCard(title = "请求") {
            ExposedDropdownMenuBox(
                expanded = endpointExpanded,
                onExpandedChange = { onExpandedChange(!endpointExpanded) }
            ) {
                OutlinedTextField(
                    value = "${endpoint.title} · ${endpoint.method}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("接口") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endpointExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(
                    expanded = endpointExpanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    endpoints.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${item.method} ${item.pathTemplate}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = { onEndpointSelect(index) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            endpoint.params.forEachIndexed { index, param ->
                if (param.inputType == ApiParamInputType.Select && param.options.isNotEmpty()) {
                    Text(
                        text = buildString {
                            append(param.label)
                            if (param.required) append(" *")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        param.options.forEach { option ->
                            FilterChip(
                                selected = paramValues[param.name] == option,
                                onClick = { paramValues[param.name] = option },
                                label = { Text(option) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = paramValues[param.name].orEmpty(),
                        onValueChange = { paramValues[param.name] = it },
                        label = {
                            Text(buildString {
                                append(param.label)
                                if (param.required) append(" *")
                            })
                        },
                        placeholder = {
                            if (param.placeholder.isNotBlank()) Text(param.placeholder)
                        },
                        supportingText = {
                            if (param.helper.isNotBlank()) Text(param.helper)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (index != endpoint.params.lastIndex || endpoint.hasRawBody) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            if (endpoint.hasRawBody) {
                OutlinedTextField(
                    value = rawBody,
                    onValueChange = onRawBodyChange,
                    label = { Text("JSON 请求体") },
                    supportingText = {
                        if (endpoint.bodyHint.isNotBlank()) Text(endpoint.bodyHint)
                    },
                    minLines = 4,
                    maxLines = 9,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = onSend,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("请求中")
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("发送请求")
                }
            }
        }

        AnimatedVisibility(visible = requestUrl.isNotBlank()) {
            RequestPreviewCard(
                requestUrl = requestUrl,
                curlCommand = curlCommand,
                onCopyCurl = onCopyCurl
            )
        }

        if (response != null) {
            ResponseMetaCard(
                responseCode = response.responseCode,
                durationMs = response.responseDurationMs,
                bodySizeBytes = response.bodySizeBytes,
                onCopyResponse = onCopyResponse,
                onClearResponse = onClearResponse
            )
            ResponsePreviewCard(
                previewText = response.previewText,
                fullText = response.fullText,
                previewTruncated = response.previewTruncated
            )
        }
    }
}

@Composable
private fun RequestPreviewCard(
    requestUrl: String,
    curlCommand: String,
    onCopyCurl: () -> Unit
) {
    WorkbenchCard(
        title = "请求预览",
        action = {
            if (curlCommand.isNotBlank()) {
                IconButton(onClick = onCopyCurl, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ContentCopy, "复制 CURL", Modifier.size(16.dp))
                }
            }
        }
    ) {
        MonoPreview(text = requestUrl)
        if (curlCommand.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MonoPreview(text = curlCommand)
        }
    }
}

@Composable
private fun ResponseMetaCard(
    responseCode: Int,
    durationMs: Long,
    bodySizeBytes: Int,
    onCopyResponse: () -> Unit,
    onClearResponse: () -> Unit
) {
    WorkbenchCard(
        title = "响应",
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onCopyResponse, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ContentCopy, "复制响应", Modifier.size(16.dp))
                }
                IconButton(onClick = onClearResponse, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ClearAll, "清空响应", Modifier.size(16.dp))
                }
            }
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusBadge(text = "HTTP $responseCode", color = statusColor(responseCode))
            StatusBadge(text = "${durationMs}ms", color = MaterialTheme.colorScheme.primary)
            StatusBadge(text = formatBytes(bodySizeBytes), color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun ResponsePreviewCard(
    previewText: String,
    fullText: String = previewText,
    previewTruncated: Boolean
) {
    val canExpand = previewTruncated && fullText != previewText
    var expanded by rememberSaveable(fullText, previewText, previewTruncated) {
        mutableStateOf(false)
    }
    val displayText = if (canExpand && expanded) fullText else previewText

    WorkbenchCard(
        title = "响应预览",
        subtitle = when {
            canExpand && expanded -> "已展开完整内容"
            canExpand -> "默认折叠超长内容，可继续展开"
            else -> null
        },
        action = if (canExpand) {
            {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(if (expanded) "收起" else "显示更多")
                }
            }
        } else {
            null
        }
    ) {
        MonoPreview(text = displayText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DanmuTestPane(
    danmuTab: Int,
    onDanmuTabChange: (Int) -> Unit,
    autoFileName: String,
    onAutoFileNameChange: (String) -> Unit,
    isAutoMatching: Boolean,
    autoMatchResult: DanmuInsight?,
    onAutoMatch: () -> Unit,
    onClearAuto: () -> Unit,
    manualQuery: String,
    onManualQueryChange: (String) -> Unit,
    onManualSearch: () -> Unit,
    isSearchingAnime: Boolean,
    manualHasSearched: Boolean,
    animeCandidates: List<DownloadAnimeCandidate>,
    currentAnime: DownloadAnimeCandidate?,
    episodeCandidates: List<DownloadEpisodeCandidate>,
    loadingAnimeId: Long?,
    isLoadingEpisodes: Boolean,
    isLoadingManualDanmu: Boolean,
    manualResult: DanmuInsight?,
    onOpenAnime: (DownloadAnimeCandidate) -> Unit,
    onPickEpisode: (DownloadAnimeCandidate, DownloadEpisodeCandidate) -> Unit,
    onBackManual: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DanmuModeSwitcher(
            selectedTab = danmuTab,
            onTabChange = onDanmuTabChange
        )

        AnimatedContent(targetState = danmuTab, label = "danmuTab") { tab ->
            if (tab == 0) {
                DanmuAutoPane(
                    fileName = autoFileName,
                    onFileNameChange = onAutoFileNameChange,
                    isLoading = isAutoMatching,
                    result = autoMatchResult,
                    onAutoMatch = onAutoMatch,
                    onClearResult = onClearAuto
                )
            } else {
                DanmuManualPane(
                    query = manualQuery,
                    onQueryChange = onManualQueryChange,
                    onSearch = onManualSearch,
                    isSearchingAnime = isSearchingAnime,
                    manualHasSearched = manualHasSearched,
                    animeCandidates = animeCandidates,
                    currentAnime = currentAnime,
                    episodeCandidates = episodeCandidates,
                    loadingAnimeId = loadingAnimeId,
                    isLoadingEpisodes = isLoadingEpisodes,
                    isLoadingManualDanmu = isLoadingManualDanmu,
                    result = manualResult,
                    onOpenAnime = onOpenAnime,
                    onPickEpisode = onPickEpisode,
                    onBack = onBackManual
                )
            }
        }
    }
}

@Composable
private fun DanmuModeSwitcher(
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DanmuModeTab(
                modifier = Modifier.weight(1f),
                selected = selectedTab == 0,
                label = "自动匹配",
                icon = Icons.Rounded.AutoAwesome,
                onClick = { onTabChange(0) }
            )
            DanmuModeTab(
                modifier = Modifier.weight(1f),
                selected = selectedTab == 1,
                label = "手动匹配",
                icon = Icons.Rounded.Search,
                onClick = { onTabChange(1) }
            )
        }
    }
}

@Composable
private fun DanmuModeTab(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        lerp(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            0.26f
        )
    } else {
        Color.Transparent
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f)
    }
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    }
    val iconContainerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.76f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accentColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        Color.Transparent
    }

    Surface(
        modifier = modifier.height(38.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = iconTint
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun DanmuAutoPane(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    isLoading: Boolean,
    result: DanmuInsight?,
    onAutoMatch: () -> Unit,
    onClearResult: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkbenchCard(
            title = "自动匹配",
            subtitle = "`match -> comment/json`",
            action = {
                if (result != null) {
                    TextButton(onClick = onClearResult) { Text("清空") }
                }
            }
        ) {
            OutlinedTextField(
                value = fileName,
                onValueChange = onFileNameChange,
                label = { Text("文件名") },
                placeholder = { Text("例如：凡人修仙传 S01E01 1080P") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAutoMatch,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("匹配中")
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("开始自动匹配")
                }
            }
        }

        if (isLoading) {
            LoadingHintCard(title = "正在匹配", subtitle = "先请求 match，再获取 comment/json")
        }
        if (result != null) {
            DanmuInsightPanel(insight = result)
        }
    }
}

@Composable
private fun DanmuManualPane(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearchingAnime: Boolean,
    manualHasSearched: Boolean,
    animeCandidates: List<DownloadAnimeCandidate>,
    currentAnime: DownloadAnimeCandidate?,
    episodeCandidates: List<DownloadEpisodeCandidate>,
    loadingAnimeId: Long?,
    isLoadingEpisodes: Boolean,
    isLoadingManualDanmu: Boolean,
    result: DanmuInsight?,
    onOpenAnime: (DownloadAnimeCandidate) -> Unit,
    onPickEpisode: (DownloadAnimeCandidate, DownloadEpisodeCandidate) -> Unit,
    onBack: () -> Unit
) {
    when {
        result != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactContextBar(
                    title = result.episodeTitle.ifBlank { result.animeTitle.ifBlank { "弹幕结果" } },
                    meta = buildString {
                        if (result.animeTitle.isNotBlank()) append(result.animeTitle)
                        if (result.commentId != null) {
                            if (isNotBlank()) append(" · ")
                            append("commentId ${result.commentId}")
                        }
                    },
                    actionLabel = "返回剧集",
                    onAction = onBack
                )
                DanmuInsightPanel(insight = result)
            }
        }

        currentAnime != null -> {
            val anime = currentAnime
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            var episodeQuery by rememberSaveable(anime.animeId) { mutableStateOf("") }
            val filteredEpisodes = remember(episodeCandidates, episodeQuery) {
                val trimmed = episodeQuery.trim()
                when {
                    trimmed.isBlank() -> episodeCandidates
                    trimmed.all { it.isDigit() } -> episodeCandidates
                    else -> {
                        val normalized = trimmed.lowercase(Locale.getDefault())
                        episodeCandidates.filter { episode ->
                            episode.title.lowercase(Locale.getDefault()).contains(normalized) ||
                                episode.source.lowercase(Locale.getDefault()).contains(normalized) ||
                                episode.episodeNumber.toString().contains(trimmed)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactContextBar(
                    title = anime.title,
                    meta = buildString {
                        if (anime.episodeCount > 0) append("共 ${anime.episodeCount} 集")
                        if (anime.animeId > 0L) {
                            if (isNotBlank()) append(" · ")
                            append("ID ${anime.animeId}")
                        }
                    },
                    actionLabel = "返回搜索",
                    onAction = onBack
                )

                when {
                    isLoadingEpisodes -> LoadingHintCard(title = "正在加载剧集", subtitle = "加载完成后可直接搜索或跳转集数")
                    episodeCandidates.isEmpty() -> InfoHintCard(title = "暂无剧集", subtitle = "可以返回搜索结果重新选择")
                    else -> WorkbenchCard(title = "剧集列表") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = episodeQuery,
                                onValueChange = { episodeQuery = it },
                                label = { Text("搜索 / 集数") },
                                placeholder = { Text("输入标题筛选，输入数字跳转") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        val targetEpisode = episodeQuery.trim().toIntOrNull() ?: return@KeyboardActions
                                        val targetIndex = episodeCandidates.indexOfFirst { it.episodeNumber == targetEpisode }
                                        if (targetIndex >= 0) {
                                            scope.launch { listState.animateScrollToItem(targetIndex) }
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    val targetEpisode = episodeQuery.trim().toIntOrNull() ?: return@FilledTonalButton
                                    val targetIndex = episodeCandidates.indexOfFirst { it.episodeNumber == targetEpisode }
                                    if (targetIndex >= 0) {
                                        scope.launch { listState.animateScrollToItem(targetIndex) }
                                    }
                                },
                                enabled = episodeQuery.trim().toIntOrNull() != null,
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("跳转")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (episodeQuery.trim().all { it.isDigit() } || episodeQuery.isBlank()) {
                                "共 ${episodeCandidates.size} 集"
                            } else {
                                "筛选结果 ${filteredEpisodes.size} / ${episodeCandidates.size}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp, max = 460.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(
                                items = filteredEpisodes,
                                key = { _, item -> item.episodeId }
                            ) { _, episode ->
                                EpisodeCandidateRow(
                                    episode = episode,
                                    loading = isLoadingManualDanmu,
                                    onClick = { onPickEpisode(anime, episode) }
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkbenchCard(title = "手动匹配", subtitle = "`search/anime -> bangumi -> comment/json`") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        label = { Text("动漫关键词") },
                        placeholder = { Text("例如：凡人修仙传") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSearch,
                        enabled = !isSearchingAnime,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isSearchingAnime) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("搜索中")
                        } else {
                            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("搜索动漫")
                        }
                    }
                }

                when {
                    isSearchingAnime -> LoadingHintCard(title = "正在搜索动漫", subtitle = "搜索结果会直接出现在下方")
                    animeCandidates.isNotEmpty() -> WorkbenchCard(title = "搜索结果") {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(animeCandidates, key = { _, item -> item.animeId }) { _, anime ->
                                AnimeCandidateRow(
                                    anime = anime,
                                    loading = isLoadingEpisodes && loadingAnimeId == anime.animeId,
                                    onClick = { onOpenAnime(anime) }
                                )
                            }
                        }
                    }
                    manualHasSearched -> InfoHintCard(title = "没有找到匹配动漫", subtitle = "换个关键词试试")
                }
            }
        }
    }
}

@Composable
private fun AnimeCandidateRow(
    anime: DownloadAnimeCandidate,
    loading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (anime.episodeCount > 0) "共 ${anime.episodeCount} 集" else "集数未知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                    Text("进入")
                }
            }
        }
    }
}

@Composable
private fun EpisodeCandidateRow(
    episode: DownloadEpisodeCandidate,
    loading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "第 ${episode.episodeNumber} 集 · ${episode.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.source.isNotBlank()) {
                    Text(
                        text = episode.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledTonalButton(
                onClick = onClick,
                enabled = !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("查看")
                }
            }
        }
    }
}

@Composable
private fun DanmuInsightPanel(insight: DanmuInsight) {
    val peakMoment = remember(insight.highMoments) {
        insight.highMoments.maxByOrNull { it.count }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DanmuMetaBar(insight = insight)
        DanmuMetricsGrid(insight = insight)
        if (peakMoment != null) {
            DanmuPeakMomentCard(moment = peakMoment)
        }
        DanmuHeatmapCard(insight = insight)
        DanmuCommentListCard(insight = insight)
        if (insight.comments.isEmpty()) {
            ResponsePreviewCard(
                previewText = insight.rawPreview,
                previewTruncated = insight.rawPreviewTruncated
            )
        }
    }
}

@Composable
private fun DanmuMetaBar(insight: DanmuInsight) {
    WorkbenchCard(
        title = insight.episodeTitle.ifBlank {
            insight.animeTitle.ifBlank {
                insight.commentId?.let { "commentId $it" } ?: "弹幕结果"
            }
        },
        subtitle = buildString {
            append(insight.pathLabel)
            if (insight.source.isNotBlank()) {
                append(" · ${insight.source}")
            }
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (insight.commentId != null) {
                StatusBadge(text = "commentId ${insight.commentId}", color = MaterialTheme.colorScheme.primary)
            }
            insight.requestDurationMs?.let { duration ->
                StatusBadge(text = "${duration}ms", color = MaterialTheme.colorScheme.tertiary)
            }
            if (insight.totalCount == 0) {
                StatusBadge(text = "未解析到弹幕", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DanmuMetricsGrid(insight: DanmuInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "匹配时间",
                value = formatMatchedAt(insight.matchedAtMillis),
                icon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp)) }
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "弹幕总数",
                value = insight.totalCount.toString(),
                icon = { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "视频时长",
                value = formatVideoTime(insight.durationSeconds),
                icon = { Icon(Icons.Rounded.Movie, null, Modifier.size(16.dp)) }
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                label = "高能峰值",
                value = if (insight.maxHeatCount > 0) "${insight.maxHeatCount} 条" else "暂无",
                icon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DanmuPeakMomentCard(moment: DanmuHighMoment) {
    WorkbenchCard(title = "高能时刻") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${formatVideoTime(moment.startSeconds)} - ${formatVideoTime(moment.endSeconds)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "该时间段弹幕最密集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusBadge(text = "${moment.count} 条", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DanmuHeatmapCard(insight: DanmuInsight) {
    WorkbenchCard(title = "弹幕热力图") {
        if (insight.heatBuckets.isEmpty()) {
            Text(
                text = "暂无热力数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@WorkbenchCard
        }

        val maxCount = insight.maxHeatCount.coerceAtLeast(1)
        val chartBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        val startColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        val endColor = MaterialTheme.colorScheme.tertiary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(chartBackground)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            repeat(3) { lineIndex ->
                val y = size.height * (lineIndex + 1) / 4f
                drawLine(
                    color = gridColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }
            val barWidth = size.width / insight.heatBuckets.size.toFloat()
            insight.heatBuckets.forEachIndexed { index, bucket ->
                val intensity = bucket.count / maxCount.toFloat()
                val drawHeight = size.height * (0.14f + intensity * 0.86f)
                val left = index * barWidth + barWidth * 0.12f
                val top = size.height - drawHeight
                val barColor = lerp(startColor, endColor, intensity.coerceIn(0f, 1f))
                drawRoundRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth * 0.76f, drawHeight),
                    cornerRadius = CornerRadius(barWidth * 0.3f, barWidth * 0.3f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatVideoTime(0.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatVideoTime(insight.durationSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DanmuCommentListCard(insight: DanmuInsight) {
    var selectedFilter by remember(insight.commentId, insight.matchedAtMillis) {
        mutableStateOf(DanmuCommentFilter.All)
    }
    var visibleCount by remember(insight.commentId, insight.matchedAtMillis, selectedFilter) {
        mutableIntStateOf(100)
    }
    val listState = rememberLazyListState()
    val filterCounts = remember(insight.comments) {
        DanmuCommentFilter.entries.associateWith { filter ->
            when (filter) {
                DanmuCommentFilter.All -> insight.comments.size
                else -> insight.comments.count { it.filter == filter }
            }
        }
    }
    val filteredComments = remember(insight.comments, selectedFilter) {
        when (selectedFilter) {
            DanmuCommentFilter.All -> insight.comments
            else -> insight.comments.filter { it.filter == selectedFilter }
        }
    }
    val displayedComments = remember(filteredComments, visibleCount) {
        filteredComments.take(visibleCount)
    }
    val canLoadMore = displayedComments.size < filteredComments.size
    val loadMoreVisible by remember(listState, displayedComments, filteredComments) {
        derivedStateOf {
            if (!canLoadMore || displayedComments.isEmpty()) {
                return@derivedStateOf false
            }
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= displayedComments.lastIndex
        }
    }

    LaunchedEffect(selectedFilter, insight.commentId, insight.matchedAtMillis) {
        listState.scrollToItem(0)
    }

    WorkbenchCard(
        title = "弹幕列表",
        subtitle = "已显示 ${displayedComments.size} / ${filteredComments.size}"
    ) {
        DanmuCommentFilterBar(
            selectedFilter = selectedFilter,
            counts = filterCounts,
            onSelect = { selectedFilter = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredComments.isEmpty()) {
            Text(
                text = "当前筛选下没有弹幕",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = displayedComments,
                    key = { index, item -> "${item.uniqueId}-${item.timeSeconds}-${index}" }
                ) { _, item ->
                    DanmuCommentRow(item = item)
                }
            }
            if (canLoadMore && loadMoreVisible) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { visibleCount += 100 },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("加载更多 100 条")
                }
            }
        }
    }
}

@Composable
private fun DanmuCommentFilterBar(
    selectedFilter: DanmuCommentFilter,
    counts: Map<DanmuCommentFilter, Int>,
    onSelect: (DanmuCommentFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DanmuCommentFilter.entries.forEach { filter ->
            CompactDanmuFilterButton(
                modifier = Modifier.weight(1f),
                selected = selectedFilter == filter,
                text = "${filter.label} ${formatCompactCount(counts[filter] ?: 0)}",
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun CompactDanmuFilterButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val fontSize = when {
                maxWidth < 72.dp || text.length >= 8 -> 9.sp
                maxWidth < 84.dp || text.length >= 7 -> 10.sp
                else -> 11.sp
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DanmuCommentRow(item: DanmuCommentItem) {
    val color = parseComposeColor(item.colorHex)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(text = formatVideoTime(item.timeSeconds), color = MaterialTheme.colorScheme.primary)
                    StatusBadge(text = item.filter.label, color = MaterialTheme.colorScheme.tertiary)
                    ColorSwatch(color = color)
                }
                if (item.fontSize != null) {
                    Text(
                        text = "字号 ${item.fontSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .padding(4.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun CompactContextBar(
    title: String,
    meta: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(actionLabel, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WorkbenchCard(
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (action != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    action()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MonoPreview(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun InfoHintCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LoadingHintCard(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun statusColor(code: Int): Color {
    return when {
        code in 200..299 -> Color(0xFF3C9F5B)
        code in 400..499 -> Color(0xFFD38C2F)
        code >= 500 -> Color(0xFFD1485F)
        else -> Color(0xFF64748B)
    }
}

private fun formatBytes(bytes: Int): String {
    val value = bytes.toLong().coerceAtLeast(0L)
    return when {
        value >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MB", value / 1024f / 1024f)
        value >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", value / 1024f)
        else -> "$value B"
    }
}

private fun formatMatchedAt(timestamp: Long): String {
    val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatVideoTime(rawSeconds: Double): String {
    val totalSeconds = rawSeconds.toInt().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun formatCompactCount(count: Int): String {
    val safeCount = count.coerceAtLeast(0)
    return when {
        safeCount >= 100_000 -> "${safeCount / 1000}k"
        safeCount >= 1000 -> String.format(Locale.US, "%.1fk", safeCount / 1000f).replace(".0k", "k")
        else -> safeCount.toString()
    }
}

private fun parseComposeColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrElse {
        Color(0xFFFFFFFF)
    }
}
