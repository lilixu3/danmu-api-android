package com.example.danmuapiapp.ui.screen.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.TraceValue
import com.example.danmuapiapp.domain.model.RequestTraceConfig
import com.example.danmuapiapp.domain.model.RequestTraceDetail
import com.example.danmuapiapp.domain.model.RequestTraceStep
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RequestTraceDetailDialog(
    state: RequestRecordsViewModel.TraceDetailState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Form,
        scrollContent = false,
        title = { Text("请求处理详情") },
        text = {
            when {
                state.loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在读取处理流水…")
                    }
                }
                state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                state.detail != null -> key(state.detail.id) { RequestTraceDetailContent(state.detail) }
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) { Text("关闭") }
        },
        confirmButton = if (state.error != null) {
            {
                AppGlassButton(onClick = onRetry, tint = MaterialTheme.colorScheme.primary) {
                    Text("重试")
                }
            }
        } else null
    )
}

@Composable
private fun ColumnScope.RequestTraceDetailContent(detail: RequestTraceDetail) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = rememberSaveableStateHolder()
    PrimaryTabRow(selectedTabIndex = tab) {
        listOf("处理总览", "配置覆盖").forEachIndexed { index, label ->
            Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
        }
    }
    tabs.SaveableStateProvider(tab) {
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (tab == 0) TraceOverview(detail) else TraceConfigs(detail.configs)
        }
    }
}

@Composable
private fun TraceOverview(detail: RequestTraceDetail) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("总耗时", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text("${detail.totalMs} ms", style = MaterialTheme.typography.headlineSmall)
                }
                Text(detail.statusCode?.let { "HTTP $it" } ?: "未记录状态码",
                    style = MaterialTheme.typography.labelLarge,
                    color = if ((detail.statusCode ?: 0) >= 400) colors.error else colors.primary)
            }
            val sum = detail.prepareMs.toDouble() + detail.handlerMs
            if (sum > 0) {
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val split = size.width * (detail.prepareMs / sum).toFloat()
                    drawRect(colors.tertiary, size = Size(split, size.height))
                    drawRect(colors.primary, Offset(split, 0f), Size(size.width - split, size.height))
                }
            }
            Text("准备 ${detail.prepareMs} ms   ·   接口处理 ${detail.handlerMs} ms", style = MaterialTheme.typography.labelMedium)
            HorizontalDivider()
            Text("${detail.method} ${detail.path}${detail.query}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            val time = remember(detail.timestamp) {
                if (detail.timestamp <= 0) "未知时间" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(detail.timestamp))
            }
            Text(listOf(time, detail.clientIp).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    detail.diagnosis?.let { diagnosis ->
        Text("结果诊断", style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (diagnosis.conclusion.isNotBlank()) {
                    Text(diagnosis.conclusion, style = MaterialTheme.typography.bodyMedium,
                        color = if (diagnosis.zeroStep != null) colors.error else colors.onSurface)
                }
                diagnosis.chain.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tint = if (step.zero) colors.error else colors.secondary
                        Box(Modifier.size(6.dp).background(tint, CircleShape))
                        Text(step.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        val count = step.rawCount?.let { "$it → ${step.count}" } ?: step.count.toString()
                        Text(if (step.name == diagnosis.zeroStep) "$count 项 · 归零" else "$count 项",
                            style = MaterialTheme.typography.labelMedium, color = tint)
                    }
                }
            }
        }
    }
    Text("处理流水线", style = MaterialTheme.typography.titleMedium)
    if (detail.steps.isEmpty()) {
        Text("本次请求没有记录处理步骤。", style = MaterialTheme.typography.bodySmall)
    } else {
        val longest = detail.steps.maxOf { it.durationMs }
        Column {
            detail.steps.forEachIndexed { index, step ->
                TraceStepCard(index, step, longest > 0 && step.durationMs == longest, index == detail.steps.lastIndex)
            }
        }
    }
}

@Composable
private fun TraceStepCard(index: Int, step: RequestTraceStep, slowest: Boolean, last: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = when (step.status.lowercase()) {
        "fail", "failed", "failure", "error" -> colors.error
        "empty", "miss" -> colors.tertiary
        "skip" -> colors.onSurfaceVariant
        else -> colors.primary
    }
    val hasDetails = step.inputs != null || step.outputs != null || step.table != null || step.changes.isNotEmpty() || step.error != null
    var expanded by rememberSaveable { mutableStateOf(index == 0) }
    Row(Modifier.fillMaxWidth().drawBehind {
        if (!last) {
            val x = 12.dp.toPx()
            drawLine(colors.outlineVariant, Offset(x, x), Offset(x, size.height), 1.dp.toPx())
        }
    }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(24.dp).background(colors.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = colors.onSecondaryContainer)
        }
        Surface(Modifier.weight(1f).padding(bottom = 8.dp), shape = RoundedCornerShape(10.dp),
            color = colors.surfaceContainerHigh) {
            Column {
                Column(Modifier.fillMaxWidth().clickable(enabled = hasDetails, onClickLabel = if (expanded) "收起步骤" else "展开步骤") {
                    expanded = !expanded
                }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(step.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (hasDetails) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    }
                    Text(listOfNotNull(statusLabel(step.status), "${step.durationMs} ms", "最慢".takeIf { slowest }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = tint)
                    step.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (expanded && hasDetails) {
                    Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        TraceValueBlock("输入", step.inputs)
                        TraceValueBlock("输出", step.outputs)
                        TraceValueBlock("错误", step.error)
                        step.table?.let { TraceDataTable(it) }
                        if (step.changes.isNotEmpty()) {
                            Text("配置生效与过滤判定", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            step.changes.forEach { TraceChangeCard(it) }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status.lowercase()) {
    "ok", "success", "done", "" -> "成功"
    "fail", "error", "failed", "failure" -> "失败"
    "hit" -> "命中"
    "miss" -> "未命中"
    "empty" -> "无结果"
    "skip" -> "跳过"
    "running" -> "进行中"
    else -> status
}

@Composable
private fun TraceConfigs(configs: List<RequestTraceConfig>) {
    var filter by rememberSaveable { mutableIntStateOf(1) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("全部", "已读取", "命中").forEachIndexed { index, label ->
            AppGlassFilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
        }
    }
    val visible = configs.filter { filter == 0 || (filter == 1 && it.used) || (filter == 2 && it.hit == true) }
    Text("已读取 ${configs.count { it.used }} / ${configs.size} 项", style = MaterialTheme.typography.labelMedium)
    if (visible.isEmpty()) Text("没有符合条件的配置项。", style = MaterialTheme.typography.bodySmall)
    val labels = mapOf("api" to "API 配置", "source" to "源配置", "match" to "匹配配置",
        "danmu" to "弹幕配置", "cache" to "缓存配置", "system" to "系统配置", "runtime" to "运行环境")
    visible.groupBy { it.category }.forEach { (category, group) ->
        key(category) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(labels[category] ?: category, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${group.size} 项", style = MaterialTheme.typography.labelMedium)
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (expanded) "收起" else "展开", Modifier.size(18.dp))
                    }
                    if (expanded) group.forEach { config ->
                        key(config.name) {
                            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HorizontalDivider()
                                Text(config.name, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
                                Text(listOfNotNull(if (config.used) "已读取" else "未读取",
                                    config.hit?.let { if (it) "命中" else "未命中" }).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                if (config.description.isNotBlank()) Text(config.description, style = MaterialTheme.typography.bodySmall)
                                val value = config.value
                                val unset = value == null || value == TraceValue.Empty || (value is TraceValue.Scalar && value.text.isBlank())
                                TraceValueBlock("当前值", if (unset) TraceValue.Scalar("未设置") else value)
                                config.step?.let { Text("读取于 $it", style = MaterialTheme.typography.labelSmall) }
                                config.effectStep?.let { Text("生效于 $it", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}
