package com.example.danmuapiapp.ui.screen.apitest

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

private const val DEFAULT_PREVIEW_LIMIT = 12_000
private const val DANMU_PREVIEW_LIMIT = 4_000
private const val PRETTY_PRINT_SAFE_LIMIT = 200_000
private val COMMENT_TEXT_KEYS = listOf("m", "text", "content", "body", "message", "msg", "danmu")
private val COMMENT_TIME_KEYS = listOf("time", "progress", "seconds", "position", "playTime", "timeline", "offset")
private val COMMENT_MODE_KEYS = listOf("mode", "type", "ct", "positionType")
private val COMMENT_COLOR_KEYS = listOf("color", "colorValue", "rgb")
private val DURATION_HINT_REGEX = Regex("(duration|video.*time|play.*time|length)", RegexOption.IGNORE_CASE)

internal fun prettyPrintJson(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return raw
    if (trimmed.length > PRETTY_PRINT_SAFE_LIMIT) return raw
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

internal fun buildTextPreview(
    raw: String,
    limit: Int = DEFAULT_PREVIEW_LIMIT
): TextPreview {
    val formatted = prettyPrintJson(raw)
    if (formatted.length <= limit) {
        return TextPreview(text = formatted, isTruncated = false)
    }
    val preview = formatted.take(limit).trimEnd() + "\n…已截断，避免超长内容导致界面卡顿。"
    return TextPreview(text = preview, isTruncated = true)
}

internal fun parseMatchSelection(raw: String): MatchSelection? {
    val root = parseJsonNode(raw) ?: return null
    val candidates = mutableListOf<MatchCandidate>()
    collectMatchCandidates(root, depth = 0, candidates = candidates)
    val best = candidates.maxWithOrNull(
        compareBy<MatchCandidate> { it.score }
            .thenBy { -it.depth }
            .thenBy { it.commentId }
    ) ?: return null
    return MatchSelection(
        commentId = best.commentId,
        animeTitle = best.animeTitle,
        episodeTitle = best.episodeTitle,
        source = best.source
    )
}

internal fun parseDanmuInsight(
    raw: String,
    commentId: Long?,
    animeTitle: String,
    episodeTitle: String,
    source: String,
    pathLabel: String,
    matchedAtMillis: Long,
    requestDurationMs: Long?
): DanmuInsight? {
    val root = parseJsonNode(raw) ?: return null
    val comments = extractCommentItems(root)
    val durationSeconds = extractDurationSeconds(root, comments)
    val heatBuckets = buildHeatBuckets(durationSeconds, comments)
    val highMoments = buildHighMoments(heatBuckets)
    val preview = buildTextPreview(raw, DANMU_PREVIEW_LIMIT)
    return DanmuInsight(
        commentId = commentId,
        animeTitle = animeTitle,
        episodeTitle = episodeTitle,
        source = source,
        pathLabel = pathLabel,
        matchedAtMillis = matchedAtMillis,
        totalCount = comments.size,
        durationSeconds = durationSeconds,
        maxHeatCount = heatBuckets.maxOfOrNull { it.count } ?: 0,
        requestDurationMs = requestDurationMs,
        rawPreview = preview.text,
        rawPreviewTruncated = preview.isTruncated,
        heatBuckets = heatBuckets,
        highMoments = highMoments,
        comments = comments
    )
}

private fun parseJsonNode(raw: String): Any? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { JSONTokener(trimmed).nextValue() }.getOrNull()
}

private fun extractCommentItems(root: Any): List<DanmuCommentItem> {
    val bestArray = findBestCommentArray(root) ?: return emptyList()
    val out = ArrayList<DanmuCommentItem>(bestArray.length())
    for (index in 0 until bestArray.length()) {
        val item = bestArray.opt(index)
        val parsed = when (item) {
            is JSONObject -> parseCommentItem(item, index)
            else -> null
        }
        if (parsed != null) {
            out += parsed
        }
    }
    return out.sortedBy { it.timeSeconds }
}

private fun findBestCommentArray(root: Any): JSONArray? {
    val candidates = mutableListOf<ArrayCandidate>()
    collectCommentArrays(root, depth = 0, candidates = candidates)
    return candidates.maxWithOrNull(
        compareBy<ArrayCandidate> { it.score }
            .thenBy { it.depth * -1 }
            .thenBy { it.length }
    )?.array
}

private fun collectCommentArrays(
    node: Any?,
    depth: Int,
    candidates: MutableList<ArrayCandidate>
) {
    when (node) {
        is JSONArray -> {
            val score = scoreCommentArray(node)
            if (score > 0) {
                candidates += ArrayCandidate(
                    array = node,
                    score = score,
                    depth = depth,
                    length = node.length()
                )
            }
            val sampleSize = minOf(node.length(), 8)
            for (index in 0 until sampleSize) {
                collectCommentArrays(node.opt(index), depth + 1, candidates)
            }
        }

        is JSONObject -> {
            val iterator = node.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                collectCommentArrays(node.opt(key), depth + 1, candidates)
            }
        }
    }
}

private fun scoreCommentArray(array: JSONArray): Int {
    if (array.length() <= 0) return 0
    val sampleSize = minOf(array.length(), 8)
    var parsedCount = 0
    var score = 0
    for (index in 0 until sampleSize) {
        val item = array.opt(index) as? JSONObject ?: continue
        if (item.optString("p").contains(',')) {
            score += 8
        }
        val text = readFirstString(item, COMMENT_TEXT_KEYS)
        if (text.isNotBlank()) {
            score += 5
        }
        val time = parseTimeSeconds(item)
        if (time != null && time >= 0.0) {
            score += 4
        }
        val mode = parseMode(item)
        if (mode > 0) {
            score += 2
        }
        if (text.isNotBlank() && time != null && time >= 0.0) {
            parsedCount += 1
        }
    }
    if (parsedCount == 0) return 0
    return score + parsedCount * 10 + minOf(array.length(), 60)
}

private fun parseCommentItem(
    obj: JSONObject,
    index: Int
): DanmuCommentItem? {
    val p = obj.optString("p").takeIf { it.contains(',') }
    val pParts = p?.split(',').orEmpty()
    val pMetadata = parseCommentPMetadata(pParts)
    val timeSeconds = pMetadata.timeSeconds
        ?: parseTimeSeconds(obj)
    if (timeSeconds == null || !timeSeconds.isFinite() || timeSeconds < 0.0) {
        return null
    }

    val mode = pMetadata.mode
        ?: parseMode(obj)
    val text = readFirstString(obj, COMMENT_TEXT_KEYS).ifBlank {
        pParts.getOrNull(8).orEmpty()
    }
    if (text.isBlank()) return null

    val fontSize = pMetadata.fontSize
        ?: readPositiveInt(obj, listOf("fontSize", "size"))
    val colorValue = pMetadata.colorValue
        ?: readColorValue(obj)
    val sentAtSeconds = pMetadata.sentAtSeconds
        ?: readPositiveLong(obj, listOf("timestamp", "ctime", "createdAt", "date"))
    val sourceLabel = pMetadata.sourceLabel.ifBlank {
        normalizeSourceLabel(
            readFirstString(obj, listOf("source", "type", "provider", "platform", "from"))
        )
    }
    val sourceId = pMetadata.sourceId.ifBlank {
        readFirstString(obj, listOf("id", "cid", "commentId", "danmuId"))
    }
    val uniqueId = sourceId.toLongOrNull()
        ?: readPositiveLong(obj, listOf("id", "cid", "commentId", "danmuId"))
        ?: (index + 1).toLong()
    val filter = when (mode) {
        4 -> DanmuCommentFilter.Bottom
        5 -> DanmuCommentFilter.Top
        else -> DanmuCommentFilter.Scroll
    }

    return DanmuCommentItem(
        uniqueId = uniqueId,
        timeSeconds = timeSeconds,
        mode = mode,
        filter = filter,
        text = text,
        colorValue = colorValue,
        colorHex = toColorHex(colorValue),
        fontSize = fontSize,
        sentAtSeconds = sentAtSeconds,
        sourceLabel = sourceLabel,
        sourceId = sourceId
    )
}

private fun parseCommentPMetadata(parts: List<String>): CommentPMetadata {
    if (parts.isEmpty()) return CommentPMetadata()
    val timeSeconds = parts.getOrNull(0)?.toDoubleOrNull()
    val mode = parts.getOrNull(1)?.toIntOrNull()

    // comment/json 当前主格式是 time,mode,color,[source]，
    // 标准 XML 才是 time,mode,size,color,timestamp,pool,hash,rowid。
    return when {
        parts.size >= 8 -> CommentPMetadata(
            timeSeconds = timeSeconds,
            mode = mode,
            fontSize = parts.getOrNull(2)?.toIntOrNull(),
            colorValue = parseColorValue(parts.getOrNull(3).orEmpty()),
            sentAtSeconds = parts.getOrNull(4)?.toLongOrNull(),
            sourceId = parts.getOrNull(7).orEmpty()
        )

        parts.size >= 4 -> CommentPMetadata(
            timeSeconds = timeSeconds,
            mode = mode,
            colorValue = parseColorValue(parts.getOrNull(2).orEmpty()),
            fontSize = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 8..72 },
            sourceLabel = parts.getOrNull(3)
                ?.takeIf { it.toIntOrNull()?.let { num -> num in 8..72 } != true }
                ?.let(::normalizeSourceLabel)
                .orEmpty()
        )

        else -> CommentPMetadata(
            timeSeconds = timeSeconds,
            mode = mode
        )
    }
}

private fun parseTimeSeconds(obj: JSONObject): Double? {
    val p = obj.optString("p")
    if (p.contains(',')) {
        p.split(',').firstOrNull()?.toDoubleOrNull()?.let { return it }
    }
    COMMENT_TIME_KEYS.forEach { key ->
        if (!obj.has(key)) return@forEach
        val rawNumber = obj.optDouble(key, Double.NaN)
        if (rawNumber.isFinite()) {
            return normalizeTimeValue(key, rawNumber)
        }
        val rawText = obj.optString(key).trim()
        rawText.toDoubleOrNull()?.let { return normalizeTimeValue(key, it) }
    }
    return null
}

private fun parseMode(obj: JSONObject): Int {
    val p = obj.optString("p")
    if (p.contains(',')) {
        p.split(',').getOrNull(1)?.toIntOrNull()?.let { return it }
    }
    COMMENT_MODE_KEYS.forEach { key ->
        if (!obj.has(key)) return@forEach
        val rawNumber = obj.optInt(key, Int.MIN_VALUE)
        if (rawNumber != Int.MIN_VALUE) {
            return normalizeModeValue(rawNumber)
        }
        val rawText = obj.optString(key).trim()
        when (rawText.lowercase()) {
            "scroll", "rolling", "marquee", "move" -> return 1
            "bottom", "fixed_bottom" -> return 4
            "top", "fixed_top" -> return 5
        }
        rawText.toIntOrNull()?.let { return normalizeModeValue(it) }
    }
    return 1
}

private fun normalizeModeValue(raw: Int): Int {
    return when (raw) {
        4, 5 -> raw
        else -> 1
    }
}

private fun normalizeTimeValue(
    key: String,
    value: Double
): Double {
    if (value <= 0.0) return 0.0
    return if (key.contains("ms", ignoreCase = true) || value > 30_000.0) {
        value / 1000.0
    } else {
        value
    }
}

private fun parseColorValue(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    if (text.startsWith("#")) {
        return text.removePrefix("#").toLongOrNull(16)
    }
    if (text.startsWith("0x", ignoreCase = true)) {
        return text.removePrefix("0x").toLongOrNull(16)
    }

    // 核心 comment/json 的颜色主格式是十进制字符串，必须优先按十进制读取。
    if (text.all { it.isDigit() }) {
        return text.toLongOrNull()
    }
    if (text.matches(Regex("[0-9A-Fa-f]{6,8}"))) {
        return text.toLongOrNull(16)
    }
    return text.toLongOrNull()
}

private fun readColorValue(obj: JSONObject): Long? {
    COMMENT_COLOR_KEYS.forEach { key ->
        if (!obj.has(key)) return@forEach
        when (val value = obj.opt(key)) {
            is Number -> value.toLong().takeIf { it > 0L }?.let { return it }
            is String -> parseColorValue(value)?.takeIf { it > 0L }?.let { return it }
            is JSONObject -> readColorValue(value)?.let { return it }
        }
    }
    obj.optJSONObject("style")?.let { nested ->
        readColorValue(nested)?.let { return it }
    }
    return null
}

private fun toColorHex(value: Long?): String {
    if (value == null || value <= 0L) return "#FFFFFF"
    return "#" + value.toString(16).uppercase().padStart(6, '0').takeLast(6)
}

private fun extractDurationSeconds(
    root: Any,
    comments: List<DanmuCommentItem>
): Double {
    val candidates = mutableListOf<Double>()
    collectDurationCandidates(root, depth = 0, candidates = candidates)
    val fromMeta = candidates.maxOrNull()
    val fromComments = if (comments.size >= 2) {
        val sorted = comments.map { it.timeSeconds }.sorted()
        val p99Index = ((sorted.size - 1) * 0.99).toInt().coerceIn(0, sorted.lastIndex)
        sorted[p99Index]
    } else {
        comments.maxOfOrNull { it.timeSeconds } ?: 0.0
    }
    return when {
        fromMeta != null && fromMeta > 1.0 -> max(fromMeta, fromComments)
        fromComments > 1.0 -> fromComments + 5.0
        else -> 0.0
    }
}

private fun collectDurationCandidates(
    node: Any?,
    depth: Int,
    candidates: MutableList<Double>
) {
    if (depth > 5) return
    when (node) {
        is JSONObject -> {
            val iterator = node.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (DURATION_HINT_REGEX.containsMatchIn(key)) {
                    readDurationValue(node, key)?.let { candidates += it }
                }
                collectDurationCandidates(node.opt(key), depth + 1, candidates)
            }
        }

        is JSONArray -> {
            val sampleSize = minOf(node.length(), 8)
            for (index in 0 until sampleSize) {
                collectDurationCandidates(node.opt(index), depth + 1, candidates)
            }
        }
    }
}

private fun readDurationValue(
    obj: JSONObject,
    key: String
): Double? {
    val rawNumber = obj.optDouble(key, Double.NaN)
    if (rawNumber.isFinite()) {
        return normalizeDurationCandidate(key, rawNumber)
    }
    val rawText = obj.optString(key).trim()
    return rawText.toDoubleOrNull()?.let { normalizeDurationCandidate(key, it) }
}

private fun normalizeDurationCandidate(
    key: String,
    value: Double
): Double? {
    if (!value.isFinite() || value <= 0.0) return null
    val seconds = if (key.contains("ms", ignoreCase = true) || value > 30_000.0) {
        value / 1000.0
    } else {
        value
    }
    return seconds.takeIf { it in 1.0..36_000.0 }
}

private fun buildHeatBuckets(
    durationSeconds: Double,
    comments: List<DanmuCommentItem>
): List<DanmuHeatBucket> {
    if (comments.isEmpty()) return emptyList()
    val safeDuration = max(durationSeconds, comments.maxOfOrNull { it.timeSeconds } ?: 0.0).coerceAtLeast(60.0)
    val bucketCount = ceil(safeDuration / 30.0).toInt().coerceIn(24, 72)
    val bucketWidth = safeDuration / bucketCount
    val counts = IntArray(bucketCount)
    comments.forEach { item ->
        val index = ((item.timeSeconds / bucketWidth).toInt()).coerceIn(0, bucketCount - 1)
        counts[index] += 1
    }
    return List(bucketCount) { index ->
        val start = bucketWidth * index
        DanmuHeatBucket(
            index = index,
            startSeconds = start,
            endSeconds = start + bucketWidth,
            count = counts[index]
        )
    }
}

private fun buildHighMoments(
    buckets: List<DanmuHeatBucket>
): List<DanmuHighMoment> {
    if (buckets.isEmpty()) return emptyList()
    val ranked = buckets.sortedByDescending { it.count }
    val selected = mutableListOf<DanmuHeatBucket>()
    for (bucket in ranked) {
        if (bucket.count <= 0) continue
        val hasNeighbor = selected.any { chosen -> abs(chosen.index - bucket.index) <= 1 }
        if (!hasNeighbor) {
            selected += bucket
        }
        if (selected.size >= 5) break
    }
    return selected.sortedBy { it.startSeconds }.map {
        DanmuHighMoment(
            startSeconds = it.startSeconds,
            endSeconds = it.endSeconds,
            count = it.count
        )
    }
}

private fun collectMatchCandidates(
    node: Any?,
    depth: Int,
    candidates: MutableList<MatchCandidate>
) {
    when (node) {
        is JSONObject -> {
            scoreMatchObject(node, depth)?.let { candidates += it }
            val iterator = node.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                collectMatchCandidates(node.opt(key), depth + 1, candidates)
            }
        }

        is JSONArray -> {
            val sampleSize = minOf(node.length(), 8)
            for (index in 0 until sampleSize) {
                collectMatchCandidates(node.opt(index), depth + 1, candidates)
            }
        }
    }
}

private fun scoreMatchObject(
    obj: JSONObject,
    depth: Int
): MatchCandidate? {
    val commentId = readPositiveLong(obj, listOf("commentId", "episodeId", "cid"))
        ?: run {
            val titleLike = readFirstString(obj, listOf("episodeTitle", "episodeName", "episode", "title", "name"))
            val fallbackId = readPositiveLong(obj, listOf("id"))
            if (fallbackId != null && titleLike.isNotBlank()) fallbackId else null
        }
        ?: return null

    val animeTitle = readFirstString(
        obj,
        listOf("animeTitle", "animeName", "anime", "bangumiTitle", "bangumi", "seriesTitle")
    )
    val episodeTitle = readFirstString(
        obj,
        listOf("episodeTitle", "episodeName", "episode", "title", "name")
    )
    val source = readFirstString(obj, listOf("source", "type", "provider", "platform"))
    var score = 0
    if (obj.has("commentId")) score += 12
    if (obj.has("episodeId")) score += 10
    if (obj.has("cid")) score += 8
    if (episodeTitle.isNotBlank()) score += 4
    if (animeTitle.isNotBlank()) score += 3
    if (source.isNotBlank()) score += 2
    score -= depth
    return MatchCandidate(
        commentId = commentId,
        animeTitle = animeTitle,
        episodeTitle = episodeTitle,
        source = source,
        score = score,
        depth = depth
    )
}

private fun readFirstString(
    obj: JSONObject,
    keys: List<String>
): String {
    keys.forEach { key ->
        if (!obj.has(key)) return@forEach
        val value = obj.opt(key)
        when (value) {
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isNotBlank() && !trimmed.equals("null", ignoreCase = true)) {
                    return trimmed
                }
            }

            is JSONObject -> {
                val nested = readFirstString(value, listOf("title", "name", "animeTitle", "episodeTitle"))
                if (nested.isNotBlank()) return nested
            }
        }
    }
    return ""
}

private fun readPositiveInt(
    obj: JSONObject,
    keys: List<String>
): Int? {
    keys.forEach { key ->
        if (!obj.has(key)) return@forEach
        val number = obj.optInt(key, Int.MIN_VALUE)
        if (number != Int.MIN_VALUE && number > 0) return number
        val text = obj.optString(key).trim()
        text.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
    }
    return null
}

private fun readPositiveLong(
    obj: JSONObject,
    keys: List<String>
): Long? {
    keys.forEach { key ->
        if (!obj.has(key)) return@forEach
        val number = obj.optLong(key, Long.MIN_VALUE)
        if (number != Long.MIN_VALUE && number > 0L) return number
        val text = obj.optString(key).trim()
        text.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
    }
    return null
}

private data class ArrayCandidate(
    val array: JSONArray,
    val score: Int,
    val depth: Int,
    val length: Int
)

private data class MatchCandidate(
    val commentId: Long,
    val animeTitle: String,
    val episodeTitle: String,
    val source: String,
    val score: Int,
    val depth: Int
)

private data class CommentPMetadata(
    val timeSeconds: Double? = null,
    val mode: Int? = null,
    val fontSize: Int? = null,
    val colorValue: Long? = null,
    val sentAtSeconds: Long? = null,
    val sourceLabel: String = "",
    val sourceId: String = ""
)

private fun normalizeSourceLabel(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return ""
    val unwrapped = text
        .removePrefix("【")
        .removeSuffix("】")
        .removePrefix("[")
        .removeSuffix("]")
        .trim()
    if (unwrapped.isBlank()) return ""
    if (unwrapped.all { it.isDigit() }) return ""
    return unwrapped
}
