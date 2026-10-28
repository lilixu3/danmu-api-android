package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.GithubRelease
import com.example.danmuapiapp.domain.model.buildCoreBranchCatalog
import com.example.danmuapiapp.domain.model.normalizeGithubBranch

/** Resolves branch snapshots and download sources while callers own caching. */
internal class CoreRemoteBranchSourceController(
    private val remoteDataSource: CoreRemoteDataSource,
    private val readDefaultBranch: (String) -> String?,
    private val writeDefaultBranch: (String, String) -> Unit,
    private val readBranchSource: (String) -> CoreRemoteSource?,
    private val writeBranchSource: (String, CoreRemoteSource) -> Unit
) {
    fun resolveDefaultBranch(repo: String): String? {
        readDefaultBranch(repo)?.takeIf { it.isNotBlank() }?.let { return it }
        remoteDataSource.fetchDefaultBranch(repo)?.takeIf { it.isNotBlank() }?.let { branch ->
            writeDefaultBranch(repo, branch)
            return branch
        }
        return buildCoreBranchCatalog(
            variant = com.example.danmuapiapp.domain.model.ApiVariant.Custom,
            repo = repo,
            defaultBranch = null,
            branches = remoteDataSource.fetchBranchList(repo)
        ).defaultBranch.ifBlank { null }
    }

    fun fetchSnapshot(repo: String, branch: String): CoreRemoteCommit? {
        return remoteDataSource.fetchBranchHead(repo, branch)?.toRemoteCommit()
    }

    fun fetchSource(repo: String, branch: String): CoreRemoteSource? {
        val normalizedBranch = normalizeGithubBranch(branch)
        if (normalizedBranch.isBlank()) return null
        val directHead = remoteDataSource.fetchBranchHead(repo, normalizedBranch)
        val resolvedBranch = if (directHead != null) {
            normalizedBranch
        } else {
            resolveRemoteBranchName(repo, normalizedBranch) ?: return null
        }
        val head = directHead.takeIf { resolvedBranch == normalizedBranch }
            ?: remoteDataSource.fetchBranchHead(repo, resolvedBranch)
        val cacheKey = CoreRemoteBranchParsing.branchCacheKey(repo, resolvedBranch)
        readBranchSource(cacheKey)?.takeIf { cached ->
            head != null && cached.metadata?.commitSha?.let {
                CoreRemoteParsing.commitShasEquivalent(it, head.sha)
            } == true
        }?.let { return it }

        val versionLabel = remoteDataSource.fetchVersionFromGlobals(repo, listOf(resolvedBranch)).orEmpty()
        if (head == null) {
            if (versionLabel.isBlank()) return null
            val fallback = buildBranchRemoteFallbackPlan(repo, resolvedBranch, versionLabel)
            return CoreRemoteSource(
                release = GithubRelease(
                    tagName = fallback.tagName,
                    name = fallback.name,
                    body = "",
                    publishedAt = "",
                    zipballUrl = fallback.zipballUrl
                ),
                metadata = CoreSourceMetadata(
                    repo = repo,
                    branch = resolvedBranch,
                    commitSha = "",
                    commitPublishedAt = "",
                    versionLabel = fallback.versionLabel
                )
            ).also { writeBranchSource(cacheKey, it) }
        }

        val shortSha = head.sha.take(7)
        val branchTag = versionLabel.ifBlank { resolvedBranch }
        val branchName = buildString {
            append(resolvedBranch)
            if (shortSha.isNotBlank()) {
                append(" @ ")
                append(shortSha)
            }
        }
        return CoreRemoteSource(
            release = GithubRelease(
                tagName = branchTag,
                name = branchName,
                body = head.title,
                publishedAt = head.publishedAt,
                zipballUrl = remoteDataSource.buildBranchZipUrl(repo, resolvedBranch)
            ),
            metadata = CoreSourceMetadata(
                repo = repo,
                branch = resolvedBranch,
                commitSha = head.sha,
                commitPublishedAt = head.publishedAt,
                versionLabel = versionLabel
            ),
            remoteCommit = head.toRemoteCommit()
        ).also { writeBranchSource(cacheKey, it) }
    }

    fun resolveRemoteBranchName(repo: String, requestedBranch: String): String? {
        val normalized = normalizeGithubBranch(requestedBranch)
        if (normalized.isBlank()) return null

        if (!remoteDataSource.fetchVersionFromGlobals(repo, listOf(normalized)).isNullOrBlank()) {
            return normalized
        }

        val branches = remoteDataSource.fetchBranchList(repo)
        val direct = branches.firstOrNull { it.equals(normalized, ignoreCase = true) }
        if (direct != null) return direct

        val suffixMatches = branches.filter { branchName ->
            branchName.substringAfterLast('/').equals(normalized, ignoreCase = true) ||
                branchName.endsWith("/$normalized", ignoreCase = true)
        }
        return suffixMatches.singleOrNull()
    }

    private fun BranchHeadInfo.toRemoteCommit(): CoreRemoteCommit = CoreRemoteCommit(
        sha = sha,
        title = title,
        message = message,
        author = author,
        committedAt = publishedAt,
        htmlUrl = htmlUrl
    )
}
