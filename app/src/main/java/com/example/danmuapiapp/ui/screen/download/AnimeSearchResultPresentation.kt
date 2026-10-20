package com.example.danmuapiapp.ui.screen.download

data class AnimeSearchResultPresentation(
    val title: String,
    val source: String,
    val episodeCountText: String,
    val idText: String
)

internal fun buildAnimeSearchResultPresentation(
    anime: DownloadAnimeCandidate
): AnimeSearchResultPresentation {
    val titleWithoutSource = anime.title
        .replace(Regex("\\s+from\\s+.+$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { anime.title.trim() }
    val source = anime.sourceLabel.ifBlank {
        extractSourceFromAnimeTitle(anime.title).ifBlank { "来源未知" }
    }
    val episodeCountText = if (anime.directUrl.isNotBlank() && anime.directEpisodes.isNotEmpty()) {
        "${anime.directEpisodes.size} 个播放条目" + anime.episodeLabel.takeIf { it.isNotBlank() }?.let { " · 当前 $it" }.orEmpty()
    } else if (anime.directUrl.isNotBlank() && anime.directMetadataNotice.isNotBlank()) {
        "当前链接" + anime.episodeLabel.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    } else if (anime.episodeLabel.isNotBlank()) {
        anime.episodeLabel
    } else if (anime.episodeCount > 0) {
        "${anime.episodeCount} 集"
    } else {
        "集数未知"
    }
    return AnimeSearchResultPresentation(
        title = titleWithoutSource,
        source = source,
        episodeCountText = episodeCountText,
        idText = if (anime.directUrl.isNotBlank()) {
            anime.year.takeIf { it.isNotBlank() }?.let { "$it · URL 直达" } ?: "URL 直达"
        } else {
            "ID：${anime.animeId}"
        }
    )
}
