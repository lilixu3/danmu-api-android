package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuPayloadKind
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

sealed interface DanmuExportTarget {
    data class Episode(val episodeId: Long) : DanmuExportTarget
    data class VideoUrl(val url: String) : DanmuExportTarget
}

enum class DanmuExportGroup(val label: String) {
    General("通用格式"),
    Player("播放器格式"),
    Advanced("高级格式")
}

data class ApiTestExportOption(
    val format: DanmuDownloadFormat,
    val group: DanmuExportGroup,
    val displayName: String
)

data class DanmuExportPayload(
    val id: Long,
    val bytes: ByteArray,
    val format: DanmuDownloadFormat,
    val fileName: String,
    val contentType: String
)

object ApiTestExportCatalog {
    val options: List<ApiTestExportOption> = listOf(
        ApiTestExportOption(DanmuDownloadFormat.Json, DanmuExportGroup.General, "通用 JSON"),
        ApiTestExportOption(DanmuDownloadFormat.Xml, DanmuExportGroup.General, "Bilibili XML（内置）"),
        ApiTestExportOption(DanmuDownloadFormat.DdplayJson, DanmuExportGroup.Player, "弹弹Play JSON"),
        ApiTestExportOption(DanmuDownloadFormat.DplayerJson, DanmuExportGroup.Player, "DPlayer JSON"),
        ApiTestExportOption(DanmuDownloadFormat.ArtplayerJson, DanmuExportGroup.Player, "ArtPlayer JSON"),
        ApiTestExportOption(DanmuDownloadFormat.VodJson, DanmuExportGroup.Player, "VOD JSON"),
        ApiTestExportOption(DanmuDownloadFormat.BahaJson, DanmuExportGroup.Player, "巴哈姆特 JSON"),
        ApiTestExportOption(DanmuDownloadFormat.BiliXml, DanmuExportGroup.Advanced, "Bilibili XML（DanUni）"),
        ApiTestExportOption(DanmuDownloadFormat.DanuniJson, DanmuExportGroup.Advanced, "DanUni JSON"),
        ApiTestExportOption(DanmuDownloadFormat.DanuniBinPb, DanmuExportGroup.Advanced, "DanUni Protobuf")
    )
}

internal fun buildDanmuExportUrl(
    apiBaseUrl: String,
    target: DanmuExportTarget,
    format: DanmuDownloadFormat
): String {
    val base = apiBaseUrl.trim().trimEnd('/')
    require(base.isNotBlank()) { "弹幕源 Base URL 无效" }
    val encodedFormat = encodeQueryValue(format.value)
    return when (target) {
        is DanmuExportTarget.Episode -> {
            require(target.episodeId > 0L) { "弹幕 ID 无效" }
            "$base/api/v2/comment/${target.episodeId}?format=$encodedFormat"
        }
        is DanmuExportTarget.VideoUrl -> {
            require(target.url.isNotBlank()) { "视频 URL 无效" }
            "$base/api/v2/comment?url=${encodeQueryValue(target.url)}&format=$encodedFormat"
        }
    }
}

internal fun buildDanmuExportFileName(
    animeTitle: String,
    episodeTitle: String,
    target: DanmuExportTarget,
    format: DanmuDownloadFormat
): String {
    val anime = sanitizeExportFileComponent(animeTitle)
    val episode = sanitizeExportFileComponent(episodeTitle)
    val rawBase = when {
        episode.isNotBlank() && anime.isNotBlank() && episode.equals(anime, ignoreCase = true) -> episode
        episode.isNotBlank() && anime.isNotBlank() && episode.startsWith(anime, ignoreCase = true) -> episode
        episode.isNotBlank() && anime.isNotBlank() -> "$anime - $episode"
        episode.isNotBlank() -> episode
        anime.isNotBlank() -> anime
        target is DanmuExportTarget.Episode -> "danmu_${target.episodeId}"
        else -> "danmu_export"
    }
    val maxBaseLength = (120 - format.extension.length - 1).coerceAtLeast(24)
    val base = rawBase.take(maxBaseLength).trim().trimEnd('.', ' ').ifBlank { "danmu_export" }
    return "$base.${format.extension}"
}

internal fun normalizeDanmuExportPayload(
    bytes: ByteArray,
    format: DanmuDownloadFormat
): ByteArray {
    if (format.payloadKind != DanmuPayloadKind.Json) return bytes
    val cleaned = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
    if (cleaned.isBlank()) return bytes
    val pretty = runCatching {
        when (val root = JSONTokener(cleaned).nextValue()) {
            is JSONObject -> root.toString(2)
            is JSONArray -> root.toString(2)
            else -> cleaned
        }
    }.getOrElse { return bytes }
    return (pretty.trimEnd() + "\n").toByteArray(Charsets.UTF_8)
}

private fun sanitizeExportFileComponent(raw: String): String {
    return raw
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ' ')
}

private fun encodeQueryValue(value: String): String {
    return URLEncoder.encode(value, Charsets.UTF_8.name())
}
