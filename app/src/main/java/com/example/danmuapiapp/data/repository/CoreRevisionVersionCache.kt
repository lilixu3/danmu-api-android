package com.example.danmuapiapp.data.repository

import java.util.concurrent.ConcurrentHashMap

/** Caches commit-to-version lookups with bounded storage and a failure cooldown. */
internal class CoreRevisionVersionCache(
    private val failureTtlMs: Long = FAILURE_TTL_MS,
    private val maxEntries: Int = MAX_ENTRIES
) {
    companion object {
        const val FAILURE_TTL_MS = 60_000L
        const val MAX_ENTRIES = 2_048
    }

    private val versions = ConcurrentHashMap<String, String>()
    private val failures = ConcurrentHashMap<String, Long>()
    private val globalsPaths = ConcurrentHashMap<String, String>()

    fun cachedVersion(repo: String, commitSha: String): String? {
        return versions[cacheKey(repo, commitSha)]
    }

    fun isFailureCoolingDown(repo: String, commitSha: String, now: Long): Boolean {
        val key = cacheKey(repo, commitSha)
        val failedAt = failures[key] ?: return false
        if (now - failedAt < failureTtlMs) return true
        failures.remove(key, failedAt)
        return false
    }

    fun lookupPaths(repo: String): List<String> {
        val normalizedRepo = normalizeRepo(repo)
        val preferredPath = globalsPaths[normalizedRepo]
        return (listOfNotNull(preferredPath) + CoreRevisionVersionResolver.globalsFilePaths()).distinct()
    }

    fun recordSuccess(
        repo: String,
        commitSha: String,
        filePath: String,
        version: String
    ) {
        val key = cacheKey(repo, commitSha)
        if (versions.size >= maxEntries) {
            versions.clear()
            failures.clear()
        }
        globalsPaths[normalizeRepo(repo)] = filePath
        versions[key] = version
        failures.remove(key)
    }

    fun recordFailure(repo: String, commitSha: String, now: Long) {
        failures[cacheKey(repo, commitSha)] = now
    }

    private fun cacheKey(repo: String, commitSha: String): String {
        return "${normalizeRepo(repo)}:${commitSha.trim().lowercase()}"
    }

    private fun normalizeRepo(repo: String): String = repo.trim().lowercase()
}
