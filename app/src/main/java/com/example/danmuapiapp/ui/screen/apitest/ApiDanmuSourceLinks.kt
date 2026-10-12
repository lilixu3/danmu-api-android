package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.domain.model.LogTagClassifier

internal fun detailSourceLinks(raw: String, fallback: String = ""): List<Pair<String, String>> =
    raw.split("$$$").mapNotNull { segment ->
        val value = segment.trim()
        if (value.isEmpty()) return@mapNotNull null
        val colon = value.indexOf(':')
        val prefix = if (colon > 0) value.substring(0, colon).trim().lowercase() else ""
        val isSource = prefix.isNotBlank() && prefix.none { it == '/' || it == '.' || it.isWhitespace() } &&
            prefix !in setOf("http", "https", "data", "blob", "file", "content")
        val target = if (isSource) value.substring(colon + 1).trim() else value
        if (target.isBlank()) return@mapNotNull null
        val source = if (isSource) prefix else fallback.trim()
        detailSourceName(source) to target
    }

internal fun detailSourceKey(raw: String): String = when (val key = raw.trim().lowercase()) {
    "qq" -> "tencent"
    "qiyi" -> "iqiyi"
    "imgo", "mgtv", "hunantv" -> "mango"
    "bili", "bilibili1", "b23" -> "bilibili"
    else -> key
}

internal fun detailSourceName(raw: String): String = LogTagClassifier.labelFor(detailSourceKey(raw))
