package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.LocalDanmuParseConfidence
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.domain.model.ResolvedLocalDanmuMetadata
import java.text.Normalizer
import java.util.Locale

/**
 * Resolves import metadata using, in order:
 * 1. an existing download record (exact metadata from this app),
 * 2. the relative directory path (show/season folders),
 * 3. the file name template used by the download page,
 * 4. common third-party file-name patterns.
 */
internal object LocalDanmuMetadataResolver {

    private val knownExtensions = setOf("xml", "json", "ass", "ssa", "csv", "txt", "zip")
    private val appTemplateRegex = Regex(
        "^(?<prefix>.+?)_E(?<ep>\\d{1,4})(?:_(?<rest>.+))?\\.[A-Za-z0-9]{1,8}$",
        RegexOption.IGNORE_CASE
    )
    private val genericEpisodeRegexes = listOf(
        Regex("(?i)(?<![A-Z0-9])S(\\d{1,2})\\s*[._ -]?\\s*E(?:P|PISODE)?\\s*0*(\\d{1,4})(?!\\d)"),
        Regex("(?i)(?<![A-Z0-9])E(?:P|PISODE)?\\s*[._ -]?\\s*0*(\\d{1,4})(?!\\d)"),
        Regex("第\\s*([0-9一二三四五六七八九十百零两]+)\\s*[集话話期]"),
        Regex("(?<!\\d)(\\d{1,4})\\s*[集话話]"),
        Regex("(?<!\\d)(\\d{1,2})\\s*[xX]\\s*(\\d{2,3})(?!\\d)"),
        Regex("[._ -](\\d{1,4})(?=\\s*[\\[(（])")
    )
    private val seasonRegexes = listOf(
        Regex("第\\s*([0-9一二三四五六七八九十百零两]+)\\s*[季部]"),
        Regex("(?i)(?<![A-Z0-9])S(?:EASON)?\\s*0*(\\d{1,2})(?!\\d)"),
        Regex("(?i)(?<![A-Z0-9])SEASON\\s*0*(\\d{1,2})(?!\\d)"),
        Regex("(?<!\\d)(\\d{1,2})\\s*[xX]\\s*\\d{2,3}(?!\\d)")
    )
    private val movieRegex = Regex("(?i)(剧场版|劇場版|电影|電影|movie|film)")
    private val trailingGroupRegex = Regex("\\s+-\\s*[A-Za-z0-9_]{2,24}\\s*$")
    private val sourceSuffixRegex = Regex("(?i)[_\\-@](?:bilibili\\d*|tencent|youku|iqiyi|mango|mgtv|sohu|renren|hanjutv|dandan|bahamut|animeko|xigua|migu|maiduidui|aiyifan|hongguo|custom)\\s*$")

    private val qualityTokens = listOf(
        Regex("(?i)\\b(?:480|576|720|1080|2160|4320)[pi]\\b"),
        Regex("(?i)\\b(?:4K|8K|UHD|HDR|SDR|DV|DoVi)\\b"),
        Regex("(?i)\\b(?:x264|x265|h\\.?264|h\\.?265|hevc|avc|av1|vp9|10bit|8bit|hi10p)\\b"),
        Regex("(?i)\\b(?:web[-_. ]?dl|webrip|web|bluray|blu[-_. ]?ray|bdrip|brrip|hdtv|dvdrip|remux|hdrip|tvrip)\\b"),
        Regex("(?i)\\b(?:aac|flac|ac3|eac3|dts|ddp?|truehd|atmos|5\\.1|7\\.1)\\b"),
        Regex("(?i)\\b(?:chs|cht|gb|big5|jpsc|jptc|sc|tc|简|繁|简体|繁体|中英|双语|内嵌|外挂|中字|无字|生肉|熟肉)\\b"),
        Regex("(?i)\\b(?:repack|proper|v2|v3|complete|合集|全集|完结|高清|超清|蓝光|国语|粤语|日语|国粤|重制版|重製版)\\b")
    )

    private val rootFolderNames = setOf(
        "弹幕下载", "弹幕", "danmaku", "danmu", "download", "downloads", "下载"
    )

    fun resolve(
        fileName: String,
        relativePath: String = "",
        archiveName: String? = null,
        contextName: String? = null,
        downloadRecord: DanmuDownloadRecord? = null
    ): ResolvedLocalDanmuMetadata {
        downloadRecord?.let { record ->
            if (record.animeTitle.isNotBlank()) {
                return resolveFromRecord(record, fileName, relativePath)
            }
        }
        return resolveFromPath(fileName, relativePath, archiveName, contextName)
    }

    private fun resolveFromRecord(
        record: DanmuDownloadRecord,
        fileName: String,
        relativePath: String
    ): ResolvedLocalDanmuMetadata {
        val titleParts = splitTitleAndSeason(record.animeTitle)
        val title = titleParts.first.ifBlank { cleanShowTitle(record.animeTitle) }
        val year = extractYear(record.animeTitle) ?: extractYear(fileName)
        val type = inferType("${record.animeTitle} $fileName")
        val season = titleParts.second
            ?: extractSeason(record.animeTitle)
            ?: extractSeason(relativePath)
            ?: if (type == LocalDanmuType.Movie.wire) null else 1
        val explicitEpisode = record.episodeNo.takeIf { it > 0 }
            ?: extractEpisode(fileName, season)
        val episode = explicitEpisode
            ?: if (type == LocalDanmuType.Movie.wire) null else 1
        return ResolvedLocalDanmuMetadata(
            title = title.ifBlank { "未命名" },
            year = year,
            type = type,
            season = season,
            episode = episode,
            episodeTitle = record.episodeTitle.trim(),
            confidence = if (title.isNotBlank() && year != null && explicitEpisode != null) {
                LocalDanmuParseConfidence.High
            } else if (explicitEpisode != null) {
                LocalDanmuParseConfidence.Medium
            } else {
                LocalDanmuParseConfidence.Low
            },
            notes = listOf("来自下载记录"),
            matchSource = "download-record"
        )
    }

    private fun resolveFromPath(
        fileName: String,
        relativePath: String,
        archiveName: String?,
        contextName: String?
    ): ResolvedLocalDanmuMetadata {
        val normalizedRelative = Normalizer.normalize(relativePath, Normalizer.Form.NFKC)
            .replace('\\', '/')
        val normalizedFile = Normalizer.normalize(fileName, Normalizer.Form.NFKC)
            .replace('\\', '/')
            .substringAfterLast('/')
        val segments = normalizedRelative.split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        if (segments.lastOrNull()?.substringAfterLast('.')?.lowercase(Locale.ROOT) in setOf(
                "xml", "json", "ass", "ssa", "csv", "txt"
            )
        ) {
            segments.removeAt(segments.lastIndex)
        }
        val filteredSegments = segments.filterNot { segment ->
            rootFolderNames.contains(segment.lowercase(Locale.ROOT))
        }

        val seasonFolder = filteredSegments.lastOrNull { segment ->
            extractSeasonOnly(segment) != null
        }
        val folderSeason = seasonFolder?.let(::extractSeasonOnly)
        val showFolder = filteredSegments
            .filterNot { it == seasonFolder }
            .lastOrNull { segment ->
                val cleaned = cleanShowTitle(segment)
                cleaned.isNotBlank() && !isEpisodeFolder(segment)
            }

        val appTemplate = appTemplateRegex.find(normalizedFile)
        val templatePrefix = appTemplate?.groups?.get("prefix")?.value
        val templateEpisode = appTemplate?.groups?.get("ep")?.value?.toIntOrNull()
        val templateRest = appTemplate?.groups?.get("rest")?.value.orEmpty()
        val templateEpisodeTitle = cleanEpisodeTitle(templateRest)

        val filenameTitleCandidate = templatePrefix
            ?: normalizedFile.substringBeforeEpisodeMarker()
        val filenameTitle = cleanShowTitle(filenameTitleCandidate)

        val folderTitle = showFolder?.let(::cleanShowTitle).orEmpty()
        val archiveTitle = archiveName
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC).substringAfterLast('/') }
            ?.let(::cleanShowTitle)
            .orEmpty()
        val extraContextTitle = contextName
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC).substringAfterLast('/') }
            ?.let(::cleanShowTitle)
            .orEmpty()

        val title = when {
            folderTitle.isNotBlank() && folderTitle != "未命名" -> folderTitle
            filenameTitle.isNotBlank() &&
                filenameTitle != "未命名" &&
                !Regex("^\\d{1,4}$").matches(filenameTitle) -> filenameTitle
            archiveTitle.isNotBlank() -> archiveTitle
            extraContextTitle.isNotBlank() && extraContextTitle != "未命名" -> extraContextTitle
            filenameTitle.isNotBlank() -> filenameTitle
            else -> "未命名"
        }
        val titleParts = splitTitleAndSeason(title)
        val cleanTitle = titleParts.first.ifBlank { title }

        val searchText = listOf(normalizedRelative, normalizedFile, archiveName.orEmpty()).joinToString(" ")
        val year = extractYear(showFolder.orEmpty())
            ?: extractYear(normalizedFile)
            ?: extractYear(archiveName.orEmpty())
            ?: extractYear(contextName.orEmpty())
        val season = folderSeason
            ?: titleParts.second
            ?: extractSeason(normalizedFile)
            ?: extractSeason(showFolder.orEmpty())
            ?: extractSeason(archiveName.orEmpty())
            ?: extractSeason(contextName.orEmpty())
        val explicitEpisode = templateEpisode ?: extractEpisode(normalizedFile, season)
        val episode = explicitEpisode
            ?: if (inferType(searchText) == LocalDanmuType.Movie.wire) null else 1
        val type = inferType(searchText)

        val notes = buildList {
            if (showFolder != null && folderTitle.isNotBlank()) add("剧名来自目录")
            else if (templatePrefix != null) add("剧名来自下载文件模板")
            else add("剧名来自文件名")
            if (folderSeason != null) add("季来自目录")
            if (year == null) add("未识别到年份，需要手动选择")
            if (title == "未命名") add("未识别到剧名，需要手动填写")
            if (explicitEpisode == null) add("未识别到集数，默认第 1 集，请确认")
        }.distinct()

        val confidence = when {
            cleanTitle == "未命名" || episode == null -> LocalDanmuParseConfidence.Low
            showFolder != null && templateEpisode != null -> LocalDanmuParseConfidence.High
            templatePrefix != null && templateEpisode != null -> LocalDanmuParseConfidence.High
            year != null && explicitEpisode != null -> LocalDanmuParseConfidence.High
            explicitEpisode != null -> LocalDanmuParseConfidence.Medium
            else -> LocalDanmuParseConfidence.Low
        }

        val safeType = type
        val safeSeason = when {
            safeType == LocalDanmuType.Movie.wire -> season
            season != null && season > 0 -> season
            else -> 1
        }
        val safeEpisode = when {
            safeType == LocalDanmuType.Movie.wire -> episode
            episode != null && episode > 0 -> episode
            else -> 1
        }

        return ResolvedLocalDanmuMetadata(
            title = cleanTitle.ifBlank { "未命名" },
            year = year,
            type = safeType,
            season = safeSeason,
            episode = safeEpisode,
            episodeTitle = templateEpisodeTitle,
            confidence = confidence,
            notes = notes,
            matchSource = when {
                showFolder != null -> "folder"
                templatePrefix != null -> "download-template"
                extraContextTitle.isNotBlank() -> "context"
                else -> "filename"
            }
        )
    }

    private fun String.substringBeforeEpisodeMarker(): String {
        val markers = listOf(
            Regex("(?i)[._ -]S\\d{1,2}\\s*[._ -]?\\s*E(?:P|PISODE)?\\s*\\d{1,4}"),
            Regex("(?i)(?<![A-Z0-9])E(?:P|PISODE)?\\s*[._ -]?\\s*0*\\d{1,4}(?!\\d)"),
            Regex("第\\s*[0-9一二三四五六七八九十百零两]+\\s*[集话話期]"),
            Regex("(?<![\\d])[._ -]\\d{1,4}\\s*[集话話]"),
            Regex("[._ -]\\d{1,4}\\s*(?=[\\[(（]|$)")
        )
        val positions = markers.mapNotNull { regex -> regex.find(this)?.range?.first }
        val markerIndex = positions.minOrNull() ?: return this
        return substring(0, markerIndex)
    }

    private fun splitTitleAndSeason(raw: String): Pair<String, Int?> {
        var value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        var season: Int? = null
        for (pattern in seasonRegexes) {
            val match = pattern.find(value) ?: continue
            val number = match.groupValues.getOrNull(1).orEmpty()
            val parsed = parseNumberToken(number)
            if (parsed != null && parsed > 0) {
                season = parsed
                value = value.removeRange(match.range)
                break
            }
        }
        return cleanShowTitle(value) to season
    }

    private fun cleanShowTitle(raw: String): String {
        var value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .replace('\\', '/')
            .substringAfterLast('/')
        value = stripKnownExtension(value)
        value = value.replace(Regex("(?i)\\s*from\\s+.*$"), " ")
        value = value.replace(Regex("^\\s*(?:\\[[^\\]]*]|【[^】]*】)+\\s*"), " ")
        value = value.replace(Regex("【[^】]*】"), " ")
        value = value.replace(Regex("[\\(（]\\s*(?:19|20)\\d{2}\\s*[\\)）]"), " ")
        value = value.replace(Regex("\\[\\s*(?:19|20)\\d{2}\\s*]"), " ")
        value = value.replace(sourceSuffixRegex, " ")
        value = value.replace(trailingGroupRegex, " ")
        qualityTokens.forEach { value = it.replace(value, " ") }
        value = value.replace(Regex("\\[[A-Za-z0-9._ -]{1,24}]"), " ")
        value = value.replace(Regex("[_]+"), " ")
        if (value.contains(' ')) {
            // Keep dots inside titles such as "Mr. Robot" when the name already has spaces.
        } else {
            value = value.replace('.', ' ')
        }
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-', '_', '.', '—', '–', ' ', '[', ']', '【', '】', '(', ')', '（', '）')
            .take(160)
    }

    private fun cleanEpisodeTitle(raw: String): String {
        if (raw.isBlank()) return ""
        var value = raw
            .replace(Regex("(?i)[_\\-@](?:bilibili\\d*|tencent|youku|iqiyi|mango|mgtv|sohu|renren|hanjutv|dandan|bahamut|animeko|xigua|migu|maiduidui|aiyifan|hongguo|custom)\\s*$"), "")
            .replace(Regex("[_]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim('-', '_', '.', ' ')
        return value.take(120)
    }

    private fun isEpisodeFolder(raw: String): Boolean {
        val value = raw.trim()
        return Regex("(?i)^(?:S\\d{1,2}|Season\\s*\\d+|第\\s*\\d+\\s*[季部]|第\\s*\\d+\\s*[集话話]|EP?\\d+|OVA|OAD|SP|SPs|特典|花絮|合集)$")
            .matches(value)
    }

    private fun inferType(text: String): String {
        return if (movieRegex.containsMatchIn(text)) {
            LocalDanmuType.Movie.wire
        } else {
            LocalDanmuType.Tv.wire
        }
    }

    private fun extractSeasonOnly(raw: String): Int? {
        val value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        for (pattern in seasonRegexes) {
            val match = pattern.find(value) ?: continue
            val parsed = parseNumberToken(match.groupValues.getOrNull(1).orEmpty())
            if (parsed != null && parsed in 1..99) return parsed
        }
        return null
    }

    private fun extractSeason(text: String): Int? {
        if (text.isBlank()) return null
        return extractSeasonOnly(text)
    }

    private fun extractEpisode(text: String, season: Int?): Int? {
        if (text.isBlank()) return null
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replace('\\', '/')
        val baseName = normalized.substringAfterLast('/').substringBeforeLast('.')
        if (Regex("^\\d{1,4}$").matches(baseName)) {
            val numeric = baseName.toIntOrNull()
            if (numeric != null && numeric in 1..999 && numeric !in 1900..2099) return numeric
        }
        genericEpisodeRegexes.forEachIndexed { index, pattern ->
            pattern.findAll(normalized).forEach { match ->
                val raw = when (index) {
                    0, 4 -> match.groupValues.getOrNull(2).orEmpty()
                    else -> match.groupValues.getOrNull(1).orEmpty()
                }
                val parsed = parseNumberToken(raw)
                if (parsed != null && parsed in 1..9999) return parsed
            }
        }
        val trailing = Regex("[\\s._-](\\d{1,4})$").find(normalized) ?: return null
        val parsed = trailing.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        if (parsed in 1900..2099) return null
        return parsed.takeIf { it in 1..999 }
    }

    private fun stripKnownExtension(value: String): String {
        val dotIndex = value.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == value.lastIndex) return value
        val extension = value.substring(dotIndex + 1).lowercase(Locale.ROOT)
        return if (extension in knownExtensions) value.substring(0, dotIndex) else value
    }

    private fun extractYear(text: String): Int? {
        if (text.isBlank()) return null
        val bracketed = Regex("[\\[(【]((?:19|20)\\d{2})[\\])】]")
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .lastOrNull { it in 1900..2099 }
        if (bracketed != null) return bracketed
        return Regex("(?<![0-9A-Za-z])((?:19|20)\\d{2})(?![0-9])")
            .findAll(text)
            .mapNotNull { match ->
                val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val before = text.getOrNull(match.range.first - 1)
                val after = text.getOrNull(match.range.last + 1)
                if (before?.equals('x', true) == true || after?.equals('x', true) == true) {
                    null
                } else {
                    value
                }
            }
            .lastOrNull { it in 1900..2099 }
    }

    private fun parseNumberToken(raw: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        value.toIntOrNull()?.let { return it }
        return parseChineseNumber(value)
    }

    private fun parseChineseNumber(raw: String): Int? {
        val digits = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        if (raw.isEmpty()) return null
        if (raw.all { it.isDigit() }) return raw.toIntOrNull()
        var section = 0
        var number = 0
        var matched = false
        for (char in raw) {
            when (char) {
                '十' -> {
                    section += (if (number == 0) 1 else number) * 10
                    number = 0
                    matched = true
                }
                '百' -> {
                    section += (if (number == 0) 1 else number) * 100
                    number = 0
                    matched = true
                }
                else -> {
                    val digit = digits[char] ?: return null
                    number = digit
                    matched = true
                }
            }
        }
        val total = section + number
        return total.takeIf { matched && it > 0 }
    }
}
