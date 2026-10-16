package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.ui.screen.download.canonicalSourceKey
import com.example.danmuapiapp.domain.model.cleanDownloadAnimeTitle
import com.example.danmuapiapp.ui.screen.download.extractSourceFromAnimeTitle
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Reconstruct metadata from the same filenames and folders used by manual import. */
internal object DownloadRecordRecovery {
    private val sources = setOf("360", "vod", "tmdb", "douban", "tencent", "youku", "iqiyi", "imgo",
        "bilibili", "migu", "renren", "hanjutv", "sohu", "leshi", "xigua", "maiduidui", "aiyifan",
        "hongguo", "dandan", "dandanplay", "bahamut", "animeko", "custom")

    fun source(fileName: String, relativePath: String = ""): String {
        val extension = DanmuDownloadFormat.fromFileName(fileName)?.extension
        val baseName = if (extension != null) fileName.dropLast(extension.length + 1) else fileName
        val platform = baseName.split(Regex("[_@.\\s-]+"))
            .asReversed().map(::canonicalSourceKey).firstOrNull { it in sources }
        if (platform != null) return platform
        val fromTitle = extractSourceFromAnimeTitle(relativePath.substringBeforeLast('/', ""))
        val fromFile = Regex("(?i)\\sfrom\\s+([^_./]+)").find(baseName)?.groupValues?.get(1).orEmpty()
        for (value in listOf(fromFile, fromTitle)) {
            val parts = value.split('&').map(::canonicalSourceKey)
            if (parts.isNotEmpty() && parts.all { it in sources }) return parts.joinToString("&")
        }
        return ""
    }

    fun record(candidate: DownloadDirectoryScanner.Candidate, createdAtFallback: Long): DanmuDownloadRecord {
        val parsed = LocalDanmuMetadataResolver.resolve(candidate.name, candidate.relativePath)
        val metadata = parsed.copy(year = LocalDanmuMetadataResolver.resolve(candidate.name).year ?: parsed.year)
        val source = source(candidate.name, candidate.relativePath)
        val title = buildString {
            append(metadata.title)
            if (metadata.type != "movie" && (metadata.season ?: 1) > 1) append(" 第${metadata.season}季")
            metadata.year?.let { append("($it)") }
            if (metadata.type == "movie") append("【电影】")
        }
        val explicitEpisode = metadata.notes.none { "未识别到集数" in it }
        val digest = MessageDigest.getInstance("SHA-256").digest(candidate.uri.toByteArray(Charsets.UTF_8))
        val id = (ByteBuffer.wrap(digest).long and Long.MAX_VALUE).takeIf { it != 0L } ?: 1L
        return DanmuDownloadRecord(
            id = id,
            createdAt = candidate.lastModified.takeIf { it > 0L } ?: createdAtFallback,
            animeTitle = title,
            episodeTitle = metadata.episodeTitle.ifBlank { candidate.name.removeSuffix(".${candidate.format.extension}") },
            episodeId = 0L,
            episodeNo = if (metadata.type == "movie") 1 else if (explicitEpisode) metadata.episode ?: 0 else 0,
            source = source.ifBlank { "目录同步" },
            format = candidate.format.value,
            status = DownloadRecordStatus.Success.key,
            fileName = candidate.name,
            relativePath = candidate.relativePath,
            fileUri = candidate.uri,
            bytes = candidate.bytes,
            danmuCount = null
        )
    }

    /** Reuse an existing exact/legacy name, then a unique compatible title. */
    fun seriesDirectory(preferred: String, legacy: String, existingNames: List<String>): String {
        if (preferred in existingNames) return preferred
        if (legacy in existingNames) return legacy
        val cleaned = cleanDownloadAnimeTitle(preferred)
        val wanted = LocalDanmuMetadataResolver.resolve("$legacy.xml")
        val matches = existingNames.filter {
            val found = LocalDanmuMetadataResolver.resolve("$it.xml")
            cleanDownloadAnimeTitle(it) == cleaned &&
                (wanted.year == null || found.year == null || wanted.year == found.year)
        }
        return matches.singleOrNull() ?: preferred
    }
}
