@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip

@Composable
internal fun VodServersEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "VOD 站点",
        description = "直接维护站点列表；快速添加只生成“名称@地址”。",
        textLabel = "站点列表",
        placeholder = "站点名@https://example.com/api.php/provide/vod",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加 VOD 站点"
    ) { appendRule, closePanel ->
        var name by remember(rememberKey) { mutableStateOf("") }
        var url by remember(rememberKey) { mutableStateOf("") }
        SimpleField(name, { name = it }, "名称（可空）", "我的站点")
        SimpleField(url, { url = it }, "接口地址", "https://example.com/api.php/provide/vod")
        QuickActionRow(
            onAppend = {
                val cleanUrl = url.trim()
                val rule = if (cleanUrl.isBlank()) "" else if (name.isBlank()) cleanUrl else "${name.trim()}@$cleanUrl"
                appendRule(rule, ",")
            },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun SourceConcurrencyBySourceEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    sourceOptions: List<String>,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "按来源并发数",
        description = "给某个来源单独设置并发数。可以直接手写 source:数量。",
        textLabel = "并发覆盖规则",
        placeholder = "bilibili:2,qq:3",
        minLines = 2,
        maxLines = 6,
        quickTitle = "添加来源并发规则"
    ) { appendRule, closePanel ->
        var source by remember(rememberKey) { mutableStateOf("") }
        var concurrency by remember(rememberKey) { mutableStateOf("") }
        SimpleField(source, { source = it }, "来源", "bilibili")
        OptionAssistChips(sourceOptions, onPick = { source = it })
        SimpleField(concurrency, { concurrency = it }, "并发数", "2")
        QuickActionRow(
            onAppend = {
                val rule = if (source.isBlank() || concurrency.isBlank()) "" else "${normalizeSource(source)}:${concurrency.trim()}"
                appendRule(rule, ",")
            },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun IpBlacklistEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "IP 黑名单",
        description = "支持单个 IP、CIDR 或正则。默认直接追加到列表。",
        textLabel = "黑名单列表",
        placeholder = "1.2.3.4,10.0.0.0/24,/^192\\.168\\./",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加 IP 规则"
    ) { appendRule, closePanel ->
        var kind by remember(rememberKey) { mutableStateOf("exact") }
        var valueText by remember(rememberKey) { mutableStateOf("") }
        var flags by remember(rememberKey) { mutableStateOf("") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppGlassFilterChip(selected = kind == "exact", onClick = { kind = "exact" }, label = { Text("IP") })
            AppGlassFilterChip(selected = kind == "cidr", onClick = { kind = "cidr" }, label = { Text("CIDR") })
            AppGlassFilterChip(selected = kind == "regex", onClick = { kind = "regex" }, label = { Text("正则") })
        }
        SimpleField(valueText, { valueText = it }, if (kind == "regex") "正则内容" else "IP / 网段", if (kind == "cidr") "10.0.0.0" else "1.2.3.4")
        if (kind == "cidr") SimpleField(flags, { flags = it }, "掩码位", "24")
        if (kind == "regex") SimpleField(flags, { flags = it }, "正则 flags（可空）", "i")
        QuickActionRow(
            onAppend = {
                val clean = valueText.trim()
                val rule = when {
                    clean.isBlank() -> ""
                    kind == "cidr" -> "$clean/${flags.trim().ifBlank { "24" }}"
                    kind == "regex" -> "/$clean/${flags.trim()}"
                    else -> clean
                }
                appendRule(rule, ",")
            },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun KeywordListEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    subtitle: String,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = title,
        description = subtitle.ifBlank { "直接维护关键词列表。" },
        textLabel = "关键词列表",
        placeholder = "关键词1,关键词2",
        minLines = 2,
        maxLines = 6,
        quickTitle = "添加关键词"
    ) { appendRule, closePanel ->
        var keyword by remember(rememberKey) { mutableStateOf("") }
        SimpleField(keyword, { keyword = it }, "关键词", "广告")
        QuickActionRow(
            onAppend = { appendRule(keyword, ",") },
            onCancel = closePanel
        )
    }
}
