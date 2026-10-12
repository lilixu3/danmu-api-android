package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.CoreBranchSnapshot
import com.example.danmuapiapp.domain.model.CoreInfo

internal fun shouldRefreshCoreWorkspace(
    hasRemoteInfo: Boolean,
    lastCheckedAtMillis: Long,
    nowMillis: Long,
    force: Boolean = false
): Boolean = force || !hasRemoteInfo || lastCheckedAtMillis <= 0L ||
    nowMillis < lastCheckedAtMillis || nowMillis - lastCheckedAtMillis > 10 * 60_000L

/** Reuse a successful update check only when its repository and branch match this card. */
internal fun coreWorkspaceUpdateSnapshot(
    info: CoreInfo?,
    repo: String,
    requestedBranch: String
): Pair<Long, CoreBranchSnapshot>? {
    if (info == null || info.updateCheckError != null || repo.isBlank()) return null
    val commit = info.remoteCommit?.takeIf { it.sha.isNotBlank() } ?: return null
    val branch = info.remoteBranch?.takeIf { it.isNotBlank() } ?: return null
    val checkedAt = info.updateCheckedAtEpochMillis?.takeIf { it > 0 } ?: return null
    if (!commit.htmlUrl.startsWith("https://github.com/$repo/commit/", ignoreCase = true)) return null
    if (requestedBranch.isNotBlank() && requestedBranch != branch) return null
    return checkedAt to CoreBranchSnapshot(repo, branch, commit)
}
