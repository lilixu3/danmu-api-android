package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRevision

internal object CoreRevisionSearch {
    private val githubLoginPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
    private val commitShaPattern = Regex("[a-fA-F0-9]{7,40}")

    fun query(repo: String, keyword: String): String =
        "${keyword.trim()} repo:${repo.trim()}".trim()

    fun authorQuery(keyword: String): CoreRevisionAuthorQuery? {
        val value = keyword.trim()
        val explicitValue = when {
            value.startsWith("author:", ignoreCase = true) -> value.substringAfter(':').trim()
            value.startsWith('@') -> value.drop(1).trim()
            else -> null
        }
        if (explicitValue != null) {
            return explicitValue.takeIf(githubLoginPattern::matches)?.let {
                CoreRevisionAuthorQuery(login = it, explicit = true)
            }
        }
        if (commitShaPattern.matches(value) || !githubLoginPattern.matches(value)) return null
        return CoreRevisionAuthorQuery(login = value, explicit = false)
    }

    fun hasNextPage(
        linkHeader: String?,
        page: Int,
        pageSize: Int,
        totalCount: Int,
        receivedCount: Int
    ): Boolean {
        if (linkHeader?.contains("rel=\"next\"") == true) return true
        if (totalCount > 0) return page * pageSize < totalCount
        return receivedCount == pageSize
    }

    fun hasNextHistoryPage(
        linkHeader: String?,
        receivedCount: Int,
        pageSize: Int
    ): Boolean = linkHeader?.contains("rel=\"next\"") == true || receivedCount == pageSize

    fun matches(revision: CoreRevision, keyword: String): Boolean {
        val needle = keyword.trim()
        if (needle.isBlank()) return true
        return sequenceOf(
            revision.commitSha,
            revision.shortSha,
            revision.title,
            revision.message,
            revision.author
        ).any { value -> value.contains(needle, ignoreCase = true) }
    }
}

internal data class CoreRevisionAuthorQuery(
    val login: String,
    val explicit: Boolean
)
