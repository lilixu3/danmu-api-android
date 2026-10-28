package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import com.example.danmuapiapp.domain.repository.SettingsRepository

/** Persists the resolved source and clears stale update markers after installation. */
internal class CoreInstalledStateController(
    private val settingsRepository: SettingsRepository,
    private val resolveRepo: (ApiVariant) -> String,
    private val resolveBranch: (ApiVariant) -> String?,
    private val removeLatestVersion: (ApiVariant) -> Unit,
    private val updateCoreInfo: (List<CoreInfo>) -> Unit,
    private val readCoreInfo: () -> List<CoreInfo>,
    private val refreshCoreInfoAndAwait: suspend () -> Unit
) {
    fun persistResolvedSourceIfNeeded(variant: ApiVariant, metadata: CoreSourceMetadata?) {
        if (metadata == null) return
        val resolution = CoreSourcePersistencePolicy.resolve(
            variant = variant,
            metadata = metadata,
            currentRepo = resolveRepo(variant),
            currentBranch = resolveBranch(variant)
        ) ?: return
        if (!resolution.hasChange) return
        if (resolution.customSourceChange) {
            settingsRepository.saveCustomCoreSource(
                repoInput = resolution.repo,
                branchInput = resolution.branch
            )
        } else if (resolution.standardBranchChange) {
            settingsRepository.setCoreBranch(variant, resolution.branch)
        }
    }

    suspend fun refreshInstalledState(variant: ApiVariant) {
        removeLatestVersion(variant)
        updateCoreInfo(
            readCoreInfo().map { info ->
                if (info.variant == variant) {
                    info.copy(
                        hasVersionUpdate = false,
                        availableVersion = null,
                        remoteVersion = null,
                        remoteBranch = null,
                        remoteCommit = null,
                        updateRelation = CoreUpdateRelation.Unknown,
                        updatedPullRequestHeads = emptyMap(),
                        updateCheckError = null,
                        updateCheckedAtEpochMillis = null
                    )
                } else {
                    info
                }
            }
        )
        refreshCoreInfoAndAwait()
    }
}
