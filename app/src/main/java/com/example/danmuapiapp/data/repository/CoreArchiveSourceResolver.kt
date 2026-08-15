package com.example.danmuapiapp.data.repository

internal object CoreArchiveSourceResolver {
    private val commitSuffix = Regex("(?:^|[-_])([0-9a-fA-F]{7,40})$")

    fun inferCommitSha(entryName: String): String? {
        val rootDirectory = entryName
            .replace('\\', '/')
            .trim('/')
            .substringBefore('/')
            .trim()
        if (rootDirectory.isBlank()) return null
        return commitSuffix.find(rootDirectory)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
    }
}
