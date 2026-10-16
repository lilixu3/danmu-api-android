package com.example.danmuapiapp.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared with Flutter's cleanAnimeTitle/sanitizeFileName/renderFileName rules. */
fun cleanDownloadAnimeTitle(raw: String): String {
    var name = raw.trim()
    if (name.isEmpty()) return name
    name = name.replace(Regex("^\\s*(\\[[^\\]]*]|【[^】]*】)\\s*"), "")
        .replace(Regex("[（(\\[【]\\s*(?:19|20)\\d{2}\\s*[）)\\]】]"), "")
        .replace(Regex("【[^】]*】"), "")
        .replace(Regex("\\s*(?:from|@|by|来源[:：]?)\\s*[^\\s（()\\[\\]【】]+$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[\\s\\-_.]*(bilibili\\d*|dandanplay|dandan|360kan|douban|renren|hanjutv|mgtv|tencent|qq|iqiyi|youku|migu|sohu)\\d*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[\\s\\-_.·]+$|^[\\s\\-_.·]+"), "")
        .replace(Regex("[\\s&+,、]+$"), "")
        .replace(Regex("[（(\\[【]\\s*[）)\\]】]$"), "")
        .trim()
    return name.ifEmpty { raw.trim() }
}

fun sanitizeDownloadFileName(raw: String): String {
    val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
        .replace(Regex("\\s+"), " ").trim()
    return truncateDownloadName(cleaned.takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "danmu", 180, 240)
}

// Android filesystems limit a component in UTF-8 bytes; do not split a surrogate pair.
private fun truncateDownloadName(value: String, maxChars: Int, maxBytes: Int): String {
    var end = 0
    var bytes = 0
    while (end < value.length) {
        val size = Character.charCount(value.codePointAt(end))
        val next = value.substring(end, end + size).toByteArray(Charsets.UTF_8).size
        if (end + size > maxChars || bytes + next > maxBytes) break
        end += size
        bytes += next
    }
    return value.substring(0, end)
}

/** Keep multipart extensions and leave room for filesystem conflict suffixes. */
fun numberedDownloadFileName(name: String, index: Int): String {
    val extension = DanmuDownloadFormat.fromFileName(name)?.extension
        ?: name.substringAfterLast('.', "")
    val suffix = if (extension.isBlank()) "" else ".$extension"
    val number = "($index)"
    val base = if (suffix.isEmpty()) name else name.dropLast(suffix.length)
    return truncateDownloadName(base, 180 - suffix.length - number.length,
        240 - suffix.toByteArray(Charsets.UTF_8).size - number.length) + number + suffix
}

fun renderDownloadFileName(
    template: String,
    format: DanmuDownloadFormat,
    animeTitle: String,
    episodeTitle: String,
    episodeNo: Int,
    episodeId: Long,
    source: String,
    now: Date = Date()
): String {
    val values = linkedMapOf(
        "animeTitle" to animeTitle,
        "episodeTitle" to episodeTitle,
        "episodeNo" to episodeNo.toString(),
        "episodeNo2" to episodeNo.toString().padStart(2, '0'),
        "episodeNo3" to episodeNo.toString().padStart(3, '0'),
        "episodeId" to episodeId.toString(),
        "source" to source,
        "format" to format.value,
        "ext" to format.extension,
        "date" to SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(now),
        "datetime" to SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(now)
    )
    var output = template.trim().ifBlank { DanmuDownloadSettings().fileNameTemplate }
    values.forEach { (key, value) -> output = output.replace("{$key}", value) }
    val suffix = ".${format.extension}"
    if (output.endsWith(suffix, ignoreCase = true)) output = output.dropLast(suffix.length)
    val base = sanitizeDownloadFileName(output)
    return truncateDownloadName(base, 180 - suffix.length, 240 - suffix.toByteArray(Charsets.UTF_8).size) + suffix
}
