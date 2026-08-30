package com.example.danmuapiapp.desktop.core

import java.io.File

/** Environment variable editor types declared by a core's envs.js. */
enum class CoreEnvType {
    Text,
    Number,
    Boolean,
    Select,
    MultiSelect,
    Map,
}

enum class CoreEnvApplyMode {
    HotReload,
    RestartService,
}

enum class CoreEnvValueSource {
    SystemEnvironment,
    DotEnv,
    CoreDefault,
    Unconfigured,
}

data class CoreEnvDefinition(
    val key: String,
    val category: String,
    val type: CoreEnvType,
    val description: String,
    val options: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val defaultValue: String? = null,
    val sensitive: Boolean = false,
    val applyMode: CoreEnvApplyMode = CoreEnvApplyMode.HotReload,
)

data class CoreEnvValue(
    val definition: CoreEnvDefinition,
    val configuredValue: String?,
    val effectiveValue: String?,
    val isConfigured: Boolean,
    val source: CoreEnvValueSource,
)

data class CoreEnvSnapshot(
    val variant: DesktopCoreVariant,
    val scriptDir: File,
    val coreDir: File,
    val envsJsFile: File,
    val envFile: File,
    val rawContent: String,
    val definitions: List<CoreEnvDefinition>,
    val values: Map<String, CoreEnvValue>,
) {
    val configuredCount: Int get() = values.values.count { it.isConfigured }
}

internal val CORE_ENV_HOST_KEYS: Set<String> = setOf(
    "DANMU_API_PORT",
    "DANMU_API_HOST",
    "DANMU_API_PROXY_PORT",
    "DANMU_API_VARIANT",
    "DANMU_API_HOME",
    "DANMU_API_RUNTIME_IDENTITY",
    "DANMU_API_WORKER",
    "DANMU_API_HOT_RELOAD",
    "DANMU_API_LOG_TO_FILE",
    "DANMU_API_LOG_MAX_BYTES",
)

internal fun coreEnvCategoryLabel(category: String): String = when (category.lowercase()) {
    "api" -> "API 配置"
    "source" -> "数据源配置"
    "match" -> "匹配配置"
    "danmu" -> "弹幕配置"
    "cache" -> "缓存配置"
    "system" -> "系统配置"
    else -> category.ifBlank { "其他配置" }
}

internal val CORE_ENV_CATEGORY_ORDER: List<String> = listOf("api", "source", "match", "danmu", "cache", "system")

internal fun CoreEnvDefinition.typeLabel(): String = when (type) {
    CoreEnvType.Text -> "文本"
    CoreEnvType.Number -> "数字"
    CoreEnvType.Boolean -> "开关"
    CoreEnvType.Select -> "单选"
    CoreEnvType.MultiSelect -> "多选"
    CoreEnvType.Map -> "规则文本"
}

internal fun CoreEnvDefinition.applyModeLabel(): String = when (applyMode) {
    CoreEnvApplyMode.HotReload -> "核心热加载"
    CoreEnvApplyMode.RestartService -> "重启服务"
}

internal fun maskCoreEnvValue(value: CoreEnvValue): String = when {
    value.definition.sensitive && value.isConfigured -> "••••••••"
    value.definition.sensitive && value.source == CoreEnvValueSource.SystemEnvironment -> "••••••••"
    !value.isConfigured && value.source == CoreEnvValueSource.Unconfigured -> "未配置"
    value.effectiveValue.isNullOrBlank() -> "空（核心默认）"
    else -> value.effectiveValue
}
