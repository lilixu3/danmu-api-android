package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiDebugPane(viewModel: ApiTestViewModel, base: String, onCopy: (String) -> Unit,
    onExport: (String, String) -> Unit, onLogs: () -> Unit) {
    var selectedKey by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var rawBody by rememberSaveable { mutableStateOf("") }
    val values = remember { mutableStateMapOf<String, String>() }
    val endpoint = viewModel.endpoints.firstOrNull { it.key == selectedKey }
    var showFull by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(endpoint?.key) {
        values.clear()
        endpoint?.let { values.putAll(apiSampleValues(it.key)) }
        rawBody = endpoint?.bodyHint?.removePrefix("示例：").orEmpty()
        showFull = false
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ApiCard {
            ApiLabel("选择接口")
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(value = endpoint?.title ?: "请选择接口", onValueChange = {}, readOnly = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp), singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    viewModel.endpoints.forEach { item ->
                        DropdownMenuItem(text = {
                            Column {
                                Text(item.title, fontSize = 12.5.sp)
                                ApiHint("${item.method} ${item.pathTemplate}")
                            }
                        }, onClick = { selectedKey = item.key; expanded = false; viewModel.clearDebugResponse(); viewModel.dismissError() })
                    }
                }
            }
            if (endpoint != null) Row(verticalAlignment = Alignment.CenterVertically) {
                ApiTag(endpoint.method)
                Spacer(Modifier.width(8.dp))
                Text(endpoint.pathTemplate, Modifier.weight(1f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { values.clear(); values.putAll(apiSampleValues(endpoint.key)); rawBody = endpoint.bodyHint.removePrefix("示例：") }) {
                    Text("填入示例", fontSize = 11.5.sp)
                }
            }
        }
        if (endpoint != null) ApiCard {
            ApiLabel("请求参数")
            if (endpoint.params.isEmpty() && !endpoint.hasRawBody) ApiHint("此接口无需参数")
            endpoint.params.forEach { param ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${param.label}${if (param.required) " *" else ""}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (param.inputType == ApiParamInputType.Select) {
                        ApiSelectParam(param, values[param.name].orEmpty()) { values[param.name] = it }
                    } else ApiInput(values[param.name].orEmpty(), { values[param.name] = it }, param.placeholder)
                    if (param.helper.isNotBlank()) ApiHint(param.helper)
                }
            }
            if (endpoint.hasRawBody) {
                ApiHint("请求体（JSON）")
                ApiInput(rawBody, { rawBody = it }, endpoint.bodyHint.removePrefix("示例："), multiline = true, mono = true)
            }
            ApiAction(if (viewModel.isLoading) "请求中…" else "发送请求", Icons.AutoMirrored.Rounded.Send,
                { viewModel.sendRequest(endpoint, base, values.toMap(), rawBody) }, Modifier.fillMaxWidth(), busy = viewModel.isLoading)
        }
        viewModel.errorMessage?.let { ApiErrorCard(it, viewModel::dismissError) }
        viewModel.debugResponse?.let { response ->
            ApiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ApiLabel("响应结果")
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onCopy(response.fullText) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.ContentCopy, "复制响应", Modifier.size(17.dp))
                    }
                    IconButton(onClick = { onExport("api-${endpoint?.key ?: "response"}.txt", response.fullText) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.FileDownload, "导出响应", Modifier.size(17.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ApiTag("HTTP ${response.responseCode}", if (response.responseCode in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    ApiHint("${response.responseDurationMs}ms · ${apiBytes(response.bodySizeBytes)}")
                }
                SelectionContainer {
                    Text(if (showFull) response.fullText.take(100_000) else response.previewText,
                        Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 17.sp)
                }
                if (response.previewTruncated) {
                    TextButton(onClick = { showFull = !showFull }) { Text(if (showFull) "收起响应" else "展开响应", fontSize = 12.sp) }
                    ApiHint("预览最多显示 100,000 字符；复制和导出包含完整响应。")
                }
            }
        }
        if (viewModel.requestUrl.isNotBlank()) ApiCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApiLabel("实际请求")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onCopy(viewModel.curlCommand) }) { Text("复制 cURL", fontSize = 12.sp) }
            }
            SelectionContainer { Text(viewModel.requestUrl, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        ApiCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApiLabel("本次请求（${viewModel.debugCalls.size}）")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onLogs, modifier = Modifier.size(34.dp)) { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, "服务日志", Modifier.size(17.dp)) }
                IconButton(onClick = { onCopy(viewModel.debugCalls.joinToString("\n\n") {
                    "${it.method} ${it.url} → ${it.statusCode ?: "失败"} (${it.elapsedMs}ms)\n${it.preview}"
                }) }, enabled = viewModel.debugCalls.isNotEmpty(), modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ContentCopy, "复制本次请求记录", Modifier.size(17.dp))
                }
                IconButton(onClick = viewModel::clearDebugCalls, enabled = viewModel.debugCalls.isNotEmpty(), modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ClearAll, "清空本次请求记录", Modifier.size(17.dp))
                }
            }
            if (viewModel.debugCalls.isEmpty()) ApiHint("发送请求后，这里显示本次会话的请求记录。")
            viewModel.debugCalls.forEach { call ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ApiTag(call.statusCode?.toString() ?: "失败", if (call.statusCode in 200..299) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Column(Modifier.weight(1f)) {
                        Text("${call.method} ${call.url}", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ApiHint("${call.elapsedMs}ms")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiSelectParam(param: ApiParamConfig, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(value.ifBlank { "默认" }, {}, readOnly = true, singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            (listOf("") + param.options).forEach { option ->
                DropdownMenuItem(text = { Text(option.ifBlank { "默认" }, fontSize = 13.sp) },
                    onClick = { onChange(option); expanded = false })
            }
        }
    }
}

private fun apiSampleValues(key: String): Map<String, String> = when (key) {
    "searchAnime" -> mapOf("keyword" to "凡人修仙传")
    "searchEpisodes" -> mapOf("anime" to "凡人修仙传")
    "matchAnime" -> mapOf("fileName" to "凡人修仙传 S01E01 2160p WEB-DL")
    "getBangumi" -> mapOf("animeId" to "236379")
    "getComment" -> mapOf("commentId" to "10009", "format" to "json", "duration" to "true")
    "getCommentByUrl" -> mapOf("format" to "json", "duration" to "true")
    "getSegmentComment" -> mapOf("format" to "json")
    else -> emptyMap()
}
