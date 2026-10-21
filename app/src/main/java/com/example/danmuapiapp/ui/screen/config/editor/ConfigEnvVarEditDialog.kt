package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import com.example.danmuapiapp.domain.model.EnvType
import com.example.danmuapiapp.domain.model.EnvVarDef
import java.util.Locale

@Composable
internal fun EnvVarEditDialog(
    def: EnvVarDef,
    currentValue: String,
    categoryLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerateBiliQr: suspend () -> Result<BilibiliQrGenerateResult>,
    onPollBiliQr: suspend (String) -> Result<BilibiliQrPollResult>,
    onVerifyBiliCookie: suspend (String) -> Result<BilibiliCookieVerifyResult>,
    onVerifyAiConnectivity: suspend (String) -> Result<AiConnectivityVerifyResult>,
    dandanplayAccount: String,
    onVerifyDandanplayAccount: suspend (String, String) -> Result<DandanplayVerifyResult>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var value by remember(def.key, currentValue) { mutableStateOf(currentValue) }
    var showPassword by remember(def.key) { mutableStateOf(false) }

    val normalizedKey = remember(def.key) { def.key.trim().uppercase(Locale.getDefault()) }
    val autoMatchValidation = remember(normalizedKey, value, def.options) {
        if (normalizedKey == KEY_AUTO_MATCH_MAPPING_TABLE) {
            validateAutoMatchMappingTable(value, def.options)
        } else {
            AutoMatchMappingValidation(valid = true)
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Form,
        tone = AppDialogTone.Brand,
        title = { Text("编辑配置项") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 与核心自带前端一致：先列变量类别/变量名/值类型，再是值输入控件，最后是描述。
                ReadonlyInfoRow("变量类别", categoryLabel)
                ReadonlyInfoRow("变量名", def.key, monospace = true)
                ReadonlyInfoRow("值类型", envTypeLabel(def.type))

                val handledSpecial = when (normalizedKey) {
                    // 与核心自带前端一致：只对 COLOR_POOL 等少数变量做专用界面，
                    // 其余变量一律按 type 走通用控件（来源/平台/映射表都不再特判）。
                    KEY_COLOR_POOL -> {
                        ColorPoolEditor(
                            rememberKey = def.key,
                            value = value,
                            onValueChange = { value = it }
                        )
                        true
                    }

                    KEY_ANIME_TITLE_FILTER,
                    KEY_EPISODE_TITLE_FILTER,
                    KEY_TITLE_NOISE_FILTER -> {
                        StableTextValueEditor(
                            def = def,
                            value = value,
                            showPassword = showPassword,
                            onTogglePassword = { showPassword = !showPassword },
                            onValueChange = { value = it }
                        )
                        // 核心前端：过滤类变量在文本域下方右侧提供「查看最近数据」。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            RecentDataToggle(
                                rememberKey = def.key,
                                currentKey = def.key,
                                onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                                compact = true
                            )
                        }
                        true
                    }

                    KEY_CUSTOM_MERGE_RULES -> {
                        CompactCustomMergeRulesEditor(
                            rememberKey = def.key,
                            value = value,
                            onValueChange = { value = it },
                            options = def.options,
                            onFetchRecentAnimeCache = onFetchRecentAnimeCache
                        )
                        true
                    }

                    KEY_DANMU_OFFSET -> {
                        CompactDanmuOffsetEditor(
                            rememberKey = def.key,
                            value = value,
                            onValueChange = { value = it },
                            options = def.options,
                            onFetchRecentAnimeCache = onFetchRecentAnimeCache
                        )
                        true
                    }

                    KEY_BILIBILI_COOKIE -> {
                        BilibiliCookieEditor(
                            value = value,
                            onValueChange = { value = it },
                            onGenerateBiliQr = onGenerateBiliQr,
                            onPollBiliQr = onPollBiliQr,
                            onVerifyBiliCookie = onVerifyBiliCookie
                        )
                        true
                    }

                    KEY_AI_API_KEY -> {
                        AiApiKeyEditor(
                            value = value,
                            onValueChange = { value = it },
                            onVerifyAiConnectivity = onVerifyAiConnectivity
                        )
                        true
                    }

                    KEY_DANDANPLAY_PASSWORD -> {
                        DandanplayPasswordEditor(
                            value = value,
                            account = dandanplayAccount,
                            onValueChange = { value = it },
                            onVerifyDandanplayAccount = onVerifyDandanplayAccount
                        )
                        true
                    }

                    else -> false
                }

                if (!handledSpecial) {
                    when (def.type) {
                        EnvType.BOOLEAN -> StableBooleanValueEditor(
                            def = def,
                            value = value,
                            onValueChange = { value = it }
                        )

                        EnvType.NUMBER -> StableNumberValueEditor(
                            def = def,
                            value = value,
                            onValueChange = { value = it }
                        )

                        EnvType.SELECT -> StableSelectValueEditor(
                            def = def,
                            value = value,
                            onValueChange = { value = it }
                        )

                        EnvType.MULTI_SELECT -> StableMultiSelectValueEditor(
                            envKey = def.key,
                            def = def,
                            value = value,
                            onValueChange = { value = it },
                            onFetchRecentAnimeCache = onFetchRecentAnimeCache
                        )

                        EnvType.MAP -> StableMapValueEditor(
                            envKey = def.key,
                            value = value,
                            onValueChange = { value = it },
                            onFetchRecentAnimeCache = onFetchRecentAnimeCache
                        )

                        EnvType.COLOR_LIST -> ColorListEditor(
                            rememberKey = def.key,
                            value = value,
                            onValueChange = { value = it },
                            title = def.description.ifBlank { def.key },
                            envKey = def.key
                        )

                        EnvType.TEXT, EnvType.CUSTOM_MERGE_RULES, EnvType.TIMELINE_OFFSET -> StableTextValueEditor(
                            def = def,
                            value = value,
                            showPassword = showPassword,
                            onTogglePassword = { showPassword = !showPassword },
                            onValueChange = { value = it }
                        )
                    }
                }
                ReadonlyInfoRow("描述", def.description.ifBlank { "—" })
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppGlassButton(
                    onClick = { onSave(value) },
                    enabled = autoMatchValidation.valid,
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
                AppGlassButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            }
        }
    )
}

@Composable
private fun ReadonlyInfoRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppGlassSurface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                value.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** 核心前端 getEnvTypeLabel 的同名映射。 */
internal fun envTypeLabel(type: EnvType): String = when (type) {
    EnvType.BOOLEAN -> "布尔"
    EnvType.NUMBER -> "数字"
    EnvType.SELECT -> "单选"
    EnvType.MAP -> "映射"
    EnvType.MULTI_SELECT -> "多选"
    else -> "文本"
}
