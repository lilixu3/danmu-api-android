package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import java.security.MessageDigest

internal data class UrlDanmuEpisode(
    val id: String,
    val title: String,
    val url: String,
    val number: Int = 1,
    val label: String = "",
    val group: String = "",
    val posterUrl: String = ""
)

internal data class UrlDanmuMetadata(
    val title: String = "",
    val episodeTitle: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val episodeLabel: String = "",
    val platformLabel: String = "URL",
    val canonicalUrl: String = "",
    val episodes: List<UrlDanmuEpisode> = emptyList(),
    val selectedEpisodeId: String = "",
    val episodesComplete: Boolean = false,
    val notice: String = ""
)

internal data class ManualUrlDanmuSelection(
    val anime: DownloadAnimeCandidate,
    val episode: DownloadEpisodeCandidate
)

internal fun buildManualUrlDanmuSelection(
    inputUrl: String,
    metadata: UrlDanmuMetadata?
): ManualUrlDanmuSelection {
    val source = metadata?.platformLabel?.ifBlank { null } ?: "URL"
    val title = metadata?.title?.ifBlank { null } ?: inputUrl
    val directUrl = metadata?.canonicalUrl?.ifBlank { null } ?: inputUrl
    val animeId = negativeStableId("$source:$directUrl")
    val episodes = metadata?.episodes.orEmpty().filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
        .distinctBy { it.url }.map { ep ->
            DownloadEpisodeCandidate(
                episodeId = negativeStableId("$source:${ep.id}:${ep.url}"),
                episodeNumber = ep.number,
                title = listOf(ep.group, ep.title).filter { it.isNotBlank() }.joinToString(" · "),
                source = source,
                directUrl = ep.url,
                sourceUrl = ep.url,
                posterUrl = metadata?.posterUrl.orEmpty().ifBlank { ep.posterUrl },
                year = metadata?.year.orEmpty(),
                resolvedEpisodeLabel = ep.label
            )
        }
    val selectedUrl = metadata?.episodes?.firstOrNull { it.id == metadata.selectedEpisodeId }?.url
    val selected = episodes.firstOrNull { it.directUrl == selectedUrl }
    val notice = metadata?.notice?.ifBlank { null } ?: when {
        metadata == null -> "未取得视频资料，仍可通过原链接获取弹幕"
        !metadata.episodesComplete && episodes.isEmpty() -> "仅显示当前链接，完整分集暂不可用"
        !metadata.episodesComplete -> "已获取 ${episodes.size} 个播放条目，分集完整性尚未确认"
        else -> ""
    }
    val anime = DownloadAnimeCandidate(
        animeId = animeId,
        title = title,
        episodeCount = episodes.size.coerceAtLeast(1),
        imageUrl = metadata?.posterUrl.orEmpty(),
        sourceLabel = source,
        year = metadata?.year.orEmpty(),
        episodeLabel = selected?.resolvedEpisodeLabel ?: metadata?.episodeLabel.orEmpty(),
        directUrl = selected?.directUrl ?: directUrl,
        resolvedEpisodeTitle = selected?.title ?: metadata?.episodeTitle.orEmpty(),
        directEpisodes = episodes,
        directMetadataNotice = notice
    )
    return ManualUrlDanmuSelection(anime, selected ?: buildManualUrlEpisodeCandidate(anime)!!)
}

internal fun buildManualUrlEpisodeCandidate(anime: DownloadAnimeCandidate): DownloadEpisodeCandidate? {
    val directUrl = anime.directUrl.takeIf { it.isNotBlank() } ?: return null
    anime.directEpisodes.firstOrNull { it.directUrl == directUrl }?.let { return it }
    val episodeNumber = Regex("\\d{1,4}").find(anime.episodeLabel)?.value?.toIntOrNull()?.takeIf { it > 0 } ?: 1
    return DownloadEpisodeCandidate(
        episodeId = negativeStableId("${anime.sourceLabel}:$directUrl"),
        episodeNumber = episodeNumber,
        title = anime.resolvedEpisodeTitle.ifBlank {
            anime.episodeLabel.takeIf { it.isNotBlank() }?.let { "${anime.title} $it" } ?: "URL 直达集"
        },
        source = anime.sourceLabel.ifBlank { "URL" },
        directUrl = directUrl,
        sourceUrl = directUrl,
        posterUrl = anime.imageUrl,
        year = anime.year,
        resolvedEpisodeLabel = anime.episodeLabel
    )
}

private fun negativeStableId(value: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val positive = bytes.take(8).fold(0L) { result, byte -> (result shl 8) or (byte.toLong() and 255L) } and Long.MAX_VALUE
    return -positive.coerceAtLeast(1L)
}
