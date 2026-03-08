package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.renderFileNameTemplatePreview
import com.example.danmuapiapp.ui.theme.appDangerButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryIconButtonColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DanmuDownloadScreen(
    onBack: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    providedViewModel: DanmuDownloadViewModel? = null
) {
    val activity = LocalContext.current.findActivity()
    val viewModel = providedViewModel ?: hiltViewModel<DanmuDownloadViewModel>(
        viewModelStoreOwner = checkNotNull(activity) { "无法获取 Activity 作用域" }
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val queueTasks by viewModel.queueTasks.collectAsStateWithLifecycle()
    val inEpisodeDetail = viewModel.currentAnime != null
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val queueSummary = viewModel.queueSummary()

    LaunchedEffect(viewModel.operationMessage) {
        val msg = viewModel.operationMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        FilledIconButton(
                            onClick = {
                                if (inEpisodeDetail) viewModel.backToAnimeList() else onBack()
                            },
                            colors = primaryActionIconButtonColors(),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (inEpisodeDetail) {
                                    viewModel.currentAnime?.title ?: "弹幕下载"
                                } else {
                                    "弹幕下载中心"
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (inEpisodeDetail) {
                                    "剧集选择与批量下载"
                                } else {
                                    "搜索动漫并管理下载队列"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = onOpenDownloadSettings,
                        colors = primaryActionIconButtonColors(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, "下载设置", Modifier.size(18.dp))
                    }
                }

                DownloadPanelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBadge("活动", queueSummary.active)
                        StatBadge("队列", queueTasks.size)
                        StatBadge("记录", records.size, MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(6.dp))
                    val summaryText = when {
                        queueSummary.running > 0 -> "队列运行中：${viewModel.queueRunningStatusText()}"
                        queueSummary.pending > 0 -> "队列已暂停，剩余 ${queueSummary.pending} 项待处理"
                        else -> "当前队列空闲，可在搜索页选择剧集后开始下载"
                    }
                    Text(
                        summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)
                    )
                ) {
                    val queueActive = queueSummary.active
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("搜索下载") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("任务队列")
                                    if (queueActive > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("$queueActive")
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("下载记录")
                                    if (records.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        ) {
                                            Text("${records.size}")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> SearchDownloadPage(viewModel = viewModel, settings = settings)
                        1 -> QueuePage(viewModel = viewModel, queueTasks = queueTasks)
                        2 -> RecordsPage(records = records, onClear = viewModel::clearRecords)
                    }
                }
            }

            if (inEpisodeDetail) {
                val selectedCount = viewModel
                    .visibleEpisodes()
                    .count { viewModel.selectedEpisodeIds.contains(it.episodeId) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "已选 $selectedCount 集",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "支持跨来源混合下载，重复项会自动去重",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (viewModel.isDownloading) {
                                OutlinedButton(onClick = viewModel::cancelDownload) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("取消")
                                }
                            }
                            Button(
                                onClick = viewModel::startDownloadSelectedEpisodes,
                                enabled = !viewModel.isDownloading && selectedCount > 0,
                                colors = primaryActionButtonColors()
                            ) {
                                Icon(Icons.Rounded.CloudDownload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("下载（$selectedCount）")
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewModel.errorMessage != null) {
        AppBottomSheetDialog(
            onDismissRequest = viewModel::clearError,
            style = AppBottomSheetStyle.Confirm,
            tone = AppBottomSheetTone.Danger,
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } }
        )
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

@Composable
internal fun ThrottleHintBanner(hint: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun StatBadge(
    label: String,
    value: Int,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Text(
            "$label $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
internal fun DownloadPanelCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
internal fun primaryActionButtonColors() = appPrimaryButtonColors()

@Composable
internal fun primaryActionIconButtonColors() = appPrimaryIconButtonColors()

@Composable
internal fun primarySelectionFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
)
