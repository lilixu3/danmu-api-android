package com.example.danmuapiapp.domain.model

import java.net.URI

const val DEFAULT_CUSTOM_CORE_BRANCH = "main"

data class ParsedGithubRepoInput(
    val repo: String = "",
    val branch: String = ""
)

data class ResolvedCustomCoreSource(
    val repo: String = "",
    val branch: String = "",
    val sourceText: String = "",
    val suggestedBranch: String = "",
    val isConfigured: Boolean = false,
    val isValidRepo: Boolean = false
)

data class ResolvedCustomCoreConfig(
    val displayName: String = "",
    val repo: String = "",
    val branch: String = "",
    val sourceText: String = "",
    val isConfigured: Boolean = false,
    val isValidRepo: Boolean = false
)

fun parseGithubRepoInput(raw: String?): ParsedGithubRepoInput {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return ParsedGithubRepoInput()

    val path = extractGithubPath(trimmed)
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.trim()
        ?.trim('/')
        .orEmpty()
    if (path.isBlank()) return ParsedGithubRepoInput()

    val parts = path.split('/').filter { it.isNotBlank() }
    if (parts.isEmpty()) return ParsedGithubRepoInput()

    val repo = when {
        parts.size >= 2 -> "${parts[0]}/${parts[1].removeSuffix(".git")}"
        else -> parts[0].removeSuffix(".git")
    }
    val branch = if (parts.size >= 4 && parts[2].equals("tree", ignoreCase = true)) {
        normalizeGithubBranch(parts.drop(3).joinToString("/"))
    } else {
        ""
    }
    return ParsedGithubRepoInput(repo = repo, branch = branch)
}

fun normalizeGithubRepo(raw: String?): String {
    val normalized = parseGithubRepoInput(raw).repo.trim()
    if (normalized.isBlank()) return ""

    val parts = normalized
        .trim('/')
        .removeSuffix(".git")
        .split('/')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return when {
        parts.size >= 2 -> "${parts[0]}/${parts[1].removeSuffix(".git")}"
        parts.size == 1 -> parts[0].removeSuffix(".git")
        else -> ""
    }
}

fun normalizeGithubBranch(raw: String?): String {
    return raw?.trim()
        .orEmpty()
        .removePrefix("refs/heads/")
        .trim()
        .trim('/')
}

fun resolveCustomCoreSource(
    repoInput: String?,
    branchInput: String?
): ResolvedCustomCoreSource {
    val parsedRepo = parseGithubRepoInput(repoInput)
    val normalizedRepo = normalizeGithubRepo(repoInput)
    val isValidRepo = normalizedRepo.contains('/')
    val suggestedBranch = if (normalizedRepo.isBlank() || !isValidRepo) {
        ""
    } else {
        normalizeGithubBranch(parsedRepo.branch).ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
    }
    val normalizedBranch = if (normalizedRepo.isBlank()) {
        ""
    } else if (!isValidRepo) {
        normalizeGithubBranch(branchInput)
    } else {
        normalizeGithubBranch(branchInput).ifBlank { suggestedBranch }
    }
    return ResolvedCustomCoreSource(
        repo = normalizedRepo,
        branch = normalizedBranch,
        sourceText = if (isValidRepo) formatCoreSourceText(normalizedRepo, normalizedBranch) else "",
        suggestedBranch = suggestedBranch,
        isConfigured = normalizedRepo.isNotBlank(),
        isValidRepo = isValidRepo
    )
}

fun resolveRepoOnlyCustomCoreSource(
    repoInput: String?,
    currentBranch: String?
): ResolvedCustomCoreSource {
    val parsedRepo = parseGithubRepoInput(repoInput)
    val parsedBranch = normalizeGithubBranch(parsedRepo.branch)
    val preservedBranch = normalizeGithubBranch(currentBranch)
    val effectiveBranch = when {
        parsedBranch.isNotBlank() -> parsedBranch
        preservedBranch.isNotBlank() -> preservedBranch
        else -> ""
    }
    return resolveCustomCoreSource(
        repoInput = normalizeGithubRepo(repoInput),
        branchInput = effectiveBranch
    )
}

fun resolveCustomCoreConfig(
    displayName: String?,
    repoInput: String?,
    branchInput: String?
): ResolvedCustomCoreConfig {
    val source = resolveCustomCoreSource(repoInput, branchInput)
    return ResolvedCustomCoreConfig(
        displayName = displayName?.trim().orEmpty(),
        repo = source.repo,
        branch = source.branch,
        sourceText = source.sourceText,
        isConfigured = source.isConfigured,
        isValidRepo = source.isValidRepo
    )
}

private fun extractGithubPath(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        return if (host == "github.com" || host == "www.github.com") {
            uri.path.orEmpty()
        } else {
            null
        }
    }

    if (trimmed.startsWith("git@github.com:", ignoreCase = true)) {
        return trimmed.substringAfter(':')
    }

    val withoutGithubHost = trimmed
        .removePrefixIgnoreCase("github.com/")
        .removePrefixIgnoreCase("www.github.com/")
    if (withoutGithubHost != trimmed) {
        return withoutGithubHost
    }

    if (trimmed.contains("://")) return null

    val looksLikeHostPath = Regex("""^[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/|$)""").containsMatchIn(trimmed)
    return if (looksLikeHostPath) null else trimmed
}

private fun String.removePrefixIgnoreCase(prefix: String): String {
    return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
