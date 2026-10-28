package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File
import java.io.IOException

internal class CoreInstallController(
    private val verifyStagedDependencies: (File) -> Unit,
    private val verifyInstalledDependencies: (File) -> Unit,
    private val syncRoot: (File, String) -> Unit,
    private val hasValidRootCore: (String) -> Boolean,
    private val loadPreviousCandidate: () -> PersistedCoreCandidate?,
    private val saveCandidate: (PersistedCoreCandidate) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    fun prepareStagedCore(
        operationId: Long,
        variant: ApiVariant,
        actionLabel: String,
        stagingDir: File,
        targetDir: File,
        mode: RunMode,
        rootDirPath: String,
        versionHint: String?,
        sourceMetadata: CoreSourceMetadata?,
        collectMissingDependencies: (File) -> List<String>
    ): PendingCoreMutation {
        NodeProjectManager.normalizeCoreLayout(stagingDir)
        NodeProjectManager.ensureCorePackageJson(stagingDir, versionHint)
        NodeProjectManager.writeRuntimeDependencyRequirements(stagingDir)
        CoreSourceMetadataStore.write(stagingDir, sourceMetadata)

        if (!NodeProjectManager.hasValidCore(stagingDir)) {
            throw IOException("核心文件不完整，缺少关键入口文件")
        }
        return PendingCoreMutation(
            operationId = operationId,
            repair = CoreDependencyRepairRequest(
                operationId = operationId,
                variant = variant,
                actionLabel = actionLabel,
                missingDependencies = collectMissingDependencies(stagingDir),
                candidateVersion = versionHint,
                onlineRepairSupported = RuntimeDependencyPackProtocol.supportsOnlineRepair(variant)
            ),
            stagingDir = stagingDir,
            targetDir = targetDir,
            mode = mode,
            rootDirPath = rootDirPath,
            sourceMetadata = sourceMetadata
        )
    }

    fun applyStagedCore(pending: PendingCoreMutation) {
        var backupDir: File? = null
        var targetReplaced = false
        val previousCandidate = loadPreviousCandidate()
        try {
            CoreStagingPolicy.clearOperationMarker(pending.stagingDir)
            verifyStagedDependencies(pending.stagingDir)
            if (!NodeProjectManager.hasValidCore(pending.stagingDir)) {
                throw IOException("候选核心文件不完整，缺少关键入口文件")
            }
            backupDir = replaceCoreDirectory(pending.targetDir, pending.stagingDir)
            targetReplaced = true
            if (pending.mode != RunMode.Normal) {
                syncRoot(pending.targetDir, pending.rootDirPath)
                if (!hasValidRootCore(pending.rootDirPath)) {
                    throw IOException("Root 核心同步后仍缺少关键入口文件")
                }
            }
            val recoveryPath = CoreCandidateRecoveryPolicy.selectRecoveryPath(
                targetPath = pending.targetDir.absolutePath,
                replacementBackupPath = backupDir?.absolutePath,
                previousTargetPath = previousCandidate?.targetDirPath,
                previousRecoveryPath = previousCandidate?.backupDirPath,
                previousRecoveryAvailable = previousCandidate?.backupDirPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it).isDirectory } == true
            )
            val recoveryDir = recoveryPath?.let(::File)
            if (backupDir != null && backupDir != recoveryDir) {
                runCatching { backupDir.deleteRecursively() }
            }
            saveCandidate(
                PersistedCoreCandidate(
                    variantKey = pending.repair.variant.key,
                    runModeKey = pending.mode.key,
                    actionLabel = pending.repair.actionLabel,
                    installedAtMs = now(),
                    targetDirPath = pending.targetDir.absolutePath,
                    rootDirPath = pending.rootDirPath,
                    backupDirPath = recoveryDir?.absolutePath.orEmpty()
                )
            )
        } catch (error: Exception) {
            if (targetReplaced) {
                runCatching { restoreCoreDirectory(pending.targetDir, backupDir) }
                if (pending.mode != RunMode.Normal && pending.targetDir.exists()) {
                    runCatching { syncRoot(pending.targetDir, pending.rootDirPath) }
                }
            }
            throw error
        }
    }

    fun applyStagedDependencies(pending: PendingCoreMutation) {
        verifyStagedDependencies(pending.stagingDir)
        if (!NodeProjectManager.hasValidCore(pending.targetDir)) {
            throw IOException("当前工作目录中的核心已失效，请重新下载核心")
        }
        replaceInstalledCoreDependencies(
            stagingCoreDir = pending.stagingDir,
            installedCoreDir = pending.targetDir,
            afterInstall = {
                if (pending.mode != RunMode.Normal) {
                    syncRoot(pending.targetDir, pending.rootDirPath)
                }
            },
            verifyInstalled = { verifyInstalledDependencies(pending.targetDir) }
        )
    }
}
