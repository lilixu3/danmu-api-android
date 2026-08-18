package com.example.danmuapiapp.domain.model

enum class CoreUpdateRelation {
    Unknown,
    Identical,
    Changed,
    RemoteAhead,
    LocalAhead,
    Diverged;

    val hasRemoteUpdate: Boolean
        get() = this == Changed || this == RemoteAhead || this == Diverged
}

data class CoreRemoteCommit(
    val sha: String,
    val title: String,
    val message: String = "",
    val author: String = "",
    val committedAt: String = "",
    val htmlUrl: String = ""
) {
    val shortSha: String
        get() = sha.take(7)
}

data class CoreUpdateSummary(
    val headline: String,
    val highlights: List<String> = emptyList(),
    val affectedAreas: List<String> = emptyList()
)

data class CoreUpdateComparison(
    val repo: String,
    val branch: String,
    val localCommitSha: String,
    val remoteCommit: CoreRemoteCommit,
    val relation: CoreUpdateRelation,
    val aheadBy: Int,
    val behindBy: Int,
    val totalCommits: Int,
    val commits: List<CoreRemoteCommit>,
    val files: List<CoreRevisionFileChange>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val summary: CoreUpdateSummary,
    val isTruncated: Boolean = false
)
