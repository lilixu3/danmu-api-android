package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTestScreen(
    onBack: () -> Unit,
    viewModel: ApiTestViewModel = hiltViewModel()
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current

    var endpointExpanded by remember { mutableStateOf(false) }
    var endpointIndex by remember { mutableStateOf(0) }
    var baseUrl by remember(runtimeState.localUrl, runtimeState.lanUrl) {
        mutableStateOf(RuntimeUrlParser.extractBase(runtimeState.localUrl))
    }
    var rawBody by remember {
        mutableStateOf("{\n  \"type\": \"qq\",\n  \"segment_start\": 0,\n  \"segment_end\": 30000,\n  \"url\": \"https://...\"\n}")
    }
    val paramValues = remember { mutableStateMapOf<String, String>() }
    val endpoint = viewModel.endpoints.getOrNull(endpointIndex) ?: viewModel.endpoints.first()

    LaunchedEffect(endpoint.key) { paramValues.clear() }

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
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                }
                Column {
                    Text("接口调试", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "${endpoint.method} ${endpoint.pathTemplate}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (viewModel.responseBody.isNotBlank()) {
                    FilledTonalIconButton(
                        onClick = {
                            clipboard.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "接口响应",
                                    prettyPrintJson(viewModel.responseBody)
                                )
                            )
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, "复制响应", Modifier.size(18.dp))
                    }
                }
                if (viewModel.responseCode != null) {
                    FilledTonalIconButton(
                        onClick = { viewModel.clearResult() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.ClearAll, "清空", Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Request config section
            RequestConfigSection(
                endpoint = endpoint,
                endpoints = viewModel.endpoints,
                endpointExpanded = endpointExpanded,
                onExpandedChange = { endpointExpanded = it },
                endpointIndex = endpointIndex,
                onEndpointSelect = { endpointIndex = it; endpointExpanded = false },
                baseUrl = baseUrl,
                onBaseUrlChange = { baseUrl = it },
                onUseLocal = { baseUrl = RuntimeUrlParser.extractBase(runtimeState.localUrl) },
                onUseLan = { baseUrl = RuntimeUrlParser.extractBase(runtimeState.lanUrl) },
                paramValues = paramValues,
                rawBody = rawBody,
                onRawBodyChange = { rawBody = it },
                isLoading = viewModel.isLoading,
                onSend = {
                    viewModel.sendRequest(
                        endpoint = endpoint,
                        baseUrl = baseUrl,
                        paramValues = paramValues,
                        rawBody = rawBody
                    )
                }
            )

            // Request preview
            if (viewModel.requestUrl.isNotBlank()) {
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
                                "请求预览",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    clipboard.nativeClipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "curl",
                                            viewModel.curlCommand
                                        )
                                    )
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, "复制CURL", Modifier.size(14.dp))
                            }
                        }
                        Text(
                            viewModel.requestUrl,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        if (viewModel.curlCommand.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    viewModel.curlCommand,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Response section
            if (viewModel.responseCode != null || viewModel.responseBody.isNotBlank()) {
                ResponseSection(
                    responseCode = viewModel.responseCode,
                    responseBody = viewModel.responseBody,
                    durationMs = viewModel.responseDurationMs
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Error dialog
    if (viewModel.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearResult() },
            title = { Text("请求失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearResult() }) { Text("知道了") }
            }
        )
    }
}

private fun prettyPrintJson(raw: String): String {
    val trimmed = raw.trim()
    return try {
        when {
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            else -> raw
        }
    } catch (_: Exception) {
        raw
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestConfigSection(
    endpoint: ApiEndpointConfig,
    endpoints: List<ApiEndpointConfig>,
    endpointExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    endpointIndex: Int,
    onEndpointSelect: (Int) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    paramValues: MutableMap<String, String>,
    rawBody: String,
    onRawBodyChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit
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
            // Endpoint selector
            ExposedDropdownMenuBox(
                expanded = endpointExpanded,
                onExpandedChange = { onExpandedChange(!endpointExpanded) }
            ) {
                OutlinedTextField(
                    value = "${endpoint.title} · ${endpoint.method} ${endpoint.pathTemplate}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("接口") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endpointExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(
                    expanded = endpointExpanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    endpoints.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (item.method == "GET") {
                                            if (isSystemInDarkTheme()) Color(0xFF4ADE80).copy(alpha = 0.15f)
                                            else Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        } else {
                                            if (isSystemInDarkTheme()) Color(0xFFFBBF24).copy(alpha = 0.15f)
                                            else Color(0xFFFF9800).copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            item.method,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace, fontSize = 10.sp
                                            ),
                                            color = if (item.method == "GET") {
                                                if (isSystemInDarkTheme()) Color(0xFF4ADE80) else Color(0xFF4CAF50)
                                            } else {
                                                if (isSystemInDarkTheme()) Color(0xFFFBBF24) else Color(0xFFFF9800)
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        item.pathTemplate,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { onEndpointSelect(index) }
                        )
                    }
                }
            }

            // Base URL
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onUseLocal, label = { Text("本机") })
                AssistChip(onClick = onUseLan, label = { Text("局域网") })
            }

            // Dynamic params
            endpoint.params.forEach { param ->
                val current = paramValues[param.name].orEmpty()
                OutlinedTextField(
                    value = current,
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
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                if (param.inputType == ApiParamInputType.Select && param.options.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        param.options.forEach { option ->
                            FilterChip(
                                selected = current == option,
                                onClick = { paramValues[param.name] = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
            }

            // Raw body editor
            if (endpoint.hasRawBody) {
                OutlinedTextField(
                    value = rawBody,
                    onValueChange = onRawBodyChange,
                    label = { Text("JSON 请求体") },
                    supportingText = {
                        if (endpoint.bodyHint.isNotBlank()) Text(endpoint.bodyHint)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Send button
            Button(
                onClick = onSend,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("请求中...")
                } else {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("发送请求")
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun ResponseSection(
    responseCode: Int?,
    responseBody: String,
    durationMs: Long?
) {
    val isSuccess = responseCode != null && responseCode in 200..299
    val dark = isSystemInDarkTheme()
    val statusColor = when {
        responseCode == null -> MaterialTheme.colorScheme.onSurfaceVariant
        isSuccess -> if (dark) Color(0xFF4ADE80) else Color(0xFF4CAF50)
        responseCode in 400..499 -> if (dark) Color(0xFFFBBF24) else Color(0xFFFF9800)
        else -> if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    }
    val formattedBody = remember(responseBody) { prettyPrintJson(responseBody) }

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "响应",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        "${responseCode ?: "ERR"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (durationMs != null) {
                    Text(
                        "${durationMs}ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // JSON body
            if (formattedBody.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = formattedBody,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
