package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.GithubRelease

/** Builds the persisted source metadata for install and rollback archives. */
internal object CoreInstallMetadataPolicy {
    fun buildReleaseMetadata(
        repo: String,
        branch: String?,
        release: GithubRelease,
        versionHint: String?
    ): CoreSourceMetadata? {
        if (repo.isBlank()) return null
        val commitSha = release.commitSha.ifBlank {
            CoreRemoteParsing.extractArchiveCommitSha(release.zipballUrl).orEmpty()
        }
        return metadata(repo, branch, release, versionHint, commitSha)
    }

    fun buildRollbackMetadata(
        repo: String,
        branch: String?,
        release: GithubRelease,
        versionHint: String?,
        resolveTagCommitSha: (String) -> String?
    ): CoreSourceMetadata? {
        if (repo.isBlank()) return null
        val archiveCommitSha = release.commitSha.ifBlank {
            CoreRemoteParsing.extractArchiveCommitSha(release.zipballUrl).orEmpty()
        }
        val commitSha = archiveCommitSha.ifBlank {
            release.tagName.takeIf { it.isNotBlank() }
                ?.let(resolveTagCommitSha)
                .orEmpty()
        }
        return metadata(repo, branch, release, versionHint, commitSha)
    }

    private fun metadata(
        repo: String,
        branch: String?,
        release: GithubRelease,
        versionHint: String?,
        commitSha: String
    ): CoreSourceMetadata = CoreSourceMetadata(
        repo = repo,
        branch = branch.orEmpty(),
        commitSha = commitSha,
        commitPublishedAt = release.publishedAt,
        versionLabel = release.version.ifBlank { versionHint.orEmpty() }
    )
}
