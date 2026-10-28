package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.normalizeGithubBranch
import com.example.danmuapiapp.domain.model.normalizeGithubRepo

/** Decides when a resolved archive source should be written back to settings. */
internal object CoreSourcePersistencePolicy {
    data class Resolution(
        val repo: String,
        val branch: String,
        val customSourceChange: Boolean,
        val standardBranchChange: Boolean
    ) {
        val hasChange: Boolean
            get() = customSourceChange || standardBranchChange
    }

    fun resolve(
        variant: ApiVariant,
        metadata: CoreSourceMetadata,
        currentRepo: String,
        currentBranch: String?
    ): Resolution? {
        val normalizedRepo = normalizeGithubRepo(metadata.repo)
        val normalizedBranch = normalizeGithubBranch(metadata.branch)
        if (normalizedRepo.isBlank() || normalizedBranch.isBlank()) return null
        val currentBranchValue = currentBranch.orEmpty()
        if (normalizedRepo.equals(currentRepo, ignoreCase = true) &&
            normalizedBranch.equals(currentBranchValue, ignoreCase = true)
        ) {
            return Resolution(normalizedRepo, normalizedBranch, false, false)
        }
        return if (variant == ApiVariant.Custom) {
            Resolution(normalizedRepo, normalizedBranch, customSourceChange = true, standardBranchChange = false)
        } else if (normalizedRepo.equals(currentRepo, ignoreCase = true)) {
            Resolution(normalizedRepo, normalizedBranch, customSourceChange = false, standardBranchChange = true)
        } else {
            null
        }
    }
}
