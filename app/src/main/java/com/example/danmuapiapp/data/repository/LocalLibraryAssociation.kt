package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord

internal fun scopedLocalResourceKey(libraryId: String, resourceKey: String): String = "$libraryId\u0000$resourceKey"

internal fun reconcileLocalLibraryRecord(record: DanmuDownloadRecord, libraryId: String, resourceKey: String?,
    reconcile: Boolean, mediaType: String, importedAt: Long): DanmuDownloadRecord {
    require(libraryId.isNotBlank())
    if (resourceKey.isNullOrBlank()) {
        return if (reconcile) record.copy(localImports = record.localImports - libraryId) else record
    }
    return record.copy(localImportedAt = if (record.localImports[libraryId] == resourceKey) record.localImportedAt else importedAt,
        localResourceKey = resourceKey, localImports = record.localImports + (libraryId to resourceKey),
        mediaType = mediaType.takeIf { it == "movie" || it == "tv" } ?: record.mediaType)
}
