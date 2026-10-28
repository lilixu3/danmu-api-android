package com.example.danmuapiapp.data.repository

import java.io.File

internal object CoreArtifactPolicy {
    const val OPERATION_MARKER_FILE = ".danmuapiapp-operation"
    internal const val MARKED_STAGING_RETENTION_MS = 60L * 60L * 1000L
    internal const val LEGACY_STAGING_RETENTION_MS = 24L * 60L * 60L * 1000L

    fun isKnownArtifact(directoryName: String): Boolean {
        return directoryName.contains(".staging-") ||
            directoryName.contains(".dependency-repair-") ||
            directoryName.contains(".backup-") ||
            directoryName.startsWith(".node_modules-backup-")
    }

    fun markerFile(directory: File): File = File(directory, OPERATION_MARKER_FILE)

    fun tempDirectoryName(targetDir: File, suffix: String, now: Long): String {
        return "${targetDir.name}.${suffix}-$now"
    }

    fun markerCreatedAt(directory: File): Long {
        val marker = markerFile(directory)
        return marker.takeIf { it.isFile }
            ?.readLines()
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: directory.lastModified()
    }

    fun retentionMs(hasMarker: Boolean): Long {
        return if (hasMarker) MARKED_STAGING_RETENTION_MS else LEGACY_STAGING_RETENTION_MS
    }

    fun isExpired(createdAt: Long, now: Long, hasMarker: Boolean): Boolean {
        return createdAt > 0L && now - createdAt >= retentionMs(hasMarker)
    }
}
