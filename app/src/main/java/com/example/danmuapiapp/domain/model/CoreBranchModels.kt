package com.example.danmuapiapp.domain.model

import java.util.Locale

data class CoreBranchSelections(
    val stable: String = "",
    val dev: String = "",
    val custom: String = ""
) {
    fun resolve(variant: ApiVariant): String = when (variant) {
        ApiVariant.Stable -> stable
        ApiVariant.Dev -> dev
        ApiVariant.Custom -> custom
    }

    fun withSelection(variant: ApiVariant, branch: String): CoreBranchSelections {
        val normalized = normalizeGithubBranch(branch)
        return when (variant) {
            ApiVariant.Stable -> copy(stable = normalized)
            ApiVariant.Dev -> copy(dev = normalized)
            ApiVariant.Custom -> copy(custom = normalized)
        }
    }
}

data class CoreBranchCatalog(
    val variant: ApiVariant,
    val repo: String,
    val defaultBranch: String,
    val branches: List<String>
)

fun buildCoreBranchCatalog(
    variant: ApiVariant,
    repo: String,
    defaultBranch: String?,
    branches: List<String>
): CoreBranchCatalog {
    val normalizedBranches = branches
        .map(::normalizeGithubBranch)
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
    val normalizedDefault = normalizeGithubBranch(defaultBranch).ifBlank {
        normalizedBranches.firstOrNull { it.equals("main", ignoreCase = true) }
            ?: normalizedBranches.firstOrNull { it.equals("master", ignoreCase = true) }
            ?: normalizedBranches.firstOrNull().orEmpty()
    }
    val ordered = buildList {
        if (normalizedDefault.isNotBlank()) add(normalizedDefault)
        addAll(normalizedBranches)
    }.distinctBy { it.lowercase(Locale.ROOT) }

    return CoreBranchCatalog(
        variant = variant,
        repo = normalizeGithubRepo(repo),
        defaultBranch = normalizedDefault,
        branches = ordered
    )
}
