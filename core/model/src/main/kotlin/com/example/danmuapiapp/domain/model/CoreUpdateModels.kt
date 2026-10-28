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

/** 更新确认和实际安装使用同一提交，避免确认后分支移动导致 PR 被意外丢弃。 */
data class CoreUpdatePlan(
    val variant: ApiVariant,
    val runMode: RunMode,
    val repo: String,
    val branch: String,
    val targetCommitSha: String,
    val versionLabel: String,
    val release: GithubRelease,
    val installedSourceFingerprint: String,
    val pullRequestNumbers: List<Int>,
    val notIncludedPullRequestNumbers: List<Int>,
    val unknownPullRequestNumbers: List<Int>,
    val confirmedPullRequestHeads: Map<Int, String> = emptyMap(),
    val installedPullRequestHeads: Map<Int, String> = emptyMap(),
    val pullRequestNotes: Map<Int, String> = emptyMap()
) {
    val requiresPullRequestChoice: Boolean
        get() = notIncludedPullRequestNumbers.isNotEmpty() || unknownPullRequestNumbers.isNotEmpty()

    val pullRequestsToReapply: List<Int>
        get() = pullRequestNumbers.filter { it in notIncludedPullRequestNumbers || it in unknownPullRequestNumbers }
}

data class CoreUpdateRequest(val plan: CoreUpdatePlan, val keepPullRequests: Boolean)
