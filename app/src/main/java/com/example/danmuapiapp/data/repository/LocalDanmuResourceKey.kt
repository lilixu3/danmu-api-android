package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuType
import java.text.Normalizer
import java.util.Locale

/**
 * Mirrors the core's local-danmu resource-key normalization closely enough to preview
 * conflicts before an upload. The core remains the source of truth after a request.
 */
internal object LocalDanmuResourceKey {

    private val trailingExtensionRegex = Regex("\\.[^.\\n]+$")
    private val invalidKeyCharsRegex = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]+")
    private val whitespaceRegex = Regex("\\s+")

    fun normalizeKey(value: String?): String {
        val raw = value.orEmpty()
        val withoutExtension = trailingExtensionRegex.replace(raw, "")
        return Normalizer.normalize(withoutExtension, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(invalidKeyCharsRegex, " ")
            .replace(whitespaceRegex, " ")
            .take(180)
    }

    fun normalizeType(value: String?): String {
        val raw = normalizeKey(value)
        if (raw.isEmpty()) return ""
        return when (raw) {
            "movie", "film", "电影", "劇場版", "剧场版" -> LocalDanmuType.Movie.wire
            "tv", "电视剧", "電視劇", "番剧", "番劇", "anime", "动画", "動畫", "动漫", "動漫", "series" ->
                LocalDanmuType.Tv.wire
            else -> raw
        }
    }

    fun normalizeEpisode(value: String?): Int? {
        val text = value?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (text.isEmpty()) return null
        val match = Regex("^(?:E|EP|第)?\\s*(\\d+)\\s*(?:集|话|話)?$").find(text) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    fun normalizeSeason(value: String?): Int? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return 1
        val match = Regex("^(?:S(?:EASON)?|第)?\\s*(\\d+)\\s*(?:季)?$", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val season = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return season.takeIf { it > 0 }
    }

    fun buildResourceKey(
        title: String?,
        year: Int?,
        type: String?,
        season: Int? = 1,
        episode: Int? = null
    ): String? {
        val cleanTitle = normalizeKey(title)
        if (cleanTitle.isEmpty()) return null
        val cleanSeason = season ?: 1
        if (cleanSeason <= 0) return null
        val cleanType = normalizeType(type).ifBlank { "any" }
        val parts = mutableListOf(
            cleanTitle,
            year?.toString() ?: "any",
            cleanType
        )
        if (cleanSeason != 1) parts += "s$cleanSeason"
        parts += episode?.toString() ?: "all"
        return parts.joinToString("|")
    }

    fun validateUpload(
        title: String,
        year: Int?,
        type: LocalDanmuType?,
        season: Int?,
        episode: Int?,
        currentYear: Int
    ): String? {
        if (title.trim().isEmpty()) return "标题为必填项"
        if (year == null) return "年份为必填项"
        if (year < 1900 || year > currentYear) return "年份必须在 1900–$currentYear 年之间"
        if (type == null) return "类型为必填项"
        if (type == LocalDanmuType.Tv) {
            if (season == null || season <= 0) return "电视剧的季数必须是大于 0 的整数"
            if (episode == null || episode <= 0) return "电视剧的集数必须是大于 0 的整数"
        } else {
            if (season != null && season <= 0) return "季数必须是大于 0 的整数"
            if (episode != null && episode <= 0) return "集数必须是大于 0 的整数"
        }
        return null
    }
}
