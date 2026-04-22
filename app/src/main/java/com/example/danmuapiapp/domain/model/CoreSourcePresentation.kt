package com.example.danmuapiapp.domain.model

fun formatCoreSourceText(repo: String, branch: String?): String {
    val normalizedRepo = repo.trim()
    val normalizedBranch = branch?.trim().orEmpty()
    return when {
        normalizedRepo.isBlank() -> ""
        normalizedBranch.isBlank() -> normalizedRepo
        else -> "$normalizedRepo · $normalizedBranch"
    }
}

fun resolveCoreVariantRepo(
    variant: ApiVariant,
    customRepo: String
): String {
    return if (variant == ApiVariant.Custom) {
        resolveCustomCoreSource(customRepo, "").repo
    } else {
        variant.repo
    }
}

fun resolveCoreVariantBranch(
    variant: ApiVariant,
    customRepo: String,
    customBranch: String
): String? {
    return if (variant == ApiVariant.Custom) {
        resolveCustomCoreSource(customRepo, customBranch)
            .takeIf { it.isValidRepo }
            ?.branch
    } else {
        null
    }
}

fun resolveCoreVariantSourceText(
    variant: ApiVariant,
    customRepo: String,
    customBranch: String
): String {
    return if (variant == ApiVariant.Custom) {
        resolveCustomCoreSource(customRepo, customBranch)
            .takeIf { it.isValidRepo }
            ?.sourceText
            .orEmpty()
    } else {
        formatCoreSourceText(variant.repo, null)
    }
}
