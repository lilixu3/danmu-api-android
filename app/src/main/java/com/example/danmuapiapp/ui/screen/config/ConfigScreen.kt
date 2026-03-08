package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.EnvType
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val KEY_SOURCE_ORDER = "SOURCE_ORDER"
internal const val KEY_BILIBILI_COOKIE = "BILIBILI_COOKIE"
internal const val KEY_MERGE_SOURCE_PAIRS = "MERGE_SOURCE_PAIRS"
internal const val KEY_PLATFORM_ORDER = "PLATFORM_ORDER"
internal const val KEY_TITLE_MAPPING_TABLE = "TITLE_MAPPING_TABLE"
internal const val KEY_TITLE_PLATFORM_OFFSET_TABLE = "TITLE_PLATFORM_OFFSET_TABLE"
internal const val KEY_AI_API_KEY = "AI_API_KEY"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    viewModel: ConfigViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val envVars by viewModel.envVars.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val isCatalogLoading by viewModel.isCatalogLoading.collectAsStateWithLifecycle()
    val rawContent by viewModel.rawContent.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val editingVar by viewModel.editingVar.collectAsStateWithLifecycle()
    val isRawMode by viewModel.isRawMode.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.reload()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredCatalog = remember(catalog, searchQuery, envVars) {
        val q = searchQuery.lowercase()
        val items = if (catalog.isNotEmpty()) catalog else {
            envVars.keys.map { key -> EnvVarDef(key, "other", EnvType.TEXT, key) }
        }
        if (q.isBlank()) items
        else items.filter {
            it.key.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    val grouped = remember(filteredCatalog) { filteredCatalog.groupBy { it.category } }
    val configuredCount = envVars.size
    val totalCount = filteredCatalog.size

    val categoryLabels = mapOf(
        "api" to "API 配置",
        "source" to "数据源",
        "match" to "匹配",
        "danmu" to "弹幕",
        "cache" to "缓存",
        "system" to "系统",
        "vod" to "VOD",
        "bilibili" to "Bilibili",
        "proxy" to "代理",
        "log" to "日志",
        "other" to "其他"
    )

    if (editingVar != null && adminState.isAdminMode) {
        EnvVarEditDialog(
            def = editingVar!!,
            currentValue = envVars[editingVar!!.key] ?: "",
            onSave = { value ->
                viewModel.setValue(editingVar!!.key, value)
                viewModel.closeEditor()
            },
            onDelete = {
                viewModel.deleteKey(editingVar!!.key)
                viewModel.closeEditor()
            },
            onDismiss = { viewModel.closeEditor() },
            onGenerateBiliQr = { viewModel.generateBilibiliQr() },
            onPollBiliQr = { key -> viewModel.pollBilibiliQr(key) },
            onVerifyBiliCookie = { cookie -> viewModel.verifyBilibiliCookie(cookie) },
            onVerifyAiConnectivity = { apiKey -> viewModel.verifyAiConnectivity(apiKey) }
        )
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
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                }
                Column {
                    Text("配置管理", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "$totalCount 项配置 · $configuredCount 已设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = { viewModel.toggleRawMode() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isRawMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.Code,
                        if (isRawMode) "可视化模式" else "源码模式",
                        Modifier.size(18.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = { viewModel.reload() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!adminState.isAdminMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.AdminPanelSettings,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "当前为只读模式",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "请先开启管理员模式后再编辑敏感配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenAdminMode) {
                        Text("去开启")
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (isRawMode) {
            RawEditMode(
                rawContent = rawContent,
                onSave = { viewModel.saveRawContent(it) },
                envFilePath = viewModel.getEnvFilePath(),
                editable = adminState.isAdminMode,
                modifier = Modifier.weight(1f)
            )
        } else {
            VisualEditMode(
                grouped = grouped,
                categoryLabels = categoryLabels,
                envVars = envVars,
                searchQuery = searchQuery,
                onSearchChange = { viewModel.setSearch(it) },
                onEditVar = { viewModel.openEditor(it) },
                editable = adminState.isAdminMode,
                isCatalogLoading = isCatalogLoading,
                catalogEmpty = catalog.isEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

