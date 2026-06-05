package com.example.danmuapiapp.domain.model

data class AnimeCacheLink(
    val title: String = "",
    val name: String = "",
    val url: String = "",
)

data class AnimeCacheItem(
    val animeId: Long? = null,
    val source: String = "",
    val animeTitle: String = "",
    val imageUrl: String = "",
    val episodeCount: Int = 0,
    val episodes: Int = 0,
    val links: List<AnimeCacheLink> = emptyList(),
    val mergedChildren: List<AnimeCacheItem> = emptyList(),
    val isHiddenChild: Boolean = false,
)
