package com.example.danmuapiapp.ui.screen.apitest

import java.net.URI
import java.util.Locale

internal fun parseUrlDanmuMetadata(inputUrl: String, html: String): UrlDanmuMetadata? {
    if (html.isBlank()) return null
    val normalizedHtml = decodeJsStringEscapes(html)
    val platform = detectUrlPlatform(inputUrl)
    val pageTitle = findTitle(normalizedHtml).ifBlank { findTitle(html) }
    val ogTitle = findMetaContent(normalizedHtml, "og:title").ifBlank { findMetaContent(html, "og:title") }
    val rawTitle = ogTitle.ifBlank { pageTitle }.trim()
    val episodeNo = findEpisodeNumber(rawTitle, pageTitle, normalizedHtml)
    val title = chooseSeriesTitle(
        platform = platform,
        episodeNo = episodeNo,
        candidates = buildList {
            addAll(findStringValues(normalizedHtml, "second_title"))
            addAll(findStringValues(normalizedHtml, "cover_title"))
            addAll(findStringValues(normalizedHtml, "coverTitle"))
            addAll(findStringValues(normalizedHtml, "video_title"))
            addAll(findStringValues(normalizedHtml, "c_title"))
            addAll(findStringValues(normalizedHtml, "title"))
            add(rawTitle)
            add(pageTitle)
        }
    )
    val year = findYear(rawTitle, normalizedHtml)
    val poster = choosePosterUrl(
        pageUrl = inputUrl,
        candidates = buildList {
            addAll(findStringValues(normalizedHtml, "new_pic_vt"))
            addAll(findStringValues(normalizedHtml, "vertical_pic"))
            addAll(findStringValues(normalizedHtml, "poster"))
            addAll(findStringValues(normalizedHtml, "coverUrl"))
            addAll(findStringValues(normalizedHtml, "cover_url"))
            addAll(findStringValues(normalizedHtml, "pic"))
            addAll(findStringValues(normalizedHtml, "pic496x280"))
            addAll(findStringValues(normalizedHtml, "imgVtUrl"))
            addAll(findStringValues(normalizedHtml, "imgUrl"))
            addAll(findStringValues(normalizedHtml, "new_pic_hz"))
            add(findMetaContent(normalizedHtml, "og:image"))
            add(findMetaContent(normalizedHtml, "twitter:image"))
            add(findMetaContent(normalizedHtml, "image"))
            add(findMetaContent(html, "og:image"))
            add(findMetaContent(html, "twitter:image"))
            add(findMetaContent(html, "image"))
        }
    )
    val episodeLabel = episodeNo?.let { "第${it}集" }.orEmpty()
    val episodeTitle = when {
        title.isNotBlank() && episodeLabel.isNotBlank() -> "$title $episodeLabel"
        title.isNotBlank() -> title
        rawTitle.isNotBlank() -> rawTitle
        else -> inputUrl
    }
    return UrlDanmuMetadata(
        title = title.ifBlank { rawTitle.ifBlank { inputUrl } },
        episodeTitle = episodeTitle,
        posterUrl = poster,
        year = year,
        episodeLabel = episodeLabel,
        platformLabel = platform
    )
}

internal fun normalizeUrlDanmuPosterUrl(pageUrl: String, assetUrl: String): String {
    val decoded = decodeJsStringEscapes(assetUrl).trim()
    if (decoded.isBlank()) return ""
    val absolute = runCatching { URI(pageUrl).resolve(decoded).toString() }
        .getOrElse {
            when {
                decoded.startsWith("//") -> "https:$decoded"
                else -> decoded
            }
        }
    return normalizePosterDeliveryUrl(absolute)
}

private fun choosePosterUrl(pageUrl: String, candidates: List<String>): String {
    return candidates.asSequence()
        .map { normalizeUrlDanmuPosterUrl(pageUrl, it) }
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .distinct()
        .sortedWith(compareBy<String> { posterRank(it) }.thenBy { it.length })
        .firstOrNull()
        .orEmpty()
}

private fun posterRank(url: String): Int {
    val lower = url.lowercase(Locale.ROOT)
    return when {
        "format/avif" in lower || lower.endsWith(".avif") -> 40
        "vcover_vt" in lower || "new_pic_vt" in lower -> 0
        "poster" in lower || "cover" in lower -> 1
        "vpic_cover" in lower || "pic496x280" in lower -> 2
        else -> 10
    }
}

private fun normalizePosterDeliveryUrl(url: String): String {
    var out = url.trim()
    if (out.startsWith("http://", ignoreCase = true)) {
        val lower = out.lowercase(Locale.ROOT)
        if ("qpic.cn" in lower || "puui.qpic.cn" in lower || "gtimg.cn" in lower) {
            out = "https://" + out.substringAfter("http://")
        }
    }
    if (out.contains("format/avif", ignoreCase = true)) {
        out = out
            .replace(Regex("/format/avif/?", RegexOption.IGNORE_CASE), "/")
            .replace(Regex("format/avif/?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[?&]imageMogr2/?$", RegexOption.IGNORE_CASE), "")
            .replace("?&", "?")
            .replace(Regex("[?&]$"), "")
    }
    return out
}

private fun detectUrlPlatform(inputUrl: String): String {
    val lower = inputUrl.lowercase(Locale.ROOT)
    return when {
        ".qq.com" in lower -> "腾讯视频"
        ".bilibili.com" in lower || "b23.tv" in lower -> "哔哩哔哩"
        ".iqiyi.com" in lower -> "爱奇艺"
        ".youku.com" in lower -> "优酷"
        ".mgtv.com" in lower -> "芒果TV"
        ".miguvideo.com" in lower -> "咪咕视频"
        ".sohu.com" in lower -> "搜狐视频"
        ".le.com" in lower -> "乐视视频"
        else -> "URL"
    }
}

private fun findMetaContent(html: String, property: String): String {
    val wanted = property.lowercase(Locale.ROOT)
    val metaRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    return metaRegex.findAll(html)
        .firstNotNullOfOrNull { match ->
            val tag = match.value
            val keys = listOf("property", "name", "itemprop").map { readHtmlAttribute(tag, it).lowercase(Locale.ROOT) }
            if (wanted in keys) readHtmlAttribute(tag, "content").takeIf { it.isNotBlank() } else null
        }
        ?.let { decodeJsStringEscapes(it).trim() }
        .orEmpty()
}

private fun readHtmlAttribute(tag: String, name: String): String {
    val attr = Regex("""\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(tag)
        ?.groupValues
        ?.getOrNull(2)
    return attr?.let { decodeHtmlEntities(it).trim() }.orEmpty()
}

private fun findTitle(html: String): String {
    return Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { decodeJsStringEscapes(it).replace(Regex("\\s+"), " ").trim() }
        .orEmpty()
}

private fun findStringValues(html: String, key: String): List<String> {
    val escapedKey = Regex.escape(key)
    val patterns = listOf(
        Regex("""["']$escapedKey["']\s*[:=]\s*(["'])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\b$escapedKey\b\s*[:=]\s*(["'])(.*?)\1""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    )
    return patterns.asSequence()
        .flatMap { pattern -> pattern.findAll(html).mapNotNull { it.groupValues.getOrNull(2) } }
        .map { decodeJsStringEscapes(it).trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

private fun findYear(title: String, html: String): String {
    val titleYear = Regex("""(?:19|20)\d{2}""").find(title)?.value
    if (!titleYear.isNullOrBlank()) return titleYear
    val patterns = listOf(
        Regex("""[\"']?\b(?:year|publish_year|video_year)\b[\"']?\s*[:=]\s*[\"']?((?:19|20)\d{2})[\"']?""", RegexOption.IGNORE_CASE),
        Regex("""(?:年份|datePublished|publish_date|pub_time)[\"']?\s*[:=：]\s*[\"']?((?:19|20)\d{2})""", RegexOption.IGNORE_CASE)
    )
    return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }.orEmpty()
}

private fun findEpisodeNumber(vararg sources: String): Int? {
    val patterns = listOf(
        Regex("""[\"']?(?:index_title|unionTitle|playTitle|c_title_output|episodeTitle|episode_title|title)[\"']?\s*[:=]\s*[\"'][^\"']*?第\s*0*(\d{1,4})\s*[集话話]""", RegexOption.IGNORE_CASE),
        Regex("""第\s*0*(\d{1,4})\s*[集话話]"""),
        Regex("""[\"']?\bepisode\b[\"']?\s*[:=]\s*[\"']?0*(\d{1,4})[\"']?""", RegexOption.IGNORE_CASE),
        Regex("""(?:^|[^A-Za-z0-9])0*(\d{1,4})(?:[_\-\s](?:电视剧|动漫|综艺|高清)|[集话話])""", RegexOption.IGNORE_CASE)
    )
    sources.forEach { source ->
        if (source.isBlank()) return@forEach
        patterns.forEach { pattern ->
            val value = pattern.find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (value != null && value in 1..9999) return value
        }
    }
    return null
}

private fun chooseSeriesTitle(
    platform: String,
    episodeNo: Int?,
    candidates: List<String>
): String {
    return candidates.asSequence()
        .map { cleanUrlTitle(it, platform, episodeNo) }
        .filter { isLikelySeriesTitle(it) }
        .distinct()
        .firstOrNull()
        .orEmpty()
}

private fun cleanUrlTitle(rawTitle: String, platform: String, episodeNo: Int?): String {
    var title = decodeJsStringEscapes(rawTitle)
        .replace(Regex("""[\r\n\t]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (title.isBlank()) return ""
    title = title
        .removePrefix("【腾讯视频】")
        .removePrefix("腾讯视频")
        .trim(' ', '-', '_', '｜', '|')
        .removeSurrounding("《", "》")
        .trim()
    title = when (platform) {
        "腾讯视频" -> title
            .substringBefore("_高清")
            .substringBefore("_在线观看")
            .substringBefore("_腾讯视频")
            .substringBefore("-腾讯视频")
            .trim()
        "哔哩哔哩" -> title
            .substringBefore("-国创")
            .substringBefore("-番剧")
            .substringBefore("-高清")
            .substringBefore("-bilibili")
            .trim()
        else -> title
    }
    if (episodeNo != null) {
        title = title.replace(Regex("""第\s*0*$episodeNo\s*[集话話].*"""), "").trim()
        title = title.replace(Regex("""[_\-\s]0*$episodeNo(?:[_\-\s].*)?$"""), "").trim()
        title = title.replace(Regex("""\s+0*$episodeNo$"""), "").trim()
    }
    return title
        .replace(Regex("""\s+from\s+.+$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[｜|].*$"""), "")
        .trim(' ', '-', '_', '｜', '|', '《', '》')
}

private fun isLikelySeriesTitle(title: String): Boolean {
    if (title.isBlank()) return false
    if (title.length > 80) return false
    if (title.startsWith("http://", true) || title.startsWith("https://", true)) return false
    val lower = title.lowercase(Locale.ROOT)
    val badKeywords = listOf("高清", "在线观看", "热搜榜", "客户端", "专享礼包", "选集", "播放", "立即")
    if (badKeywords.any { it in lower }) return false
    if (Regex("""^第\s*\d+\s*[集话話]?$""").matches(title)) return false
    if (Regex("""^\d{1,4}$""").matches(title)) return false
    return true
}

private fun decodeJsStringEscapes(raw: String): String {
    return decodeHtmlEntities(raw)
        .replace("\\/", "/")
        .replace("\\u002F", "/")
        .replace("\\u002f", "/")
        .replace("\\u003A", ":")
        .replace("\\u003a", ":")
        .replace("\\u003D", "=")
        .replace("\\u003d", "=")
        .replace("\\u0026", "&")
        .replace("\\u003F", "?")
        .replace("\\u003f", "?")
        .replace("\\\"", "\"")
        .replace("\\'", "'")
}

private fun decodeHtmlEntities(raw: String): String {
    return raw
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
