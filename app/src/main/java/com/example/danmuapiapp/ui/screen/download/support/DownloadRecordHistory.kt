package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.canonicalSourceKey
import com.example.danmuapiapp.domain.model.cleanDownloadAnimeTitle
import com.example.danmuapiapp.domain.model.extractSourceFromAnimeTitle
import com.example.danmuapiapp.data.repository.DownloadLocalImportPlanner
import com.example.danmuapiapp.data.repository.LocalDanmuMetadataResolver
import java.util.Locale

internal fun buildAnimeDownloadHistorySummary(
    anime: DownloadAnimeCandidate,
    records: List<DanmuDownloadRecord>
): AnimeDownloadHistorySummary {
    val successful = records.filter { record ->
        record.statusEnum() == DownloadRecordStatus.Success && recordMatchesAnime(record, anime)
    }
    return AnimeDownloadHistorySummary(
        downloadedEpisodeCount = successful.map(::downloadedEpisodeKey).distinct().size,
        successfulRecordCount = successful.size,
        latestDownloadedAt = successful.maxOfOrNull { it.createdAt }
    )
}

internal fun recordMatchesAnime(
    record: DanmuDownloadRecord,
    anime: DownloadAnimeCandidate
): Boolean {
    if (!animeIdentityMatches(record.animeTitle, record.animeId, anime.title, anime.animeId)) {
        return false
    }
    val candidateSource = anime.sourceLabel.ifBlank { extractSourceFromAnimeTitle(anime.title) }
    return historySourceMatches(candidateSource, record.source)
}

internal fun animeIdentityMatches(
    storedTitle: String,
    storedAnimeId: Long,
    candidateTitle: String,
    candidateAnimeId: Long
): Boolean {
    if (storedAnimeId > 0L && candidateAnimeId > 0L) {
        return storedAnimeId == candidateAnimeId
    }
    val candidateKey = normalizeAnimeTitleForMatch(candidateTitle)
    if (candidateKey.isBlank() || normalizeAnimeTitleForMatch(storedTitle) != candidateKey) {
        return false
    }
    val storedYear = extractAnimeYear(storedTitle)
    val candidateYear = extractAnimeYear(candidateTitle)
    return storedYear.isBlank() || candidateYear.isBlank() || storedYear == candidateYear
}

internal fun historySourceMatches(candidateSource: String, recordSource: String): Boolean {
    val candidateKey = canonicalSourceKey(candidateSource)
    val recordKey = canonicalSourceKey(recordSource)
    return candidateKey == "unknown" || isNeutralHistorySource(recordSource) || candidateKey == recordKey
}

internal fun isNeutralHistorySource(raw: String): Boolean {
    val normalized = raw.trim().lowercase()
    return normalized.isBlank() ||
        normalized == "unknown" ||
        normalized == "目录同步" ||
        normalized == "目录导入"
}

internal fun buildDownloadRecordAnimeGroups(
    records: List<DanmuDownloadRecord>
): List<DownloadRecordAnimeGroup> {
    // Flutter groups by clean title and year. A single known year also identifies older scans
    // whose folder name omitted the year; multiple known years stay separate.
    return records.groupBy { recordLibraryTitle(it).lowercase(Locale.ROOT) }.values
        .flatMap { bucket ->
            val years = bucket.mapNotNull(::recordLibraryYear).toSet()
            val fallbackYear = years.singleOrNull()
            bucket.groupBy { "${recordLibraryTitle(it).lowercase(Locale.ROOT)}|${recordLibraryYear(it) ?: fallbackYear ?: ""}" }.entries
        }
        .map { (key, groupedRecords) ->
            val sortedRecords = groupedRecords.sortedByDescending { it.createdAt }
            val latest = sortedRecords.first()
            DownloadRecordAnimeGroup(
                key = key,
                title = displayAnimeTitle(latest),
                records = sortedRecords,
                episodes = buildDownloadRecordEpisodeGroups(sortedRecords),
                latestAt = latest.createdAt,
                failedCount = sortedRecords.count { it.statusEnum() == DownloadRecordStatus.Failed },
                skippedCount = sortedRecords.count { it.statusEnum() == DownloadRecordStatus.Skipped },
                totalBytes = sortedRecords
                    .asSequence()
                    .filter { it.statusEnum() == DownloadRecordStatus.Success }
                    .distinctBy { it.fileUri.ifBlank { "record-${it.id}" } }
                    .map { it.bytes.coerceAtLeast(0L) }
                    .sum()
            )
        }
        .sortedByDescending { it.latestAt }
}

internal fun buildDownloadRecordEpisodeGroups(
    records: List<DanmuDownloadRecord>
): List<DownloadRecordEpisodeGroup> {
    return records
        .groupBy(::downloadEpisodeGroupKey)
        .map { (key, groupedRecords) ->
            val sortedRecords = groupedRecords.sortedByDescending { it.createdAt }
            val successful = sortedRecords.filter { it.statusEnum() == DownloadRecordStatus.Success }
            val representative = successful.firstOrNull() ?: sortedRecords.first()
            DownloadRecordEpisodeGroup(
                key = key,
                episodeNo = representative.episodeNo,
                title = representative.episodeTitle.ifBlank {
                    representative.fileName.ifBlank { "未命名剧集" }
                },
                records = sortedRecords,
                representative = representative,
                previewRecord = successful.firstOrNull { record ->
                    record.fileUri.isNotBlank() && record.formatOrNull()?.supportsPreview == true
                },
                status = if (successful.isNotEmpty()) {
                    DownloadRecordStatus.Success
                } else {
                    sortedRecords.first().statusEnum()
                },
                successfulFileCount = successful
                    .map { it.fileUri.ifBlank { "record-${it.id}" } }
                    .distinct()
                    .size,
                latestAt = sortedRecords.first().createdAt
            )
        }
        .sortedWith(
            compareBy<DownloadRecordEpisodeGroup> { if (it.episodeNo > 0) 0 else 1 }
                .thenBy { it.episodeNo }
                .thenByDescending { it.latestAt }
        )
}

private fun recordLibraryTitle(record: DanmuDownloadRecord): String {
    val raw = cleanDownloadAnimeTitle(record.animeTitle)
    if (raw.isNotBlank() && raw !in setOf("已有弹幕", "未命名剧集", "未命名", "弹幕下载") &&
        !Regex("(?i)^(?:S(?:eason)?\\s*\\d+|第[0-9一二三四五六七八九十]+季)$").matches(raw)) return raw
    val metadata = DownloadLocalImportPlanner.metadata(record)
    return metadata.title + if ((metadata.season ?: 1) > 1) " 第${metadata.season}季" else ""
}

private fun recordLibraryYear(record: DanmuDownloadRecord): Int? =
    LocalDanmuMetadataResolver.resolve(record.fileName).year
        ?: LocalDanmuMetadataResolver.resolve(record.animeTitle + ".xml").year
        ?: DownloadLocalImportPlanner.metadata(record).year

private fun extractAnimeYear(raw: String): String {
    return Regex("[（(](\\d{4})[)）]")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
}

private fun downloadEpisodeGroupKey(record: DanmuDownloadRecord): String {
    if (record.episodeNo > 0) return "number-${record.episodeNo}"
    val titleKey = normalizeEpisodeTitleForMatch(record.episodeTitle)
    if (titleKey.isNotBlank()) return "title-$titleKey"
    if (record.episodeId > 0L) return "id-${record.episodeId}"
    return "record-${record.id}"
}

private fun downloadedEpisodeKey(record: DanmuDownloadRecord): String {
    if (record.episodeNo > 0) return "number-${record.episodeNo}"
    val titleKey = normalizeEpisodeTitleForMatch(record.episodeTitle)
    if (titleKey.isNotBlank()) return "title-$titleKey"
    if (record.episodeId > 0L) return "id-${record.episodeId}"
    return "record-${record.id}"
}

private fun displayAnimeTitle(record: DanmuDownloadRecord): String {
    return record.animeTitle
        .replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { "未命名剧集" }
}
