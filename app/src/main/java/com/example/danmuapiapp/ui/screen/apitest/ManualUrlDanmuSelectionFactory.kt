package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate

internal data class UrlDanmuMetadata(
    val title: String = "",
    val episodeTitle: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val episodeLabel: String = "",
    val platformLabel: String = "URL"
)

internal data class ManualUrlDanmuSelection(
    val anime: DownloadAnimeCandidate,
    val episode: DownloadEpisodeCandidate,
    val metadataTrace: List<DanmuRequestTrace>
)

internal fun buildManualUrlDanmuSelection(
    inputUrl: String,
    metadata: UrlDanmuMetadata?,
    metadataTrace: List<DanmuRequestTrace>
): ManualUrlDanmuSelection {
    val source = metadata?.platformLabel?.ifBlank { null } ?: "URL"
    val title = metadata?.title?.ifBlank { null } ?: inputUrl
    val episodeLabel = metadata?.episodeLabel.orEmpty()
    val episodeTitle = metadata?.episodeTitle?.ifBlank { null }
        ?: episodeLabel.takeIf { it.isNotBlank() }?.let { "$title $it" }
        ?: "URL 直达集"
    val animeId = negativeStableId(inputUrl)
    val episodeNumber = Regex("\\d{1,4}")
        .find(episodeLabel)
        ?.value
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: 1

    val anime = DownloadAnimeCandidate(
        animeId = animeId,
        title = title,
        episodeCount = 1,
        imageUrl = metadata?.posterUrl.orEmpty(),
        sourceLabel = source,
        year = metadata?.year.orEmpty(),
        episodeLabel = episodeLabel,
        directUrl = inputUrl,
        resolvedEpisodeTitle = episodeTitle
    )
    val episode = buildManualUrlEpisodeCandidate(anime) ?: DownloadEpisodeCandidate(
        episodeId = animeId - 1L,
        episodeNumber = episodeNumber,
        title = episodeTitle,
        source = source,
        directUrl = inputUrl,
        posterUrl = metadata?.posterUrl.orEmpty(),
        year = metadata?.year.orEmpty(),
        resolvedEpisodeLabel = episodeLabel
    )
    return ManualUrlDanmuSelection(
        anime = anime,
        episode = episode,
        metadataTrace = metadataTrace
    )
}

internal fun buildManualUrlEpisodeCandidate(anime: DownloadAnimeCandidate): DownloadEpisodeCandidate? {
    val directUrl = anime.directUrl.takeIf { it.isNotBlank() } ?: return null
    val episodeNumber = Regex("\\d{1,4}")
        .find(anime.episodeLabel)
        ?.value
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: 1
    val title = anime.resolvedEpisodeTitle.ifBlank {
        anime.episodeLabel.takeIf { it.isNotBlank() }?.let { "${anime.title} $it" } ?: "URL 直达集"
    }
    return DownloadEpisodeCandidate(
        episodeId = anime.animeId - 1L,
        episodeNumber = episodeNumber,
        title = title,
        source = anime.sourceLabel.ifBlank { "URL" },
        directUrl = directUrl,
        posterUrl = anime.imageUrl,
        year = anime.year,
        resolvedEpisodeLabel = anime.episodeLabel
    )
}

private fun negativeStableId(value: String): Long {
    val positive = value.hashCode().toLong().let { if (it < 0) -it else it }.coerceAtLeast(1L)
    return -positive
}
