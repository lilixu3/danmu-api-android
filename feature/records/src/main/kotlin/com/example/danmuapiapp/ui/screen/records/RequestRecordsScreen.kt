package com.example.danmuapiapp.ui.screen.records

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.FileNameMatchSummary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RequestRecordsScreen(
    onBack: () -> Unit,
    viewModel: RequestRecordsViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val traceDetailState = viewModel.traceDetailState
    val clipboardManager = LocalClipboard.current
    var filterSuccess by remember { mutableStateOf<Boolean?>(null) }
    var query by remember { mutableStateOf("") }
    var methodFilter by remember { mutableStateOf<String?>(null) }
    var methodMenuExpanded by remember { mutableStateOf(false) }
    var timeRange by remember { mutableStateOf(RequestTimeRange.All) }
    var expandedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var knownRecordIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val todayCount by viewModel.todayRequestCount.collectAsStateWithLifecycle()
    val errorCount = records.count { !it.success }

    LaunchedEffect(adminState.isAdminMode) {
        viewModel.refresh()
    }

    LaunchedEffect(records) {
        val newRecords = records.filterNot { it.id in knownRecordIds }
        val fileNameMatchIds = newRecords.filter { it.fileNameMatch != null }.map { it.id }.toSet()
        if (fileNameMatchIds.isNotEmpty()) expandedIds = expandedIds + fileNameMatchIds
        knownRecordIds = records.mapTo(mutableSetOf()) { it.id }
    }

    traceDetailState.requestId?.let { requestId ->
        RequestTraceDetailDialog(
            state = traceDetailState,
            onDismiss = viewModel::closeTraceDetail,
            onRetry = { viewModel.openTraceDetail(requestId) }
        )
    }

    val availableMethods = remember(records) { records.map { it.method.uppercase() }.distinct().sorted() }
    val filteredRecords = remember(records, query, filterSuccess, methodFilter, timeRange) {
        RequestRecordInsights.filter(
            records,
            RequestRecordFilter(
                query = query,
                success = filterSuccess,
                method = methodFilter,
                timeRange = timeRange
            )
        )
    }
    val insights = remember(filteredRecords) { RequestRecordInsights.summarize(filteredRecords) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppGlassIconButton(
                    onClick = onBack,
                    size = 36.dp
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                }
                Column {
                    Text("请求记录", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "${records.size} 条记录" +
                            " · 今日 ${todayCount ?: "—"}" +
                            (if (errorCount > 0) " · $errorCount 失败" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppGlassIconButton(
                    onClick = viewModel::refresh,
                    enabled = !viewModel.isRefreshing,
                    size = 36.dp
                ) {
                    if (viewModel.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
                    }
                }
                AppGlassIconButton(
                    onClick = viewModel::clearAll,
                    enabled = records.isNotEmpty(),
                    size = 36.dp,
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(Icons.Rounded.ClearAll, "清空", Modifier.size(18.dp))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "filters") {
                if (!adminState.isAdminMode) {
                    AppGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "当前为普通模式，客户端真实 IP 可能被隐藏。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    singleLine = true,
                    label = { Text("搜索请求") },
                    placeholder = { Text("接口、IP、状态码或错误") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotBlank()) {
                        {
                            AppGlassIconButton(onClick = { query = "" }, size = 32.dp) {
                                Icon(Icons.Rounded.Close, "清空搜索")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppGlassFilterChip(
                        selected = filterSuccess == null,
                        onClick = { filterSuccess = null },
                        label = { Text("全部") },
                        leadingIcon = if (filterSuccess == null) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    AppGlassFilterChip(
                        selected = filterSuccess == true,
                        onClick = { filterSuccess = if (filterSuccess == true) null else true },
                        label = { Text("成功") },
                        accent = if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF4CAF50),
                        leadingIcon = if (filterSuccess == true) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    AppGlassFilterChip(
                        selected = filterSuccess == false,
                        onClick = { filterSuccess = if (filterSuccess == false) null else false },
                        label = { Text("失败") },
                        accent = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFE53935),
                        leadingIcon = if (filterSuccess == false) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    ExposedDropdownMenuBox(
                        expanded = methodMenuExpanded,
                        onExpandedChange = { methodMenuExpanded = it }
                    ) {
                        AppGlassFilterChip(
                            selected = methodFilter != null,
                            onClick = { methodMenuExpanded = true },
                            label = { Text(methodFilter ?: "方法") },
                            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(16.dp)) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = methodMenuExpanded,
                            onDismissRequest = { methodMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部方法") },
                                onClick = {
                                    methodFilter = null
                                    methodMenuExpanded = false
                                }
                            )
                            availableMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        methodFilter = method
                                        methodMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    AppGlassFilterChip(
                        selected = timeRange == RequestTimeRange.LastHour,
                        onClick = {
                            timeRange = if (timeRange == RequestTimeRange.LastHour) RequestTimeRange.All
                            else RequestTimeRange.LastHour
                        },
                        label = { Text("近 1 小时") }
                    )
                    AppGlassFilterChip(
                        selected = timeRange == RequestTimeRange.Today,
                        onClick = {
                            timeRange = if (timeRange == RequestTimeRange.Today) RequestTimeRange.All
                            else RequestTimeRange.Today
                        },
                        label = { Text("今天") }
                    )
                }

                // Stats card
                AppGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "匹配", value = insights.total.toString())
                        StatItem(
                            label = "失败率",
                            value = "${insights.failureRatePercent}%",
                            valueColor = if (insights.failureRatePercent > 0) {
                                if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFE53935)
                            } else MaterialTheme.colorScheme.onSurface
                        )
                        StatItem(label = "P95", value = insights.p95DurationMs?.let { "$it ms" } ?: "—")
                        StatItem(label = "客户端", value = insights.uniqueClients.toString())
                    }
                }

                if (insights.topFailureEndpoint != null || insights.slowCount > 0) {
                    AppGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Troubleshoot, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                buildString {
                                    insights.topFailureEndpoint?.let {
                                        append("失败最多：$it (${insights.topFailureCount})")
                                    }
                                    if (insights.slowCount > 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append("慢请求 ${insights.slowCount} 条")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            if (filteredRecords.isEmpty()) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(if (records.isEmpty()) "暂无请求记录" else "没有匹配的记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(filteredRecords, key = { it.id }) { record ->
                RequestRecordListCard(
                    record = record,
                    expanded = record.id in expandedIds,
                    onToggleExpanded = {
                        expandedIds = if (record.id in expandedIds) expandedIds - record.id
                        else expandedIds + record.id
                    },
                    onCopyUrl = {
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("请求地址", record.url)
                        )
                    },
                    onOpenDetail = record.requestId?.let { requestId ->
                        { viewModel.openTraceDetail(requestId) }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
