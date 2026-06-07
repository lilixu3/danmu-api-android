package com.example.danmuapiapp.domain.model

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
