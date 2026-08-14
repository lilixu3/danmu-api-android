package com.example.danmuapiapp.domain.model

enum class CacheClearItem(val wireKey: String) {
    SearchCache("searchCache"),
    CommentCache("commentCache"),
    RequestHistory("requestHistory"),
    Animes("animes"),
    BangumiData("bangumiData"),
    EpisodeIds("episodeIds"),
    EpisodeNum("episodeNum"),
    LastSelectMap("lastSelectMap")
}

enum class CacheClearSupport {
    Selective,
    LegacyAllOnly,
    Unknown
}

data class CacheClearCapability(
    val support: CacheClearSupport = CacheClearSupport.Unknown
) {
    val supportsSelective: Boolean
        get() = support == CacheClearSupport.Selective
}

data class CacheClearResult(
    val clearedItems: Set<CacheClearItem>,
    val usedSelectiveProtocol: Boolean
)

data class CacheStats(
    val reqRecordsCount: Int = 0,
    val todayReqNum: Int = 0,
    val lastClearedAt: Long? = null,
    val isAvailable: Boolean = false,
    val recentEntries: List<CacheEntry> = emptyList(),
    val animeCacheCount: Int = 0,
    val mergedSourceCount: Int = 0,
    val episodeLinkCount: Int = 0
)

data class CacheEntry(
    val key: String = "",
    val type: String = "",
    val sizeBytes: Long = 0,
    val hitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val statusCode: Int? = null,
    val clientIp: String = "",
    val keyword: String = "",
    val requestUrl: String = "",
    val fileName: String = "",
    val paramsText: String = ""
)
