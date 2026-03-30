package com.example.danmuapiapp.ui.screen.apitest

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
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
            color = apiTestPanelColor(),
            border = BorderStroke(1.dp, apiTestOutlineColor())
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
                        loadingEpisodeId = viewModel.loadingEpisodeId,
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
    loadingEpisodeId: Long?,
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
                    loadingEpisodeId = loadingEpisodeId,
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
        shape = RoundedCornerShape(16.dp),
        color = apiTestPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.height(36.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1
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
        WorkbenchCard(title = "自动匹配", subtitle = "match → comment/json") {
            TextField(
                value = fileName,
                onValueChange = onFileNameChange,
                placeholder = { Text("输入视频文件名进行匹配") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search, null,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = apiTestFieldColor(),
                    focusedContainerColor = apiTestFieldColor(),
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "例如：凡人修仙传 S01E01 1080P",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onAutoMatch,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
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
    loadingEpisodeId: Long?,
    result: DanmuInsight?,
    onOpenAnime: (DownloadAnimeCandidate) -> Unit,
    onPickEpisode: (DownloadAnimeCandidate, DownloadEpisodeCandidate) -> Unit,
    onBack: () -> Unit
) {
    when {
        result != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DanmuInsightPanel(insight = result, backLabel = "返回剧集", onBack = onBack)
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
                when {
                    isLoadingEpisodes -> LoadingHintCard(title = "正在加载剧集", subtitle = anime.title)
                    episodeCandidates.isEmpty() -> InfoHintCard(title = "暂无剧集", subtitle = "可以返回搜索结果重新选择")
                    else -> WorkbenchCard(
                        title = anime.title,
                        subtitle = buildString {
                            if (anime.episodeCount > 0) append("共 ${anime.episodeCount} 集")
                            if (anime.animeId > 0L) {
                                if (isNotBlank()) append(" · ")
                                append("ID ${anime.animeId}")
                            }
                        },
                        action = {
                            TextButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("返回搜索")
                            }
                        }
                    ) {
                        TextField(
                            value = episodeQuery,
                            onValueChange = { episodeQuery = it },
                            placeholder = { Text("搜索标题或输入集数跳转") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Search, null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            trailingIcon = {
                                val canJump = episodeQuery.trim().toIntOrNull() != null
                                if (canJump) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            val targetEpisode = episodeQuery.trim().toIntOrNull() ?: return@FilledTonalIconButton
                                            val targetIndex = episodeCandidates.indexOfFirst { it.episodeNumber == targetEpisode }
                                            if (targetIndex >= 0) {
                                                scope.launch { listState.animateScrollToItem(targetIndex) }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, "跳转", Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = apiTestFieldColor(),
                                focusedContainerColor = apiTestFieldColor(),
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = apiTestSubPanelColor()
                        ) {
                            Text(
                                text = if (episodeQuery.trim().all { it.isDigit() } || episodeQuery.isBlank()) {
                                    "共 ${episodeCandidates.size} 集"
                                } else {
                                    "筛选结果 ${filteredEpisodes.size} / ${episodeCandidates.size}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                                    loading = isLoadingManualDanmu && loadingEpisodeId == episode.episodeId,
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
                WorkbenchCard(title = "手动匹配", subtitle = "search/anime → bangumi → comment/json") {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("输入动漫关键词搜索") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search, null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = apiTestFieldColor(),
                            focusedContainerColor = apiTestFieldColor(),
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "例如：凡人修仙传",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onSearch,
                        enabled = !isSearchingAnime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
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
        shape = RoundedCornerShape(18.dp),
        color = apiTestSubPanelColor(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 2.dp
            ) {
                AnimePosterThumbnail(
                    imageUrl = anime.imageUrl,
                    title = anime.title,
                    modifier = Modifier.size(width = 54.dp, height = 72.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = if (anime.episodeCount > 0) "共 ${anime.episodeCount} 集" else "集数未知",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow, "进入",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
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
        color = apiTestSubPanelColor(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${episode.episodeNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.source.isNotBlank()) {
                    Text(
                        text = episode.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Rounded.PlayArrow, "查看",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun DanmuInsightPanel(
    insight: DanmuInsight,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null
) {
    val peakMoment = remember(insight.highMoments) {
        insight.highMoments.maxByOrNull { it.count }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DanmuMetricsGrid(insight = insight, backLabel = backLabel, onBack = onBack)
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
private fun DanmuMetaBar(
    insight: DanmuInsight,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = apiTestPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Movie, null,
                            Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = insight.episodeTitle.ifBlank {
                            insight.animeTitle.ifBlank {
                                insight.commentId?.let { "commentId $it" } ?: "弹幕结果"
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (backLabel != null && onBack != null) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(backLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (insight.animeTitle.isNotBlank() && insight.episodeTitle.isNotBlank()) {
                Text(
                    text = buildString {
                        append(insight.animeTitle)
                        if (insight.source.isNotBlank()) append(" · ${insight.source}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (insight.pathLabel.isNotBlank()) {
                Text(
                    text = insight.pathLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (insight.commentId != null) {
                    StatusBadge(text = "ID ${insight.commentId}", color = MaterialTheme.colorScheme.primary)
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
}

@Composable
private fun DanmuMetricsGrid(
    insight: DanmuInsight,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null
) {
    val matchLine = remember(insight.animeTitle, insight.episodeTitle) {
        buildPlayerMatchLine(insight)
    }
    val averageDensity = remember(insight.totalCount, insight.durationSeconds) {
        formatAverageDensity(insight.totalCount, insight.durationSeconds)
    }

    WorkbenchCard(title = "弹幕概览") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = matchLine,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (insight.commentId != null) {
                            StatusBadge(
                                text = "ID ${insight.commentId}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (insight.pathLabel.isNotBlank()) {
                            StatusBadge(
                                text = insight.pathLabel,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        insight.requestDurationMs?.let { duration ->
                            StatusBadge(
                                text = "${duration}ms",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (insight.totalCount == 0) {
                            StatusBadge(
                                text = "未解析到弹幕",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (backLabel != null && onBack != null) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(backLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "匹配时间",
                        value = formatMatchedAt(insight.matchedAtMillis),
                        icon = Icons.Rounded.Schedule,
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                    CompactMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "弹幕总数",
                        value = insight.totalCount.toString(),
                        icon = Icons.Rounded.GraphicEq,
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompactMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "视频时长",
                        value = formatVideoTime(insight.durationSeconds),
                        icon = Icons.Rounded.Movie,
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                    CompactMetricItem(
                        modifier = Modifier.weight(1f),
                        label = "平均密度",
                        value = averageDensity,
                        icon = Icons.Rounded.AutoAwesome,
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactMetricItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = apiTestSubPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = iconTint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                    maxLines = 1
                )
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
}

@Composable
private fun DanmuPeakMomentCard(moment: DanmuHighMoment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                            apiTestPanelColor()
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome, null,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "高能时刻",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatVideoTime(moment.startSeconds)} - ${formatVideoTime(moment.endSeconds)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "该时间段弹幕最密集",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${moment.count}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "条弹幕",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun DanmuHeatmapCard(insight: DanmuInsight) {
    var selectedBucketIndex by remember(insight.commentId, insight.matchedAtMillis) {
        mutableIntStateOf(-1)
    }
    var chartWidthPx by remember(insight.commentId, insight.matchedAtMillis) {
        mutableIntStateOf(0)
    }
    val selectedBucket = insight.heatBuckets.getOrNull(selectedBucketIndex)

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
        val activeGlowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        val activeStrokeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(chartBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onSizeChanged { chartWidthPx = it.width }
                    .pointerInput(insight.commentId, insight.matchedAtMillis, insight.heatBuckets.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            selectedBucketIndex = resolveHeatBucketIndex(
                                positionX = down.position.x,
                                widthPx = chartWidthPx,
                                bucketCount = insight.heatBuckets.size
                            )
                            drag(down.id) { change ->
                                selectedBucketIndex = resolveHeatBucketIndex(
                                    positionX = change.position.x,
                                    widthPx = chartWidthPx,
                                    bucketCount = insight.heatBuckets.size
                                )
                                change.consume()
                            }
                            selectedBucketIndex = -1
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
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
                        val isSelected = index == selectedBucketIndex
                        val drawHeight = size.height * (0.14f + intensity * 0.86f)
                        val slotLeft = index * barWidth
                        val left = slotLeft + barWidth * if (isSelected) 0.09f else 0.12f
                        val top = size.height - drawHeight
                        val barColor = lerp(startColor, endColor, intensity.coerceIn(0f, 1f))

                        if (isSelected) {
                            drawRoundRect(
                                color = activeGlowColor,
                                topLeft = androidx.compose.ui.geometry.Offset(slotLeft + barWidth * 0.02f, 0f),
                                size = androidx.compose.ui.geometry.Size(barWidth * 0.96f, size.height),
                                cornerRadius = CornerRadius(barWidth * 0.28f, barWidth * 0.28f)
                            )
                        }

                        drawRoundRect(
                            color = if (isSelected) barColor.copy(alpha = 1f) else barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(
                                width = barWidth * if (isSelected) 0.82f else 0.76f,
                                height = drawHeight
                            ),
                            cornerRadius = CornerRadius(barWidth * 0.3f, barWidth * 0.3f)
                        )

                        if (isSelected) {
                            drawRoundRect(
                                color = activeStrokeColor,
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(barWidth * 0.82f, drawHeight),
                                cornerRadius = CornerRadius(barWidth * 0.3f, barWidth * 0.3f),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = apiTestSubPanelColor(),
            border = BorderStroke(1.dp, apiTestOutlineColor(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = if (selectedBucket == null) "交互提示" else "当前区间",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = selectedBucket?.let(::formatHeatBucketRange)
                            ?: "按住热力图滑动，查看区间弹幕数",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = if (selectedBucket == null) FontFamily.Default else FontFamily.Monospace
                        ),
                        fontWeight = if (selectedBucket == null) FontWeight.Normal else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selectedBucket == null) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = selectedBucket?.let { "${it.count} 条弹幕" } ?: "拖动预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedBucket == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
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
    val pageSize = 200
    val sourceSummary = remember(insight.comments, insight.source) {
        buildDanmuSourceSummary(insight)
    }
    var selectedFilter by remember(insight.commentId, insight.matchedAtMillis) {
        mutableStateOf(DanmuCommentFilter.All)
    }
    var visibleCount by remember(insight.commentId, insight.matchedAtMillis, selectedFilter) {
        mutableIntStateOf(pageSize)
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
        subtitle = sourceSummary
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
                    onClick = { visibleCount += pageSize },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("加载更多 200 条")
                }
            }
        }
    }
}

private fun buildDanmuSourceSummary(insight: DanmuInsight): String {
    if (insight.comments.isEmpty()) {
        return if (insight.source.isNotBlank()) "来源分布：${insight.source}" else "来源分布：暂无"
    }

    val fallbackSource = insight.source.trim()
    val counts = linkedMapOf<String, Int>()
    insight.comments.forEach { item ->
        val rawLabel = item.sourceLabel.ifBlank { fallbackSource }.trim()
        if (rawLabel.isBlank()) return@forEach
        splitDanmuSources(rawLabel).forEach { label ->
            counts[label] = (counts[label] ?: 0) + 1
        }
    }

    if (counts.isEmpty()) return "来源分布：暂无"

    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .joinToString(
            prefix = "来源分布：",
            separator = " · "
        ) { (label, count) ->
            "$label ${formatCompactCount(count)}"
        }
}

private fun splitDanmuSources(raw: String): List<String> {
    return raw.split('&', '＆')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
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
        color = apiTestCommentRowColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor(alpha = 0.72f))
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
                    if (item.sourceLabel.isNotBlank()) {
                        StatusBadge(
                            text = item.sourceLabel,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
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

private fun buildPlayerMatchLine(insight: DanmuInsight): String {
    val animeTitle = insight.animeTitle.trim()
    val episodeTitle = insight.episodeTitle.trim()
    return when {
        animeTitle.isNotBlank() && episodeTitle.isNotBlank() -> "$animeTitle - $episodeTitle"
        episodeTitle.isNotBlank() -> episodeTitle
        animeTitle.isNotBlank() -> animeTitle
        insight.commentId != null -> "commentId ${insight.commentId}"
        else -> "弹幕结果"
    }
}

private fun formatAverageDensity(totalCount: Int, durationSeconds: Double): String {
    if (totalCount <= 0 || durationSeconds <= 0.0) return "--"
    val perMinute = totalCount / (durationSeconds / 60.0)
    return if (perMinute >= 100) {
        "${perMinute.toInt()}/分"
    } else {
        "${String.format(Locale.US, "%.1f", perMinute)}/分"
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
        color = apiTestPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
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
        color = apiTestPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
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
        color = apiTestSubPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
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
        color = apiTestSubPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
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

@Composable
private fun apiTestPanelColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.luminance() < 0.5f) {
        lerp(c.surfaceContainerHigh, c.surfaceContainerHighest, 0.32f)
    } else {
        c.surfaceContainerHigh
    }
}

@Composable
private fun apiTestSubPanelColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.luminance() < 0.5f) {
        lerp(c.surfaceContainer, c.surfaceContainerHigh, 0.42f)
    } else {
        c.surfaceContainerLow
    }
}

@Composable
private fun apiTestFieldColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.luminance() < 0.5f) {
        lerp(c.surfaceContainerLow, c.surfaceContainer, 0.68f)
    } else {
        c.surfaceContainerLowest
    }
}

@Composable
private fun apiTestCommentRowColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.luminance() < 0.5f) {
        lerp(c.surface, c.surfaceContainerLow, 0.58f)
    } else {
        c.surface.copy(alpha = 0.42f)
    }
}

@Composable
private fun apiTestOutlineColor(alpha: Float = 1f): Color {
    val c = MaterialTheme.colorScheme
    return if (c.background.luminance() < 0.5f) {
        c.outlineVariant.copy(alpha = 0.42f * alpha)
    } else {
        c.outlineVariant.copy(alpha = 0.24f * alpha)
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

private fun resolveHeatBucketIndex(
    positionX: Float,
    widthPx: Int,
    bucketCount: Int
): Int {
    if (widthPx <= 0 || bucketCount <= 0) return -1
    val clampedX = positionX.coerceIn(0f, widthPx.toFloat() - 1f)
    return ((clampedX / widthPx.toFloat()) * bucketCount)
        .toInt()
        .coerceIn(0, bucketCount - 1)
}

private fun formatHeatBucketRange(bucket: DanmuHeatBucket): String {
    return "${formatVideoTime(bucket.startSeconds)} - ${formatVideoTime(bucket.endSeconds)}"
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
