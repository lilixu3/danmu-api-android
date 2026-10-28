package com.example.danmuapiapp.ui.screen.apitest

import java.net.URI
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun parseUrlDanmuMetadata(inputUrl: String, html: String): UrlDanmuMetadata? {
    if (html.isBlank()) return null
    val platform = detectUrlPlatform(inputUrl)
    // Only parse known, identity-bound state. Recommendations and ads contain the same field names.
    if (UrlDanmuMetadataPlatform.detect(inputUrl) == UrlDanmuMetadataPlatform.Bilibili) {
        extractUrlPageObject(html, "__INITIAL_STATE__")?.let { state ->
            val video = runCatching { org.json.JSONObject(state).optJSONObject("videoData") }.getOrNull()
            if (video != null) {
                UrlDanmuMetadataPlatformParsers.parseBilibiliViewJson(inputUrl,
                    org.json.JSONObject().put("code", 0).put("data", video).toString())?.let { return it }
            }
        }
    }
    val pageTitle = findTitle(html)
    val rawTitle = findMetaContent(html, "og:title").ifBlank { pageTitle }.trim()
    val pageIdentity = findMetaContent(html, "og:url")
    if (pageIdentity.isNotBlank() && !sameUrlVideo(inputUrl, pageIdentity)) return null
    val cid = Regex("""/x/cover/([^/?.]+)""").find(inputUrl)?.groupValues?.get(1)
        ?: runCatching { inputUrl.toHttpUrlOrNull()?.queryParameter("cid") }.getOrNull()
    val coverState = if (platform == "腾讯视频" && cid != null) {
        extractUrlPageObject(html, "COVER_INFO")?.takeIf { state ->
            (findStringValues(state, "id") + findStringValues(state, "cid")).contains(cid)
        }.orEmpty()
    } else ""
    val vid = Regex("""/x/cover/[^/]+/([^/.?]+)""").find(inputUrl)?.groupValues?.get(1)
        ?: runCatching { inputUrl.toHttpUrlOrNull()?.queryParameter("vid") }.getOrNull()
    val videoState = if (platform == "腾讯视频" && vid != null) {
        extractUrlPageObject(html, "VIDEO_INFO")?.takeIf { findStringValues(it, "vid").contains(vid) }.orEmpty()
    } else ""
    val episodeNo = findEpisodeNumber(rawTitle, pageTitle, videoState)
    val scopedTitle = (findStringValues(coverState, "title") + findStringValues(coverState, "second_title")).firstOrNull().orEmpty()
    val title = cleanUrlTitle(scopedTitle.ifBlank { rawTitle }, platform, if (scopedTitle.isBlank()) episodeNo else null)
    val poster = (listOf("new_pic_vt", "vertical_pic", "pic").flatMap { findStringValues(coverState, it) } +
        listOf(findMetaContent(html, "og:image"), findMetaContent(html, "twitter:image"), findMetaContent(html, "image")))
        .asSequence().map { normalizeUrlDanmuPosterUrl(inputUrl, it) }
        .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }.orEmpty()
    val year = findYear(listOf(findMetaContent(html, "video:release_date"),
        findMetaContent(html, "op:video:release_date"), findMetaContent(html, "datePublished")), coverState)
    if (title.isBlank() && poster.isBlank()) return null
    val label = episodeNo?.let { "第${it}集" }.orEmpty()
    return UrlDanmuMetadata(title = title.ifBlank { inputUrl },
        episodeTitle = listOf(title, label).filter { it.isNotBlank() }.joinToString(" "),
        posterUrl = poster, year = year, episodeLabel = label, platformLabel = platform, canonicalUrl = inputUrl)
}

/** Extract one balanced object, preserving JSON escapes and braces inside strings. */
internal fun extractUrlPageObject(html: String, name: String): String? {
    val marker = Regex("""(?:window\.)?${Regex.escape(name)}\s*=\s*""").find(html) ?: return null
    val start = marker.range.last + 1
    if (html.getOrNull(start) != '{') return null
    var depth = 0
    var quote: Char? = null
    var escaped = false
    for (i in start until html.length) {
        val ch = html[i]
        if (quote != null) {
            if (escaped) escaped = false
            else if (ch == '\\') escaped = true
            else if (ch == quote) quote = null
        } else when (ch) {
            '\'', '"' -> quote = ch
            '{' -> depth++
            '}' -> if (--depth == 0) return html.substring(start, i + 1)
        }
    }
    return null
}

internal fun sameUrlVideo(first: String, second: String): Boolean {
    fun identity(value: String): String {
        val url = runCatching { URI(value) }.getOrNull() ?: return value
        // Share metadata may omit the selected P; compare the video identity, not tracking fields.
        val bili = Regex("""/(BV[0-9A-Za-z]+|av\d+|ep\d+|ss\d+)(?:/|$)""").find(url.path.orEmpty())?.groupValues?.get(1)
        if (bili != null && UrlDanmuMetadataPlatform.detect(value) == UrlDanmuMetadataPlatform.Bilibili) return "bilibili:$bili"
        return url.host.orEmpty().removePrefix("m.").removePrefix("www.") + url.path.orEmpty().trimEnd('/') +
            url.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') in setOf("cid", "vid", "id", "sn") }.sorted().joinToString("&")
    }
    return identity(first) == identity(second)
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
    return when (UrlDanmuMetadataPlatform.detect(inputUrl)) {
        UrlDanmuMetadataPlatform.Tencent -> "腾讯视频"
        UrlDanmuMetadataPlatform.Bilibili -> "哔哩哔哩"
        UrlDanmuMetadataPlatform.Iqiyi -> "爱奇艺"
        UrlDanmuMetadataPlatform.Youku -> "优酷"
        UrlDanmuMetadataPlatform.Mango -> "芒果TV"
        UrlDanmuMetadataPlatform.Migu -> "咪咕视频"
        UrlDanmuMetadataPlatform.Sohu -> "搜狐视频"
        UrlDanmuMetadataPlatform.Leshi -> "乐视视频"
        UrlDanmuMetadataPlatform.Xigua -> "西瓜视频"
        UrlDanmuMetadataPlatform.Unknown -> "URL"
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

private fun findYear(candidates: List<String>, html: String): String {
    candidates.forEach { candidate ->
        val year = Regex("""(?:19|20)\d{2}""").find(candidate)?.value
        if (!year.isNullOrBlank()) return year
    }
    val patterns = listOf(
        Regex("""[\"']?\b(?:year|publish_year|video_year)\b[\"']?\s*[:=]\s*[\"']?((?:19|20)\d{2})[\"']?""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:datePublished|publish_date|pub_time|publish_time|release_date)\b[\"']?\s*[:=：]\s*[\"']?((?:19|20)\d{2})""", RegexOption.IGNORE_CASE)
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(html)?.groupValues?.getOrNull(1)
    }.orEmpty()
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
            .substringBefore("_哔哩哔哩")
            .substringBefore("_bilibili")
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
