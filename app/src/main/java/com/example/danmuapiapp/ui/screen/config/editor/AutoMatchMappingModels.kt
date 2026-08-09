package com.example.danmuapiapp.ui.screen.config

import java.text.Normalizer
import java.util.Locale

internal data class AutoMatchMappingDraft(
    val sourceTitle: String = "",
    val sourceSeason: String = "1",
    val sourceStartEpisode: String = "1",
    val sourceEndEpisode: String = "",
    val targetTitle: String = "",
    val targetSeason: String = "1",
    val targetStartEpisode: String = "1",
    val targetEndEpisode: String = "",
    val targetPlatform: String = ""
) {
    val isEmpty: Boolean
        get() = sourceTitle.isBlank() && targetTitle.isBlank()
}

internal data class AutoMatchMappingValidation(
    val valid: Boolean,
    val message: String = ""
)

internal data class AutoMatchMappingPreview(
    val sourceLabel: String,
    val targetLabel: String
)

private data class ParsedAutoMatchSide(
    val title: String,
    val season: Int,
    val startEpisode: Int,
    val endEpisode: Int?,
    val platform: String = ""
)

private val AUTO_MATCH_SIDE_REGEX =
    Regex("^(.+?)\\s+S(\\d+)E(\\d+)(?:~E?(\\d+))?\\s*$", RegexOption.IGNORE_CASE)
private val AUTO_MATCH_PLATFORM_REGEX = Regex("\\s+@([a-zA-Z0-9_-]+)\\s*$")
private val AUTO_MATCH_FILENAME_REGEX =
    Regex("(?i)(?:^|[\\s._\\-\\[\\]()])S(\\d+)E(\\d+)")

internal fun parseAutoMatchMappingDrafts(raw: String): List<AutoMatchMappingDraft> {
    return raw.split(';').mapNotNull { part ->
        val rule = part.trim()
        if (rule.isBlank()) return@mapNotNull null
        val arrow = rule.indexOf("->")
        if (arrow < 0 || rule.indexOf("->", arrow + 2) >= 0) {
            return@mapNotNull AutoMatchMappingDraft(sourceTitle = rule)
        }
        val sourceRaw = rule.substring(0, arrow).trim()
        val targetRaw = rule.substring(arrow + 2).trim()
        val source = parseAutoMatchSide(sourceRaw, allowPlatform = false)
        val target = parseAutoMatchSide(targetRaw, allowPlatform = true)
        AutoMatchMappingDraft(
            sourceTitle = source?.title ?: sourceRaw,
            sourceSeason = source?.season?.toString() ?: "",
            sourceStartEpisode = source?.startEpisode?.toString() ?: "",
            sourceEndEpisode = source?.endEpisode?.toString().orEmpty(),
            targetTitle = target?.title ?: targetRaw,
            targetSeason = target?.season?.toString() ?: "",
            targetStartEpisode = target?.startEpisode?.toString() ?: "",
            targetEndEpisode = target?.endEpisode?.toString().orEmpty(),
            targetPlatform = target?.platform.orEmpty()
        )
    }
}

internal fun serializeAutoMatchMappingDrafts(rows: List<AutoMatchMappingDraft>): String {
    return rows.filterNot(AutoMatchMappingDraft::isEmpty).joinToString(";") { row ->
        val source = serializeAutoMatchSide(
            title = row.sourceTitle,
            season = row.sourceSeason,
            startEpisode = row.sourceStartEpisode,
            endEpisode = row.sourceEndEpisode
        )
        val target = serializeAutoMatchSide(
            title = row.targetTitle,
            season = row.targetSeason,
            startEpisode = row.targetStartEpisode,
            endEpisode = row.targetEndEpisode
        ) + row.targetPlatform.trim().lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
            ?.let { " @$it" }.orEmpty()
        "$source->$target"
    }
}

internal fun validateAutoMatchMappingTable(
    raw: String,
    allowedPlatforms: List<String> = emptyList()
): AutoMatchMappingValidation {
    if (raw.isBlank()) return AutoMatchMappingValidation(valid = true)
    val rules = raw.split(';').filter { it.isNotBlank() }
    if (rules.isEmpty()) return AutoMatchMappingValidation(valid = true)

    rules.forEachIndexed { index, text ->
        val arrow = text.indexOf("->")
        if (arrow < 0 || text.indexOf("->", arrow + 2) >= 0) {
            return invalidRule(index, "需要且只能包含一个 ->")
        }
        val source = parseAutoMatchSide(text.substring(0, arrow), allowPlatform = false)
            ?: return invalidRule(index, "来源季集格式无效")
        val target = parseAutoMatchSide(text.substring(arrow + 2), allowPlatform = true)
            ?: return invalidRule(index, "目标季集格式无效")
        val sourceBounded = source.endEpisode != null
        val targetBounded = target.endEpisode != null
        if (sourceBounded != targetBounded) {
            return invalidRule(index, "来源和目标必须同时设置范围")
        }
        if (
            source.endEpisode != null && target.endEpisode != null &&
            source.endEpisode - source.startEpisode != target.endEpisode - target.startEpisode
        ) {
            return invalidRule(index, "来源和目标范围长度不一致")
        }
        val allowed = allowedPlatforms.map { it.lowercase(Locale.ROOT) }.toSet()
        if (target.platform.isNotBlank() && allowed.isNotEmpty() && target.platform !in allowed) {
            return invalidRule(index, "平台 ${target.platform} 不受当前核心支持")
        }
        if (stripTargetMetadata(target.title).isBlank()) {
            return invalidRule(index, "目标标题不能为空")
        }
    }
    return AutoMatchMappingValidation(valid = true, message = "${rules.size} 条规则有效")
}

internal fun previewAutoMatchMapping(
    raw: String,
    fileName: String,
    allowedPlatforms: List<String> = emptyList()
): AutoMatchMappingPreview? {
    if (!validateAutoMatchMappingTable(raw, allowedPlatforms).valid) return null
    val input = parsePreviewFileName(fileName) ?: return null
    val candidates = raw.split(';').mapIndexedNotNull { index, text ->
        val arrow = text.indexOf("->")
        if (arrow < 0) return@mapIndexedNotNull null
        val source = parseAutoMatchSide(text.substring(0, arrow), allowPlatform = false)
            ?: return@mapIndexedNotNull null
        val target = parseAutoMatchSide(text.substring(arrow + 2), allowPlatform = true)
            ?: return@mapIndexedNotNull null
        if (normalizeAutoMatchTitle(source.title) != normalizeAutoMatchTitle(input.title)) {
            return@mapIndexedNotNull null
        }
        if (source.season != input.season || input.startEpisode < source.startEpisode) {
            return@mapIndexedNotNull null
        }
        if (source.endEpisode != null && input.startEpisode > source.endEpisode) {
            return@mapIndexedNotNull null
        }
        Triple(index, source, target)
    }.sortedWith(compareByDescending<Triple<Int, ParsedAutoMatchSide, ParsedAutoMatchSide>> {
        it.second.endEpisode != null
    }.thenBy { it.first })

    val (_, source, target) = candidates.firstOrNull() ?: return null
    val targetEpisode = target.startEpisode + input.startEpisode - source.startEpisode
    return AutoMatchMappingPreview(
        sourceLabel = formatAutoMatchEpisode(input.title, input.season, input.startEpisode),
        targetLabel = formatAutoMatchEpisode(target.title, target.season, targetEpisode) +
            target.platform.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
    )
}

internal fun validateAutoMatchDraft(
    draft: AutoMatchMappingDraft,
    allowedPlatforms: List<String> = emptyList()
): AutoMatchMappingValidation {
    if (draft.isEmpty) return AutoMatchMappingValidation(valid = true)
    return validateAutoMatchMappingTable(
        serializeAutoMatchMappingDrafts(listOf(draft)),
        allowedPlatforms
    )
}

private fun parseAutoMatchSide(raw: String, allowPlatform: Boolean): ParsedAutoMatchSide? {
    var text = raw.trim()
    var platform = ""
    if (allowPlatform) {
        val match = AUTO_MATCH_PLATFORM_REGEX.find(text)
        if (match != null && match.range.last == text.lastIndex) {
            platform = match.groupValues[1].lowercase(Locale.ROOT)
            text = text.substring(0, match.range.first).trim()
        }
    }
    val match = AUTO_MATCH_SIDE_REGEX.matchEntire(text) ?: return null
    val title = match.groupValues[1].trim()
    val season = match.groupValues[2].toIntOrNull() ?: return null
    val start = match.groupValues[3].toIntOrNull() ?: return null
    val end = match.groupValues[4].takeIf { it.isNotBlank() }?.toIntOrNull()
    if (title.isBlank() || season < 1 || start < 1 || (end != null && end < start)) return null
    return ParsedAutoMatchSide(title, season, start, end, platform)
}

private fun parsePreviewFileName(raw: String): ParsedAutoMatchSide? {
    val leaf = raw.trim().substringAfterLast('/').substringAfterLast('\\')
    val match = AUTO_MATCH_FILENAME_REGEX.find(leaf) ?: return null
    val season = match.groupValues[1].toIntOrNull() ?: return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    val title = leaf.substring(0, match.range.first)
        .replace(Regex("[._]+"), " ")
        .trim(' ', '-', '[', '(', '_', '.')
    if (title.isBlank() || season < 1 || episode < 1) return null
    return ParsedAutoMatchSide(title, season, episode, null)
}

private fun serializeAutoMatchSide(
    title: String,
    season: String,
    startEpisode: String,
    endEpisode: String
): String {
    val seasonText = season.toIntOrNull()?.takeIf { it > 0 }?.toString()?.padStart(2, '0') ?: season.trim()
    val startText = startEpisode.toIntOrNull()?.takeIf { it > 0 }?.toString()?.padStart(2, '0')
        ?: startEpisode.trim()
    val endText = endEpisode.toIntOrNull()?.takeIf { it > 0 }?.toString()?.padStart(2, '0')
        ?: endEpisode.trim()
    return buildString {
        append(title.trim())
        append(" S")
        append(seasonText)
        append('E')
        append(startText)
        if (endEpisode.isNotBlank()) {
            append('~')
            append(endText)
        }
    }
}

private fun invalidRule(index: Int, message: String): AutoMatchMappingValidation {
    return AutoMatchMappingValidation(valid = false, message = "规则 ${index + 1}：$message")
}

private fun normalizeAutoMatchTitle(raw: String): String {
    return Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .replace(Regex("[^\\p{L}\\p{N}]"), "")
        .lowercase(Locale.ROOT)
}

private fun stripTargetMetadata(raw: String): String {
    return raw
        .replace(Regex("[（(](?:19|20)\\d{2}[)）]"), "")
        .replace(Regex("【[^】]+】"), "")
        .trim()
}

private fun formatAutoMatchEpisode(title: String, season: Int, episode: Int): String {
    return "%s S%02dE%02d".format(Locale.ROOT, title.trim(), season, episode)
}
