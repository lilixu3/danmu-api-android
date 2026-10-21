package com.example.danmuapiapp.domain.model

/**
 * Extracts the provider suffix from a display title such as "某动画 from bahamut".
 *
 * This is shared by UI presentation and downloaded-record recovery, so it must remain
 * independent from Compose and data-layer implementations.
 */
internal fun extractSourceFromAnimeTitle(raw: String): String {
    val matched = Regex("from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE).find(raw)
    return matched?.groupValues?.getOrNull(1)
        ?.trim()
        ?.trim('【', '】', '[', ']')
        .orEmpty()
}

/**
 * Produces the stable provider key used for matching records and episode candidates.
 */
internal fun canonicalSourceKey(raw: String): String {
    val key = raw.trim()
        .replace(Regex("^from\\s+", RegexOption.IGNORE_CASE), "")
        .trim()
        .lowercase()
    if (key.isBlank()) return "unknown"
    return when (key) {
        "qq", "tencent" -> "tencent"
        "bilibili", "bilibili1", "bili", "b23" -> "bilibili"
        "iqiyi", "qiyi" -> "iqiyi"
        "imgo", "mango", "mgtv", "hunantv" -> "imgo"
        "douyin", "xigua" -> "xigua"
        "dandanplay", "ddplay", "dandan" -> "dandan"
        "baha", "bahamut" -> "bahamut"
        "360kan", "360" -> "360"
        "ani", "animeko" -> "animeko"
        else -> key
    }
}
