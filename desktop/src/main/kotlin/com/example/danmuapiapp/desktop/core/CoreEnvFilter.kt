package com.example.danmuapiapp.desktop.core

/** Which values should be shown in the environment-variable workbench. */
enum class CoreEnvConfiguredFilter(val label: String) {
    All("全部"),
    Configured("已配置"),
    Default("使用默认"),
}

data class CoreEnvFilter(
    val query: String = "",
    val category: String? = null,
    val type: CoreEnvType? = null,
    val configured: CoreEnvConfiguredFilter = CoreEnvConfiguredFilter.All,
) {
    fun matches(value: CoreEnvValue): Boolean {
        val definition = value.definition
        val needle = query.trim()
        val queryMatches = needle.isBlank() || listOf(
            definition.key,
            definition.description,
            coreEnvCategoryLabel(definition.category),
        ).any { it.contains(needle, ignoreCase = true) }
        val categoryMatches = category == null || definition.category == category
        val typeMatches = type == null || definition.type == type
        val configuredMatches = when (configured) {
            CoreEnvConfiguredFilter.All -> true
            CoreEnvConfiguredFilter.Configured -> value.isConfigured
            CoreEnvConfiguredFilter.Default -> !value.isConfigured
        }
        return queryMatches && categoryMatches && typeMatches && configuredMatches
    }
}

data class CoreEnvGroup(
    val category: String,
    val definitions: List<CoreEnvDefinition>,
)

fun CoreEnvSnapshot.filteredDefinitions(filter: CoreEnvFilter): List<CoreEnvDefinition> = definitions
    .filter { definition -> values[definition.key]?.let(filter::matches) == true }
    .sortedWith(compareBy<CoreEnvDefinition>({ coreEnvCategoryRank(it.category) }, { it.key }))

fun CoreEnvSnapshot.groupedDefinitions(filter: CoreEnvFilter): List<CoreEnvGroup> = filteredDefinitions(filter)
    .groupBy { it.category }
    .entries
    .sortedWith(compareBy({ coreEnvCategoryRank(it.key) }, { it.key.lowercase() }))
    .map { (category, definitions) -> CoreEnvGroup(category, definitions) }

fun CoreEnvSnapshot.categoryOptions(): List<String> = definitions
    .map { it.category }
    .distinct()
    .sortedWith(compareBy({ coreEnvCategoryRank(it) }, { it.lowercase() }))

fun CoreEnvSnapshot.typeOptions(): List<CoreEnvType> = definitions
    .map { it.type }
    .distinct()
    .sortedBy { it.name }

private fun coreEnvCategoryRank(category: String): Int = CORE_ENV_CATEGORY_ORDER.indexOf(category).let { index ->
    if (index >= 0) index else CORE_ENV_CATEGORY_ORDER.size
}

internal fun CoreEnvValueSource.label(): String = when (this) {
    CoreEnvValueSource.SystemEnvironment -> "系统环境"
    CoreEnvValueSource.DotEnv -> ".env"
    CoreEnvValueSource.CoreDefault -> "核心默认"
    CoreEnvValueSource.Unconfigured -> "未配置"
}

internal fun CoreEnvType.label(): String = when (this) {
    CoreEnvType.Text -> "文本"
    CoreEnvType.Number -> "数字"
    CoreEnvType.Boolean -> "开关"
    CoreEnvType.Select -> "单选"
    CoreEnvType.MultiSelect -> "多选"
    CoreEnvType.Map -> "规则文本"
}
