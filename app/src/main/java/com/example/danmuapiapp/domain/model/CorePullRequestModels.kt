package com.example.danmuapiapp.domain.model

data class CorePullRequest(
    val number: Int,
    val state: String,
    val title: String,
    val body: String,
    val author: String,
    val htmlUrl: String,
    val draft: Boolean,
    val updatedAt: String,
    val headSha: String,
    val headLabel: String,
    val baseSha: String,
    val baseRef: String,
    val baseRepository: String,
    val authorAssociation: String = "",
    val firstContribution: PullRequestFirstContribution? = null,
    val mergedAt: String? = null,
    val closedAt: String? = null,
    val mergeable: Boolean? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val changedFiles: Int? = null
)

enum class PullRequestFirstContribution {
    Github,
    Repository
}

enum class CorePullRequestFilter {
    Open,
    Merged,
    Closed,
    All
}

enum class CorePullRequestStatus {
    Open,
    Merged,
    Closed
}

fun CorePullRequest.effectiveStatus(locallyMerged: Boolean = false): CorePullRequestStatus = when {
    locallyMerged || !mergedAt.isNullOrBlank() -> CorePullRequestStatus.Merged
    state.equals("open", ignoreCase = true) -> CorePullRequestStatus.Open
    else -> CorePullRequestStatus.Closed
}

fun CorePullRequest.matchesFilter(
    filter: CorePullRequestFilter,
    locallyMerged: Boolean = false
): Boolean = when (filter) {
    CorePullRequestFilter.Open -> effectiveStatus(locallyMerged) == CorePullRequestStatus.Open
    CorePullRequestFilter.Merged -> effectiveStatus(locallyMerged) == CorePullRequestStatus.Merged
    CorePullRequestFilter.Closed -> effectiveStatus(locallyMerged) == CorePullRequestStatus.Closed
    CorePullRequestFilter.All -> true
}

data class CorePullRequestPage(
    val repository: String,
    val baseBranch: String,
    val isPrivateRepository: Boolean,
    val items: List<CorePullRequest>,
    val filter: CorePullRequestFilter,
    val page: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean
)

data class CorePullRequestFilePage(
    val files: List<CoreRevisionFileChange>,
    val page: Int,
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean
)

data class PullRequestStackResult(
    val repository: String,
    val baseBranch: String,
    val baseCommitSha: String,
    val localMergeSha: String,
    val pullRequests: List<CorePullRequest>,
    val version: String?
)

class PullRequestMergeConflictException(
    val pullRequestNumber: Int,
    val conflictFiles: List<String>
) : Exception(
    buildString {
        append("PR #")
        append(pullRequestNumber)
        append(" 与当前组合存在冲突")
        if (conflictFiles.isNotEmpty()) {
            append("：")
            append(conflictFiles.joinToString("、"))
        }
    }
)
