package com.example.danmuapiapp.domain.model

import java.util.Locale

/**
 * Mirrors the stable core web UI log category rules so the Android log screen filters by
 * source/category instead of exposing every low-level bracket tag as a separate chip.
 */
data class LogTagInfo(
    val category: String = "",
    val tags: List<String> = emptyList()
)

object LogTagClassifier {

    private val leadingTagPrefixRegex = Regex("""^(?:\s*\[[^\]]+])+""")
    private val bracketTagRegex = Regex("""\[([^\]]+)]""")
    private val timestampTagRegex = Regex("""^\d{4}-\d{2}-\d{2}[T ].*""")
    private val timeTagRegex = Regex("""^\d{2}:\d{2}(?::\d{2})?$""")
    private val mergeHints = listOf("匹配", "落单", "补全", "合集", "略过", "Merge-Check")
    private val genericHeaderTags = setOf("core", "log", "logs")

    private val normalizationMap = mapOf(
        "vod fastest mode" to "vod",
        "custom source" to "custom",
        "bilibili-proxy" to "bilibili",
        "tmdb-source" to "tmdb",
        "path check" to "system",
        "path fix" to "system",
        "base" to "system",
        "fongmi" to "system"
    )

    private val categoryLabels = mapOf(
        "all" to "全部来源",
        "system" to "系统",
        "ai" to "AI",
        "utils" to "工具",
        "cache" to "缓存",
        "merge" to "合并",
        "360kan" to "360kan",
        "aiyifan" to "爱壹帆",
        "animeko" to "Animeko",
        "bahamut" to "巴哈姆特",
        "bilibili" to "哔哩哔哩",
        "custom" to "自定义源",
        "dandan" to "弹弹Play",
        "douban" to "豆瓣",
        "hanjutv" to "韩剧TV",
        "iqiyi" to "爱奇艺",
        "leshi" to "乐视",
        "maiduidui" to "埋堆堆",
        "mango" to "芒果TV",
        "migu" to "咪咕",
        "other" to "其他",
        "renren" to "人人",
        "sohu" to "搜狐",
        "tencent" to "腾讯",
        "tmdb" to "TMDB",
        "vod" to "VOD",
        "xigua" to "西瓜",
        "youku" to "优酷"
    )

    private val tagOrderMap: Map<String, Int> = listOf(
        listOf("system", "ai"),
        listOf("utils", "cache", "merge"),
        listOf(
            "360kan", "aiyifan", "animeko", "bahamut", "bilibili", "custom",
            "dandan", "douban", "hanjutv", "iqiyi", "leshi", "maiduidui", "mango",
            "migu", "other", "renren", "sohu", "tencent", "tmdb", "vod", "xigua", "youku"
        )
    ).flatMapIndexed { groupIndex, group ->
        group.mapIndexed { itemIndex, tag -> tag to (groupIndex * 1000 + itemIndex) }
    }.toMap()

    fun classifyCoreMessage(message: String, fallbackTag: String = "Core"): LogTagInfo {
        val leadingTags = extractLeadingTags(message)
        val fallbackTags = fallbackTag
            .takeIf { it.isNotBlank() && !isGenericHeaderTag(it) }
            ?.let { listOf(it) }
            .orEmpty()
        val rawTags = if (leadingTags.isNotEmpty()) leadingTags else fallbackTags

        val category = when {
            rawTags.any(::isMergeTag) -> "merge"
            else -> rawTags.firstOrNull { !isNoiseTag(it) }?.let(::normalizeTag)
        } ?: fallbackTags.firstOrNull()?.let(::normalizeTag) ?: "system"

        return LogTagInfo(
            category = category,
            tags = normalizeTags(category, rawTags)
        )
    }

    fun classifyAppEntry(source: AppLogSource, tag: String = ""): LogTagInfo {
        if (source != AppLogSource.Core) {
            return LogTagInfo()
        }

        return classifyCoreMessage("", tag)
    }

    fun extractLeadingTags(message: String): List<String> {
        val prefix = leadingTagPrefixRegex.find(message)?.value ?: return emptyList()
        return bracketTagRegex.findAll(prefix)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    fun normalizeTag(raw: String): String {
        val lowered = raw.trim().lowercase(Locale.ROOT)
        if (lowered.isBlank()) return ""
        return normalizationMap[lowered] ?: lowered
    }

    fun labelFor(tag: String): String {
        val normalized = normalizeTag(tag)
        return categoryLabels[normalized] ?: tag.trim().ifBlank { normalized }
    }

    fun sortTags(tags: Iterable<String>): List<String> {
        return tags.map(::normalizeTag)
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy<String> { tagOrderMap[it] ?: 99_999 }.thenBy { it })
    }

    fun sourceFilterFor(entry: LogEntry): String {
        if (entry.source == AppLogSource.Core) {
            val category = normalizeTag(entry.category)
            if (category.isNotBlank()) return category
        }
        return classifyLeadingMessageSource(entry.message)
    }

    fun matchesSource(entry: LogEntry, source: String): Boolean {
        val normalized = normalizeTag(source)
        if (normalized.isBlank()) return true
        return sourceFilterFor(entry) == normalized
    }

    private fun normalizeTags(category: String, rawTags: List<String>): List<String> {
        val out = linkedSetOf<String>()
        if (category.isNotBlank()) out += normalizeTag(category)
        rawTags.asSequence()
            .filterNot(::isNoiseTag)
            .map(::normalizeTag)
            .filter { it.isNotBlank() && !isGenericHeaderTag(it) }
            .forEach(out::add)
        return out.toList()
    }

    private fun classifyLeadingMessageSource(message: String): String {
        val rawTags = extractLeadingTags(message)
        if (rawTags.isEmpty()) return ""
        return when {
            rawTags.any(::isMergeTag) -> "merge"
            else -> rawTags.firstOrNull { !isNoiseTag(it) }?.let(::normalizeTag)
        }.orEmpty()
    }

    private fun isMergeTag(tag: String): Boolean {
        val normalized = normalizeTag(tag)
        return normalized == "merge" || mergeHints.any { hint -> tag.contains(hint, ignoreCase = true) }
    }

    private fun isNoiseTag(tag: String): Boolean {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return true
        return timestampTagRegex.matches(trimmed) ||
            timeTagRegex.matches(trimmed) ||
            trimmed.contains("08:00") ||
            trimmed == "请求模拟" ||
            trimmed == "网络请求"
    }

    private fun isGenericHeaderTag(tag: String): Boolean {
        return normalizeTag(tag) in genericHeaderTags
    }
}
