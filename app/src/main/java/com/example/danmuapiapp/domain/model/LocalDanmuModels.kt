package com.example.danmuapiapp.domain.model

import java.io.File
import java.util.Locale

enum class LocalDanmuType(val wire: String, val label: String) {
    Tv("tv", "电视剧"),
    Movie("movie", "电影");

    companion object {
        fun fromWire(raw: String?): LocalDanmuType? {
            val value = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.wire == value }
        }
    }
}

data class LocalDanmuResource(
    val resourceKey: String,
    val videoId: String = "",
    val title: String,
    val year: Int,
    val type: String,
    val season: Int,
    val episode: Int? = null,
    val filename: String = "",
    val sizeBytes: Long = 0L,
    val format: String = "",
    val status: String = "ready",
    val count: Int = 0,
    val updatedAt: String = "",
    val matchKeys: List<String> = emptyList()
) {
    val typeLabel: String
        get() = LocalDanmuType.fromWire(type)?.label ?: type.ifBlank { "未知类型" }

    val episodeLabel: String
        get() = when {
            episode != null -> "第${episode}集"
            LocalDanmuType.fromWire(type) == LocalDanmuType.Movie -> "正片"
            else -> "全集"
        }
}

data class LocalDanmuGroup(
    val groupKey: String,
    val title: String,
    val year: Int?,
    val type: String,
    val season: Int,
    val episodeCount: Int,
    val count: Int,
    val sizeBytes: Long,
    val updatedAt: String,
    val episodes: List<LocalDanmuResource>
)

data class LocalDanmuSnapshot(
    val resources: List<LocalDanmuResource>,
    val groups: List<LocalDanmuGroup>
)

enum class LocalDanmuWritePermission {
    ReadOnly,
    AdminRequired,
    Writable
}

data class LocalDanmuUploadRequest(
    val title: String,
    val year: Int,
    val type: LocalDanmuType,
    val season: Int?,
    val episode: Int?,
    val filename: String,
    val stagedFile: File? = null,
    val sourceUri: String = "",
    val contentLength: Long? = null,
    val mimeType: String = ""
)

enum class LocalDanmuParseConfidence {
    High,
    Medium,
    Low
}

data class ResolvedLocalDanmuMetadata(
    val title: String,
    val year: Int?,
    val type: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String = "",
    val confidence: LocalDanmuParseConfidence,
    val notes: List<String> = emptyList(),
    val matchSource: String = ""
)

enum class LocalDanmuSourceKind {
    File,
    ZipEntry
}

data class LocalDanmuSourceRef(
    val resourceKey: String,
    val kind: LocalDanmuSourceKind,
    val uri: String,
    val entryName: String = "",
    val displayName: String = "",
    val format: String = "",
    val sizeBytes: Long = 0L,
    val updatedAt: Long = 0L
)
