package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.EnvType
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun parseCookieSnapshot(rawCookie: String): CookieSnapshot {
    val map = LinkedHashMap<String, String>()
    rawCookie.split(';').forEach { segment ->
        val part = segment.trim()
        val idx = part.indexOf('=')
        if (idx > 0) {
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                map[key] = value
            }
        }
    }
    val hasSessdata = map.keys.any { it.equals("SESSDATA", true) }
    val hasBiliJct = map.keys.any { it.equals("bili_jct", true) }
    return CookieSnapshot(
        keys = map.keys.toList(),
        hasRequired = hasSessdata && hasBiliJct
    )
}

internal fun buildCookieWithRefreshToken(cookie: String?, refreshToken: String?): String {
    val base = cookie?.trim().orEmpty()
    if (base.isBlank()) return ""
    val refresh = refreshToken?.trim().orEmpty()
    if (refresh.isBlank()) return base
    if (base.contains("refresh_token=")) return base
    return if (base.endsWith(";")) "$base refresh_token=$refresh" else "$base; refresh_token=$refresh"
}

internal fun inferCookieExpiryMs(cookie: String): Long? {
    if (cookie.isBlank()) return null

    val sessRaw = Regex("(^|;\\s*)SESSDATA=([^;]+)", RegexOption.IGNORE_CASE)
        .find(cookie)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()

    if (!sessRaw.isNullOrBlank()) {
        val decoded = runCatching { URLDecoder.decode(sessRaw, "UTF-8") }.getOrElse { sessRaw }
        val parts = decoded.split(',')
        if (parts.size >= 2) {
            val ts = parts[1].trim().toLongOrNull()
            val ms = ts?.let {
                when {
                    it < 10_000_000_000L -> it * 1000L
                    it > 10_000_000_000_000L -> it / 1000L
                    else -> it
                }
            }
            if (ms != null && ms > 0L) return ms
        }
    }

    val expires = Regex("(?i)Expires=([^;]+)")
        .find(cookie)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!expires.isNullOrBlank()) {
        return runCatching {
            val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            parser.parse(expires)?.time
        }.getOrNull()
    }
    return null
}

internal fun formatEpochMs(epochMs: Long?): String {
    val value = epochMs ?: return "未知"
    if (value <= 0L) return "未知"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateText = runCatching { sdf.format(Date(value)) }.getOrElse { "未知" }
    val delta = value - System.currentTimeMillis()
    if (delta <= 0L) return "$dateText（已过期）"

    val days = delta / (24 * 60 * 60 * 1000L)
    val hours = (delta / (60 * 60 * 1000L)) % 24
    val tail = when {
        days >= 1 -> "（约 ${days} 天 ${hours} 小时后）"
        hours >= 1 -> "（约 ${hours} 小时后）"
        else -> "（不到 1 小时）"
    }
    return dateText + tail
}

internal fun formatLatencyMs(latencyMs: Long?): String {
    val value = latencyMs ?: return "--"
    if (value <= 0L) return "--"
    return if (value < 1000L) {
        "${value}ms"
    } else {
        String.format(Locale.getDefault(), "%.2fs", value / 1000f)
    }
}

internal fun buildQrBitmap(content: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}

internal fun parseCsvTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    raw.split(',').forEach { token ->
        val t = token.trim()
        if (t.isNotBlank() && seen.add(t)) {
            out += t
        }
    }
    return out
}

internal fun parseTitleMappingRows(raw: String): List<TitleMappingRow> {
    return raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { segment ->
            val idx = segment.indexOf("->")
            if (idx < 0) return@mapNotNull null
            val left = segment.substring(0, idx).trim()
            val right = segment.substring(idx + 2).trim()
            if (left.isBlank() && right.isBlank()) null else TitleMappingRow(left, right)
        }
}

internal fun serializeTitleMappingRows(rows: List<TitleMappingRow>): String {
    return rows.mapNotNull { row ->
        val left = row.left.trim()
        val right = row.right.trim()
        if (left.isBlank() || right.isBlank()) null else "$left->$right"
    }.joinToString(";")
}

internal fun parseTitleOffsetRows(raw: String): List<TitleOffsetRow> {
    return raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { segment ->
            val parts = segment.split('@').map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            val offset = parts.last()
            val platforms = parts[parts.size - 2]
            val title = parts.dropLast(2).joinToString("@").trim()
            if (title.isBlank() && platforms.isBlank() && offset.isBlank()) null
            else TitleOffsetRow(title = title, platformsRaw = platforms, offset = offset)
        }
}

internal fun serializeTitleOffsetRows(rows: List<TitleOffsetRow>): String {
    return rows.mapNotNull { row ->
        val title = row.title.trim()
        val platforms = normalizePlatforms(row.platformsRaw)
        val offset = row.offset.trim()
        if (title.isBlank() || platforms.isBlank() || offset.isBlank()) null
        else "$title@$platforms@$offset"
    }.joinToString(";")
}

internal fun parsePlatformTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    raw.split(Regex("[&,]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val normalized = if (token == "*") "all" else token
            val key = normalized.lowercase(Locale.getDefault())
            if (seen.add(key)) out += normalized
        }
    return out
}

internal fun normalizePlatforms(raw: String): String {
    val tokens = parsePlatformTokens(raw)
    if (tokens.isEmpty()) return ""
    return if (tokens.any { it.equals("all", true) }) "all" else tokens.joinToString("&")
}

internal fun normalizePlatformOptions(options: List<String>): List<String> {
    val normalized = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    options.map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { option ->
            val normalizedOption = if (option == "*") "all" else option
            val key = normalizedOption.lowercase(Locale.getDefault())
            if (seen.add(key)) normalized += normalizedOption
        }

    val withAll = if (normalized.any { it.equals("all", true) }) normalized else listOf("all") + normalized
    val all = withAll.firstOrNull { it.equals("all", true) } ?: "all"
    val others = withAll.filterNot { it.equals("all", true) }
    return listOf(all) + others
}

internal fun canonicalizePlatformSelection(selected: List<String>, options: List<String>): List<String> {
    if (selected.isEmpty()) return emptyList()
    if (selected.any { it.equals("all", true) || it == "*" }) return listOf("all")

    val optionOrder = options.filterNot { it.equals("all", true) }
    val selectedMap = linkedMapOf<String, String>()
    selected.forEach { token ->
        val normalized = token.trim()
        if (normalized.isBlank() || normalized.equals("all", true)) return@forEach
        val key = normalized.lowercase(Locale.getDefault())
        if (!selectedMap.containsKey(key)) selectedMap[key] = normalized
    }

    val ordered = mutableListOf<String>()
    optionOrder.forEach { option ->
        val key = option.lowercase(Locale.getDefault())
        if (selectedMap.containsKey(key)) {
            ordered += option
            selectedMap.remove(key)
        }
    }
    ordered += selectedMap.values
    return ordered
}

internal fun togglePlatformSelection(current: List<String>, option: String, options: List<String>): List<String> {
    val normalizedOption = if (option == "*") "all" else option
    val normalizedCurrent = canonicalizePlatformSelection(current, options)

    if (normalizedOption.equals("all", true)) {
        return if (normalizedCurrent.any { it.equals("all", true) }) {
            emptyList()
        } else {
            listOf("all")
        }
    }

    val next = normalizedCurrent.filterNot { it.equals("all", true) }.toMutableList()
    val index = next.indexOfFirst { it.equals(normalizedOption, true) }
    if (index >= 0) {
        next.removeAt(index)
    } else {
        next += normalizedOption
    }
    return canonicalizePlatformSelection(next, options)
}

internal fun formatPlatformSummary(platforms: List<String>): String {
    if (platforms.isEmpty()) return "未选择"
    if (platforms.any { it.equals("all", true) }) return "全部"
    return platforms.joinToString("、")
}

internal fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val list = toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    return list
}

internal fun <T> List<T>.replace(index: Int, value: T): List<T> {
    if (index !in indices) return this
    val list = toMutableList()
    list[index] = value
    return list
}

internal fun String?.ifNullOrBlank(fallback: String): String {
    val value = this?.trim().orEmpty()
    return if (value.isBlank()) fallback else value
}

internal data class CustomMergeRuleRow(
    val action: String = "merge",
    val secondaryTitle: String = "",
    val secondarySeason: String = "",
    val secondarySource: String = "",
    val primaryTitle: String = "",
    val primarySeason: String = "",
    val primarySource: String = "",
    val routesRaw: String = "",
)

internal data class TimelineOffsetRuleRow(
    val title: String = "",
    val season: String = "",
    val episode: String = "",
    val selectedSources: List<String> = listOf("all"),
    val offset: String = "",
    val usePercent: Boolean = false,
)

internal data class MatchPlatformRuleRow(
    val title: String = "",
    val season: String = "",
    val platformsRaw: String = "",
)

internal fun cleanCacheAnimeTitle(rawTitle: String): String {
    return rawTitle.orEmpty()
        .replace(Regex("[\\u200B-\\u200F\\uFEFF]"), "")
        .replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[（(〔\\[]\\s*[0-9０-９]{4}\\s*年?\\s*[）)〕\\]]"), "")
        .replace(Regex("(.+?)\\s*【[^】]+】$"), "\$1")
        .trim()
}

internal fun normalizeNumericInput(raw: String?): String {
    val digits = raw?.trim().orEmpty().filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return digits.toIntOrNull()?.toString() ?: digits
}

internal fun formatIndexedSegment(prefix: String, raw: String?): String {
    val numeric = normalizeNumericInput(raw)
    if (numeric.isBlank()) return ""
    val padded = numeric.toIntOrNull()?.toString()?.padStart(2, '0') ?: numeric
    return prefix.uppercase(Locale.getDefault()) + padded
}

internal fun parseCustomMergeRules(raw: String): List<CustomMergeRuleRow> {
    return raw.split(Regex("[;\\n]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { ruleText ->
            val parts = ruleText.split("|", limit = 2).map { it.trim() }
            val entityPart = parts[0]
            val routesRaw = parts.getOrNull(1).orEmpty()
            val action = when {
                entityPart.contains("->") -> "merge"
                entityPart.contains("×") -> "block"
                else -> return@mapNotNull null
            }
            val entityParts = if (action == "merge") entityPart.split("->", limit = 2) else entityPart.split("×", limit = 2)
            if (entityParts.size < 2) return@mapNotNull null
            val secondary = parseCustomMergeEntity(entityParts[0].trim()) ?: return@mapNotNull null
            val primary = parseCustomMergeEntity(entityParts[1].trim()) ?: return@mapNotNull null
            CustomMergeRuleRow(
                action = action,
                secondaryTitle = secondary.title,
                secondarySeason = secondary.season,
                secondarySource = secondary.source,
                primaryTitle = primary.title,
                primarySeason = primary.season,
                primarySource = primary.source,
                routesRaw = if (action == "merge") routesRaw else ""
            )
        }
}

internal fun serializeCustomMergeRules(rows: List<CustomMergeRuleRow>): String {
    return rows.mapNotNull { row ->
        val secondary = formatCustomMergeEntity(row.secondaryTitle, row.secondarySeason, row.secondarySource)
        val primary = formatCustomMergeEntity(row.primaryTitle, row.primarySeason, row.primarySource)
        if (secondary.isBlank() || primary.isBlank()) return@mapNotNull null
        val entity = if (row.action == "block") "$secondary × $primary" else "$secondary -> $primary"
        val routes = row.routesRaw.trim()
        if (row.action == "merge" && routes.isNotBlank()) "$entity | $routes" else entity
    }.joinToString("; ")
}

internal fun parseTimelineOffsetRules(raw: String): List<TimelineOffsetRuleRow> {
    return raw.split(Regex("[,;\\n]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val colonIdx = line.lastIndexOf(':')
            if (colonIdx == -1) {
                return@mapNotNull TimelineOffsetRuleRow(title = line)
            }
            var rawPath = line.substring(0, colonIdx).trim()
            val offset = line.substring(colonIdx + 1).trim()
            var usePercent = false

            if (rawPath.endsWith('%')) {
                usePercent = true
                rawPath = rawPath.dropLast(1).trim()
            }

            val atIdx = rawPath.lastIndexOf('@')
            val pathPart: String
            val sourcePart: String?
            if (atIdx == -1) {
                pathPart = rawPath
                sourcePart = null
            } else {
                pathPart = rawPath.substring(0, atIdx).trim()
                sourcePart = rawPath.substring(atIdx + 1).trim()
            }

            val segments = pathPart.split('/').map { it.trim() }.filter { it.isNotBlank() }
            val title = segments.getOrNull(0).orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val season = normalizeNumericInput(segments.getOrNull(1))
            val episode = normalizeNumericInput(segments.getOrNull(2))
            val selectedSources = when {
                sourcePart.isNullOrBlank() -> listOf("all")
                sourcePart.equals("all", true) || sourcePart == "*" -> listOf("all")
                else -> sourcePart.split('&').map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { listOf("all") }
            }

            TimelineOffsetRuleRow(
                title = title,
                season = season,
                episode = episode,
                selectedSources = selectedSources,
                offset = offset,
                usePercent = usePercent
            )
        }
}

internal fun serializeTimelineOffsetRules(rows: List<TimelineOffsetRuleRow>): String {
    return rows.mapNotNull { row ->
        val title = row.title.trim()
        val offset = row.offset.trim()
        if (title.isBlank() || offset.isBlank()) return@mapNotNull null
        val pathParts = buildList {
            add(title)
            formatIndexedSegment("S", row.season).takeIf { it.isNotBlank() }?.let { add(it) }
            formatIndexedSegment("E", row.episode).takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val sources = normalizeTimelineOffsetSources(row.selectedSources)
        val scopedPath = pathParts.joinToString("/") + "@" + sources
        scopedPath + if (row.usePercent) "%" else "" + ":" + offset
    }.joinToString(",")
}

internal fun parseMatchPlatformRules(raw: String): List<MatchPlatformRuleRow> {
    return raw.split(Regex("[;\\n]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val arrowIdx = entry.indexOf("->")
            if (arrowIdx == -1) return@mapNotNull null
            val keyPart = entry.substring(0, arrowIdx).trim()
            val platformsRaw = entry.substring(arrowIdx + 2).trim()
            val (title, season) = parseMatchPlatformRuleKey(keyPart) ?: return@mapNotNull null
            if (platformsRaw.isBlank()) return@mapNotNull null
            MatchPlatformRuleRow(
                title = title,
                season = season,
                platformsRaw = platformsRaw
            )
        }
}

internal fun serializeMatchPlatformRules(rows: List<MatchPlatformRuleRow>): String {
    return rows.mapNotNull { row ->
        val title = row.title.trim()
        if (title.isBlank()) return@mapNotNull null
        val seasonPart = formatIndexedSegment("S", row.season)
        val key = if (seasonPart.isBlank()) title else "$title/$seasonPart"
        val platforms = parseCsvTokens(row.platformsRaw).joinToString(",")
        if (platforms.isBlank()) return@mapNotNull null
        "$key->$platforms"
    }.joinToString(";")
}

private data class ParsedCustomMergeEntity(
    val title: String,
    val season: String,
    val source: String,
)

private fun parseCustomMergeEntity(raw: String): ParsedCustomMergeEntity? {
    val match = Regex("^(.+?)(?:/S(\\d+))?@([a-zA-Z0-9_&]+)$", RegexOption.IGNORE_CASE)
        .find(raw.trim()) ?: return null
    val title = match.groupValues.getOrNull(1)?.trim().orEmpty()
    val season = normalizeNumericInput(match.groupValues.getOrNull(2))
    val source = match.groupValues.getOrNull(3)?.trim().orEmpty().lowercase(Locale.getDefault())
    if (title.isBlank() || source.isBlank()) return null
    return ParsedCustomMergeEntity(title = title, season = season, source = source)
}

private fun formatCustomMergeEntity(title: String, season: String?, source: String): String {
    val cleanTitle = title.trim()
    val cleanSource = source.trim().lowercase(Locale.getDefault())
    if (cleanTitle.isBlank() || cleanSource.isBlank()) return ""
    val seasonPart = formatIndexedSegment("S", season)
    return buildString {
        append(cleanTitle)
        if (seasonPart.isNotBlank()) append('/').append(seasonPart)
        append('@').append(cleanSource)
    }
}

private fun parseMatchPlatformRuleKey(rawKey: String): Pair<String, String>? {
    val normalized = rawKey.trim()
    if (normalized.isBlank()) return null
    val slashIdx = normalized.lastIndexOf('/')
    if (slashIdx == -1) return normalized to ""
    val seasonPart = normalized.substring(slashIdx + 1).trim()
    val seasonMatch = Regex("^S?(\\d+)$", RegexOption.IGNORE_CASE).find(seasonPart) ?: return normalized to ""
    val title = normalized.substring(0, slashIdx).trim()
    val season = normalizeNumericInput(seasonMatch.groupValues.getOrNull(1))
    if (title.isBlank()) return null
    return title to season
}

private fun normalizeTimelineOffsetSources(selectedSources: List<String>): String {
    val tokens = selectedSources.map { it.trim() }.filter { it.isNotBlank() }
    if (tokens.isEmpty() || tokens.any { it.equals("all", true) || it == "*" }) return "all"
    return tokens.distinctBy { it.lowercase(Locale.getDefault()) }.joinToString("&")
}
