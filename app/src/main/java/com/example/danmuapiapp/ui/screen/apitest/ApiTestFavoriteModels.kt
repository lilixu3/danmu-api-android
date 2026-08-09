package com.example.danmuapiapp.ui.screen.apitest

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class FavoriteSupportState {
    Unknown,
    Loading,
    Supported,
    Unsupported,
    Failed
}

enum class FavoriteOperation {
    Add,
    Remove,
    Refresh,
    Schedule
}

enum class FavoriteScheduleFrequency(val value: String, val label: String) {
    Daily("daily", "每天"),
    Weekly("weekly", "每周");

    companion object {
        fun fromValue(raw: String?): FavoriteScheduleFrequency? {
            val value = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.value == value }
        }
    }
}

data class ApiTestFavoriteSchedule(
    val frequency: FavoriteScheduleFrequency,
    val time: String,
    val weekday: Int? = null,
    val timezone: String = "Asia/Shanghai",
    val nextRunAt: Long? = null,
    val retryAt: Long? = null,
    val lastRunAt: Long? = null,
    val lastStatus: String = "",
    val lastError: String = ""
)

data class FavoriteScheduleDraft(
    val frequency: FavoriteScheduleFrequency = FavoriteScheduleFrequency.Daily,
    val time: String = "03:00",
    val weekday: Int = 1
)

data class ApiTestFavoriteItem(
    val keyword: String,
    val animeTitle: String,
    val source: String,
    val sources: List<String>,
    val imageUrl: String,
    val episodeCount: Int,
    val resultsCount: Int,
    val timestamp: Long,
    val lastRefreshAt: Long,
    val refreshSchedule: ApiTestFavoriteSchedule? = null
)

data class ApiTestFavoriteList(
    val scheduledRefreshSupported: Boolean,
    val favorites: List<ApiTestFavoriteItem>
)

data class ApiTestUiNotice(
    val id: Long,
    val message: String,
    val isError: Boolean = false
)

internal fun parseFavoriteListResponse(raw: String): ApiTestFavoriteList {
    val root = JSONObject(raw)
    require(root.optBoolean("success", false)) {
        readFavoriteResponseMessage(root, "收藏列表加载失败")
    }
    val array = root.optJSONArray("favorites") ?: JSONArray()
    val favorites = buildList(array.length()) {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val keyword = item.cleanString("keyword")
            if (keyword.isBlank()) continue
            val sources = item.optJSONArray("sources").toStringList()
            val source = item.cleanString("source").ifBlank { sources.joinToString("、") }
            val timestamp = item.positiveLongOrNull("timestamp") ?: 0L
            add(
                ApiTestFavoriteItem(
                    keyword = keyword,
                    animeTitle = item.cleanString("animeTitle").ifBlank { keyword },
                    source = source,
                    sources = sources,
                    imageUrl = item.cleanString("imageUrl"),
                    episodeCount = item.optInt("episodeCount", 0).coerceAtLeast(0),
                    resultsCount = item.optInt("resultsCount", 0).coerceAtLeast(0),
                    timestamp = timestamp,
                    lastRefreshAt = item.positiveLongOrNull("lastRefreshAt") ?: timestamp,
                    refreshSchedule = parseFavoriteSchedule(item.optJSONObject("refreshSchedule"))
                )
            )
        }
    }
    return ApiTestFavoriteList(
        scheduledRefreshSupported = root.optBoolean("scheduledRefreshSupported", false),
        favorites = favorites.sortedByDescending { it.lastRefreshAt }
    )
}

internal fun parseFavoriteMutationMessage(raw: String, fallback: String): String {
    val root = JSONObject(raw)
    require(root.optBoolean("success", false)) {
        readFavoriteResponseMessage(root, fallback)
    }
    return readFavoriteResponseMessage(root, fallback)
}

internal fun favoriteErrorMessage(raw: String, fallback: String): String {
    val root = runCatching { JSONObject(raw) }.getOrNull()
    return if (root != null) readFavoriteResponseMessage(root, fallback) else fallback
}

internal fun findFavoriteForKeyword(
    favorites: List<ApiTestFavoriteItem>,
    keyword: String
): ApiTestFavoriteItem? {
    val query = keyword.normalizedFavoriteLookupTitle()
    if (query.isBlank()) return null
    return favorites.firstOrNull { item ->
        item.keyword.normalizedFavoriteLookupTitle() == query ||
            item.animeTitle.normalizedFavoriteLookupTitle() == query
    }
}

internal fun buildFavoriteKeywordBody(keyword: String): String {
    return JSONObject().put("keyword", keyword.trim()).toString()
}

internal fun buildFavoriteScheduleBody(
    keyword: String,
    schedule: FavoriteScheduleDraft?
): String {
    val root = JSONObject().put("keyword", keyword.trim())
    if (schedule == null) {
        root.put("schedule", JSONObject.NULL)
    } else {
        val normalizedTime = normalizeFavoriteTime(schedule.time)
            ?: throw IllegalArgumentException("刷新时间必须为有效的 HH:mm")
        val value = JSONObject()
            .put("frequency", schedule.frequency.value)
            .put("time", normalizedTime)
        if (schedule.frequency == FavoriteScheduleFrequency.Weekly) {
            require(schedule.weekday in 1..7) { "每周刷新日期必须为周一到周日" }
            value.put("weekday", schedule.weekday)
        }
        root.put("schedule", value)
    }
    return root.toString()
}

internal fun normalizeFavoriteTime(raw: String): String? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(raw.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return "%02d:%02d".format(Locale.ROOT, hour, minute)
}

private fun parseFavoriteSchedule(value: JSONObject?): ApiTestFavoriteSchedule? {
    if (value == null) return null
    val frequency = FavoriteScheduleFrequency.fromValue(value.cleanString("frequency")) ?: return null
    val time = normalizeFavoriteTime(value.cleanString("time")) ?: return null
    val weekday = value.optInt("weekday", 0).takeIf { it in 1..7 }
    if (frequency == FavoriteScheduleFrequency.Weekly && weekday == null) return null
    return ApiTestFavoriteSchedule(
        frequency = frequency,
        time = time,
        weekday = weekday,
        timezone = value.cleanString("timezone").ifBlank { "Asia/Shanghai" },
        nextRunAt = value.positiveLongOrNull("nextRunAt"),
        retryAt = value.positiveLongOrNull("retryAt"),
        lastRunAt = value.positiveLongOrNull("lastRunAt"),
        lastStatus = value.cleanString("lastStatus"),
        lastError = value.cleanString("lastError")
    )
}

private fun readFavoriteResponseMessage(root: JSONObject, fallback: String): String {
    return root.cleanString("message")
        .ifBlank { root.cleanString("errorMessage") }
        .ifBlank { fallback }
}

private fun JSONObject.cleanString(key: String): String {
    val value = opt(key)
    return if (value == null || value === JSONObject.NULL) "" else value.toString().trim()
}

private fun JSONObject.positiveLongOrNull(key: String): Long? {
    val value = opt(key)
    val number = when (value) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }
    return number?.takeIf { it > 0L }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            val value = opt(index)
            if (value != null && value !== JSONObject.NULL) {
                value.toString().trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.distinct()
}

private fun String.normalizedFavoriteLookupTitle(): String {
    return trim()
        .replace(Regex("_S\\d+$", RegexOption.IGNORE_CASE), "")
        .trim()
        .lowercase(Locale.ROOT)
}
