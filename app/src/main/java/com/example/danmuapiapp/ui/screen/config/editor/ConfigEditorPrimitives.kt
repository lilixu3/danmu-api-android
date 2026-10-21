@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import java.util.Locale

@Composable
internal fun SimpleQuickAppendEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    description: String,
    textLabel: String,
    placeholder: String,
    addButtonText: String = "添加规则",
    minLines: Int = 3,
    maxLines: Int = 8,
    quickTitle: String = "快速添加",
    quickContent: @Composable (appendRule: (String, String) -> Unit, closePanel: () -> Unit) -> Unit,
) {
    var showQuick by remember(rememberKey) { mutableStateOf(false) }

    fun appendRule(rule: String, separator: String) {
        val cleanRule = rule.trim()
        if (cleanRule.isBlank()) return
        onValueChange(appendSegment(value, cleanRule, separator))
        showQuick = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(textLabel) },
            placeholder = { Text(placeholder) },
            singleLine = false,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlassButton(onClick = { showQuick = true }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(addButtonText)
            }
            Text(
                "可直接手写；快速添加只负责生成一条并追加到输入框。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (showQuick) {
            AppGlassSurface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(quickTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        AppGlassIconButton(onClick = { showQuick = false }) {
                            Icon(Icons.Rounded.Close, "关闭")
                        }
                    }
                    quickContent(::appendRule) { showQuick = false }
                }
            }
        }
    }
}

@Composable
internal fun SimpleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 4,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun OptionAssistChips(
    options: List<String>,
    onPick: (String) -> Unit,
    maxCount: Int = 14,
) {
    val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(maxCount)
    if (cleanOptions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cleanOptions.forEach { option ->
            AppGlassAssistChip(onClick = { onPick(option) }, label = { Text(option) })
        }
    }
}

@Composable
internal fun QuickActionRow(
    onAppend: () -> Unit,
    appendLabel: String = "写入输入框",
    onCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppGlassButton(onClick = onAppend) { Text(appendLabel) }
        AppGlassButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
internal fun RecentDataToggle(
    rememberKey: String,
    currentKey: String,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
    onFillMergeEntity: ((String, String, String) -> Unit)? = null,
    mergeFillRole: String? = null,
    mergeFillLabel: String = "填入",
    onFillOffsetEntity: ((String, String) -> Unit)? = null,
    onAddSourcePair: ((String) -> Unit)? = null,
    compact: Boolean = false,
) {
    RecentAnimeCachePanel(
        rememberKey = "quick-$rememberKey-$currentKey",
        currentKey = currentKey,
        onFetchRecentAnimeCache = onFetchRecentAnimeCache,
        onFillMergeEntity = onFillMergeEntity,
        mergeFillRole = mergeFillRole,
        mergeFillLabel = mergeFillLabel,
        onFillOffsetEntity = onFillOffsetEntity,
        onAddSourcePair = onAddSourcePair,
        compact = compact
    )
}

internal fun appendSegment(current: String, segment: String, separator: String): String {
    val clean = segment.trim()
    if (clean.isBlank()) return current
    val base = current.trim().trimEnd(',', ';')
    if (base.isBlank()) return clean
    return base + separator + clean
}

internal fun appendToken(current: String, token: String, separator: String = ","): String {
    val clean = token.trim()
    if (clean.isBlank()) return current
    val parts = current.split(Regex("[,&，、\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    if (parts.none { it.equals(clean, ignoreCase = true) }) parts += clean
    return parts.joinToString(separator)
}

internal fun looseTokens(raw: String): List<String> {
    return raw.split(Regex("[,&，、\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.getDefault()) }
}

internal fun normalizeSource(raw: String): String {
    return raw.trim().lowercase(Locale.getDefault())
}
