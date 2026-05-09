package com.example.danmuapiapp.data.repository

import java.net.URLEncoder

internal data class BranchRemoteFallbackPlan(
    val tagName: String,
    val name: String,
    val zipballUrl: String,
    val versionLabel: String
)

internal fun buildBranchRemoteFallbackPlan(
    repo: String,
    branch: String,
    versionLabel: String?
): BranchRemoteFallbackPlan {
    val normalizedRepo = repo.trim().trim('/')
    val normalizedBranch = normalizeGithubBranchName(branch)
    require(normalizedRepo.isNotBlank()) { "repo is blank" }
    require(normalizedBranch.isNotBlank()) { "branch is blank" }

    val label = versionLabel?.trim()?.takeIf { it.isNotBlank() } ?: normalizedBranch
    return BranchRemoteFallbackPlan(
        tagName = label,
        name = label,
        zipballUrl = buildGithubBranchArchiveZipUrl(normalizedRepo, normalizedBranch),
        versionLabel = label
    )
}

internal fun normalizeGithubBranchName(branch: String): String {
    return branch.trim()
        .removePrefix("refs/heads/")
        .trim()
        .trim('/')
}

internal fun buildGithubBranchArchiveZipUrl(repo: String, branch: String): String {
    val encodedBranchPath = branch.split('/')
        .joinToString("/") { segment -> encodeGithubPathSegment(segment) }
    return "https://github.com/$repo/archive/refs/heads/$encodedBranchPath.zip"
}

private fun encodeGithubPathSegment(raw: String): String {
    return URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")
}
