package com.example.danmuapiapp.desktop.core

import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller

enum class DesktopCoreVariant(
    val key: String,
    val label: String,
    val defaultRepository: String?,
    val defaultBranch: String,
) {
    Stable("stable", "稳定核心", DesktopCoreInstaller.STABLE_REPO, DesktopCoreInstaller.DEFAULT_BRANCH),
    Dev("dev", "开发核心", DesktopCoreInstaller.DEV_REPO, DesktopCoreInstaller.DEFAULT_BRANCH),
    Custom("custom", "自定义核心", null, DesktopCoreInstaller.DEFAULT_BRANCH);

    companion object {
        fun fromKey(raw: String?): DesktopCoreVariant = entries.firstOrNull { it.key == raw } ?: Stable
    }
}

data class CoreSourceMetadata(
    val repository: String,
    val branch: String,
    val commitSha: String?,
    val version: String?,
    val installedAtEpochMillis: Long,
)

data class DesktopCoreInfo(
    val variant: DesktopCoreVariant,
    val installed: Boolean,
    val valid: Boolean,
    val version: String?,
    val source: CoreSourceMetadata?,
    val diagnostic: String?,
)

data class CoreRepositoryMetadata(
    val repository: String,
    val defaultBranch: String,
    val description: String?,
    val isPrivate: Boolean,
)

data class CoreBranch(
    val name: String,
    val commitSha: String,
    val protected: Boolean,
)

data class CoreRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String?,
    val htmlUrl: String?,
    val zipballUrl: String?,
    val prerelease: Boolean,
    val draft: Boolean,
)

data class CoreRemoteSnapshot(
    val repository: String,
    val branch: String,
    val commitSha: String,
    val shortSha: String,
    val version: String?,
    val title: String,
    val message: String,
    val author: String?,
    val committedAt: String?,
)

data class CoreRevision(
    val commitSha: String,
    val title: String,
    val message: String,
    val author: String?,
    val committedAt: String?,
    val version: String?,
)

data class CoreRevisionPage(
    val revisions: List<CoreRevision>,
    val page: Int,
    val hasNextPage: Boolean,
)

enum class CoreDiffLineType { Context, Added, Removed, Header }

data class CoreDiffLine(
    val type: CoreDiffLineType,
    val content: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
)

data class CoreRevisionFileChange(
    val path: String,
    val previousPath: String?,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val lines: List<CoreDiffLine>,
    val patchUnavailableReason: String?,
)

data class CoreRevisionDetails(
    val revision: CoreRevision,
    val files: List<CoreRevisionFileChange>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
)

data class CoreUpdateComparison(
    val localSha: String,
    val remoteSha: String,
    val status: String,
    val aheadBy: Int,
    val behindBy: Int,
    val commits: List<CoreRevision>,
    val files: List<CoreRevisionFileChange>,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
)

data class CorePullRequest(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val author: String?,
    val baseBranch: String,
    val headBranch: String,
    val merged: Boolean,
    val additions: Int?,
    val deletions: Int?,
    val changedFiles: Int?,
    val htmlUrl: String?,
)

sealed class DesktopCoreOperationState {
    data object Idle : DesktopCoreOperationState()
    data class Running(
        val variant: DesktopCoreVariant,
        val action: String,
        val stage: String,
    ) : DesktopCoreOperationState()
    data class Completed(
        val variant: DesktopCoreVariant,
        val message: String,
    ) : DesktopCoreOperationState()
    data class Failed(
        val variant: DesktopCoreVariant?,
        val action: String,
        val cause: Throwable,
    ) : DesktopCoreOperationState()
}

enum class DesktopCoreAction {
    Install,
    Update,
    Reinstall,
    Delete,
    Rollback,
}
