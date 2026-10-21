package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.RuntimeDependencyHealthChecker
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequiredException
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreOperationState
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeDependencyResumeAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 核心操作门与待处理变更：操作身份、候选交接、在线/离线依赖修复、应用或丢弃候选。
 * Repository 只提供状态流读写、来源与目录解析、安装事务和进度发布回调。
 */
internal class CorePendingMutationController(
    private val context: Context,
    private val operationController: CoreOperationController,
    private val readOperationState: () -> CoreOperationState,
    private val readPendingDependencyRepair: () -> CoreDependencyRepairRequest?,
    private val writePendingDependencyRepair: (CoreDependencyRepairRequest?) -> Unit,
    private val runtimeDependencyPackManager: RuntimeDependencyPackManager,
    private val installController: CoreInstallController,
    private val installedStateController: CoreInstalledStateController,
    private val mutationMutex: Mutex,
    private val verifyCoreDependencies: (File, String?) -> Unit,
    private val collectMissingCoreDependencies: (File) -> List<String>,
    private val currentRunMode: () -> RunMode,
    private val locationOf: (ApiVariant, RunMode) -> CoreLocation,
    private val createStagingDir: (File, String) -> File,
    private val writeOperationMarker: (File, Long) -> Unit,
    private val refreshCoreInfoAndAwait: suspend () -> Unit,
    private val publishProgress: (CoreDownloadProgress) -> Unit,
    private val clearProgress: () -> Unit,
    private val logWarning: (String, Throwable) -> Unit
) {
    private val operationLock = Any()

    @Volatile
    private var pendingMutation: PendingCoreMutation? = null

    fun beginOperation(variant: ApiVariant?, actionLabel: String): Long {
        synchronized(operationLock) {
            return operationController.begin(variant, actionLabel)
        }
    }

    fun markOperationAwaitingRepair(operationId: Long) {
        synchronized(operationLock) {
            operationController.markAwaitingRepair(operationId)
        }
    }

    fun claimPendingOperation(operationId: Long, actionLabel: String): PendingCoreMutation {
        synchronized(operationLock) {
            return operationController.claimPending(operationId, actionLabel, pendingMutation) {
                pendingMutation = null
                writePendingDependencyRepair(null)
            }
        }
    }

    fun finishOperation(operationId: Long) {
        synchronized(operationLock) {
            operationController.finish(operationId)
        }
    }

    /** 当前等待处理的候选任务 ID；没有候选时为 null。 */
    fun pendingOperationId(): Long? = pendingMutation?.operationId

    fun setPendingMutation(pending: PendingCoreMutation, publishDependencyRepair: Boolean = true) {
        synchronized(operationLock) {
            val current = readOperationState()
            if (current.operationId != pending.operationId || pendingMutation != null) {
                throw IOException("已有核心任务正在等待处理，不能覆盖")
            }
            pendingMutation = pending
            writePendingDependencyRepair(pending.repair.takeIf { publishDependencyRepair })
        }
    }

    fun clearPendingMutation(operationId: Long, deleteStaging: Boolean) {
        val removed = synchronized(operationLock) {
            val old = pendingMutation?.takeIf { it.operationId == operationId }
                ?: return@synchronized null
            pendingMutation = null
            writePendingDependencyRepair(null)
            old
        }
        if (deleteStaging && removed != null) {
            runCatching { removed.stagingDir.deleteRecursively() }
        }
    }

    suspend fun runOwnedOperation(
        variant: ApiVariant,
        actionLabel: String,
        block: suspend (Long) -> Unit
    ): Result<Unit> {
        val operationId = runCatching { beginOperation(variant, actionLabel) }
            .getOrElse { return Result.failure(it) }
        return try {
            block(operationId)
            finishOperation(operationId)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            clearPendingMutation(operationId, deleteStaging = true)
            finishOperation(operationId)
            throw cancelled
        } catch (required: CoreDependencyRepairRequiredException) {
            markOperationAwaitingRepair(operationId)
            Result.failure(required)
        } catch (error: Exception) {
            clearPendingMutation(operationId, deleteStaging = true)
            finishOperation(operationId)
            Result.failure(error)
        }
    }

    suspend fun repairPendingDependenciesOnline(operationId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pending = runCatching { claimPendingOperation(operationId, "修复依赖") }
                .getOrElse { return@withContext Result.failure(it) }
            reportProgress(
                variant = pending.repair.variant,
                actionLabel = "修复依赖",
                stageText = "正在检查签名运行时依赖",
                progress = null,
                downloadedBytes = 0L,
                totalBytes = -1L
            )
            try {
                val result = runtimeDependencyPackManager.installIfAvailable(
                    coreDir = pending.stagingDir,
                    variant = pending.repair.variant,
                    onProgress = { stage, progress, downloadedBytes, totalBytes ->
                        reportProgress(
                            variant = pending.repair.variant,
                            actionLabel = "修复依赖",
                            stageText = stage,
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        )
                    }
                )
                verifyCoreDependencies(pending.stagingDir, result.unavailableReason)
                markOperationAwaitingRepair(operationId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                markOperationAwaitingRepair(operationId)
                throw cancelled
            } catch (error: Exception) {
                markOperationAwaitingRepair(operationId)
                Result.failure(error)
            } finally {
                clearProgress()
            }
        }

    suspend fun repairPendingDependenciesFromArchive(
        operationId: Long,
        archiveUri: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pending = runCatching { claimPendingOperation(operationId, "导入依赖") }
                .getOrElse { return@withContext Result.failure(it) }
            val temporaryArchive = runCatching {
                File.createTempFile("runtime-import-", ".zip", context.cacheDir)
            }.getOrElse {
                markOperationAwaitingRepair(operationId)
                return@withContext Result.failure(it)
            }
            reportProgress(
                variant = pending.repair.variant,
                actionLabel = "导入依赖",
                stageText = "正在读取本地依赖包",
                progress = null,
                downloadedBytes = 0L,
                totalBytes = -1L
            )
            try {
                val input = context.contentResolver.openInputStream(archiveUri.toUri())
                    ?: throw IOException("无法读取选择的依赖压缩包")
                input.buffered().use { source ->
                    FileOutputStream(temporaryArchive).use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
                                throw IOException("依赖压缩包超过大小上限")
                            }
                            target.write(buffer, 0, read)
                        }
                    }
                }
                reportProgress(
                    variant = pending.repair.variant,
                    actionLabel = "导入依赖",
                    stageText = "正在校验本地依赖闭包",
                    progress = null,
                    downloadedBytes = temporaryArchive.length(),
                    totalBytes = temporaryArchive.length()
                )
                LocalRuntimeDependencyArchiveImporter.verifyAndInstall(
                    archive = temporaryArchive,
                    coreDir = pending.stagingDir,
                    runtimeNodeModulesDir = File(RuntimePaths.normalProjectDir(context), "node_modules")
                )
                verifyCoreDependencies(pending.stagingDir, null)
                markOperationAwaitingRepair(operationId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                markOperationAwaitingRepair(operationId)
                throw cancelled
            } catch (error: Exception) {
                markOperationAwaitingRepair(operationId)
                Result.failure(error)
            } finally {
                runCatching { temporaryArchive.delete() }
                clearProgress()
            }
        }

    suspend fun applyPendingMutation(operationId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pending = runCatching { claimPendingOperation(operationId, "应用依赖") }
                .getOrElse { return@withContext Result.failure(it) }
            try {
                mutationMutex.withLock {
                    when (pending.type) {
                        PendingCoreMutationType.ReplaceCore -> {
                            installController.applyStagedCore(pending)
                            installedStateController.persistResolvedSourceIfNeeded(
                                pending.repair.variant,
                                pending.sourceMetadata
                            )
                        }
                        PendingCoreMutationType.RepairInstalledDependencies -> {
                            installController.applyStagedDependencies(pending)
                        }
                    }
                }
                RuntimeDependencyHealthChecker.clearPendingIssue(context, pending.repair.variant)
                if (
                    pending.type == PendingCoreMutationType.ReplaceCore
                ) {
                    installedStateController.refreshInstalledState(pending.repair.variant)
                } else {
                    refreshCoreInfoAndAwait()
                }
                clearPendingMutation(operationId, deleteStaging = true)
                finishOperation(operationId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                markOperationAwaitingRepair(operationId)
                throw cancelled
            } catch (error: Exception) {
                clearPendingMutation(operationId, deleteStaging = true)
                finishOperation(operationId)
                Result.failure(error)
            }
        }

    suspend fun discardPendingMutation(operationId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { claimPendingOperation(operationId, "取消任务") }
                .fold(
                    onSuccess = {
                        clearPendingMutation(operationId, deleteStaging = true)
                        finishOperation(operationId)
                        Result.success(Unit)
                    },
                    onFailure = { Result.failure(it) }
                )
        }

    suspend fun prepareInstalledCoreDependencyRepair(
        variant: ApiVariant,
        origin: CoreDependencyRepairOrigin,
        resumeAction: RuntimeDependencyResumeAction,
        suspectedMissingPackage: String?
    ): CoreDependencyRepairRequest? = withContext(Dispatchers.IO) {
        val effectiveResumeAction = CoreDependencyRepairPolicy.effectiveResumeAction(origin, resumeAction)
        val mode = currentRunMode()
        val location = locationOf(variant, mode)
        val coreDir = location.normalDir
        if (!NodeProjectManager.hasValidCore(coreDir)) return@withContext null
        NodeProjectManager.writeRuntimeDependencyRequirements(coreDir)

        val projectDir = RuntimePaths.normalProjectDir(context)
        val detectedMissing = if (NodeProjectManager.hasProjectEntry(projectDir)) {
            collectMissingCoreDependencies(coreDir)
        } else {
            NodeProjectManager.collectMissingRuntimeDepsForCoreAgainstBundledRuntime(coreDir)
        }
        val suspectedRequirement = NodeProjectManager.runtimeDependencyRequirementForCore(
            coreDir = coreDir,
            packageName = suspectedMissingPackage
        )
        val missing = CoreDependencyRepairPolicy.collectMissingDependencies(
            detected = detectedMissing,
            suspectedRequirement = suspectedRequirement
        )
        if (missing.isEmpty()) {
            RuntimeDependencyHealthChecker.clearPendingIssue(context, variant)
            return@withContext null
        }

        synchronized(operationLock) {
            val existing = pendingMutation
            val reusable = CoreDependencyRepairPolicy.reusableRequest(
                existing = existing,
                variant = variant,
                missingDependencies = missing,
                effectiveResumeAction = effectiveResumeAction
            )
            if (reusable != null && existing != null) {
                if (existing.repair != reusable) {
                    pendingMutation = existing.copy(repair = reusable)
                    writePendingDependencyRepair(reusable)
                }
                return@withContext reusable
            }
        }

        val operationId = beginOperation(variant, "修复依赖")

        val stagingDir = createStagingDir(coreDir, "dependency-repair")
        writeOperationMarker(stagingDir, operationId)
        try {
            val packageJson = File(coreDir, "package.json")
            if (!packageJson.isFile) {
                throw IOException("当前核心缺少依赖清单，无法执行依赖修复")
            }
            packageJson.copyTo(File(stagingDir, "package.json"), overwrite = true)
            val request = CoreDependencyRepairRequest(
                operationId = operationId,
                variant = variant,
                actionLabel = CoreDependencyRepairPolicy.actionLabel(effectiveResumeAction),
                missingDependencies = missing,
                candidateVersion = NodeProjectManager.readCoreVersion(coreDir),
                onlineRepairSupported = RuntimeDependencyPackProtocol.supportsOnlineRepair(variant),
                origin = origin,
                resumeAction = effectiveResumeAction
            )
            setPendingMutation(
                PendingCoreMutation(
                    operationId = operationId,
                    repair = request,
                    stagingDir = stagingDir,
                    targetDir = coreDir,
                    mode = mode,
                    rootDirPath = location.rootDirPath,
                    sourceMetadata = null,
                    type = PendingCoreMutationType.RepairInstalledDependencies
                )
            )
            markOperationAwaitingRepair(operationId)
            request
        } catch (cancelled: CancellationException) {
            runCatching { stagingDir.deleteRecursively() }
            finishOperation(operationId)
            throw cancelled
        } catch (error: Exception) {
            runCatching { stagingDir.deleteRecursively() }
            finishOperation(operationId)
            throw error
        }
    }

    suspend fun restoreRecordedDependencyRepair() {
        val issue = RuntimeDependencyHealthChecker.readPendingIssue(context) ?: return
        if (readPendingDependencyRepair() != null) return
        runCatching {
            prepareInstalledCoreDependencyRepair(
                variant = issue.variant,
                origin = CoreDependencyRepairOrigin.RuntimeStart,
                resumeAction = RuntimeDependencyResumeAction.None,
                suspectedMissingPackage = issue.suspectedPackage
            )
        }.onFailure { error ->
            logWarning("恢复启动依赖修复提示失败", error)
        }
    }

    private fun reportProgress(
        variant: ApiVariant,
        actionLabel: String,
        stageText: String,
        progress: Float?,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        publishProgress(
            CoreDownloadProgress(
                inProgress = true,
                variant = variant,
                actionLabel = actionLabel,
                stageText = stageText,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
        )
    }
}
