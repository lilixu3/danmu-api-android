package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreCandidateState
import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * 启动失败候选核心：候选记录持久化、路径校验、确认与从恢复点回滚。
 * Repository 只提供目录位置、安装互斥与刷新回调。
 */
internal class CoreCandidateController(
    context: Context,
    private val locationOf: (ApiVariant, RunMode) -> CoreLocation,
    private val mutationMutex: Mutex,
    private val syncRoot: (File, String) -> Unit,
    private val refreshCoreInfo: () -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val store = CoreCandidateStore(
        context.getSharedPreferences(CoreCandidateStore.PREFS_NAME, Context.MODE_PRIVATE),
        json,
        ::isPathValid
    )
    private val _candidateState = MutableStateFlow(store.load()?.let(store::publicState))
    val candidateState: StateFlow<CoreCandidateState?> = _candidateState.asStateFlow()

    fun load(): PersistedCoreCandidate? = store.load()

    fun save(candidate: PersistedCoreCandidate) {
        store.save(candidate)
        _candidateState.value = store.publicState(candidate)
    }

    fun clear(candidate: PersistedCoreCandidate, deleteBackup: Boolean) {
        store.clear(candidate, deleteBackup)
        _candidateState.value = null
    }

    suspend fun confirmCandidateCore(
        variant: ApiVariant,
        runMode: RunMode,
        expectedInstalledAtMs: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            mutationMutex.withLock {
                val candidate = load() ?: return@withLock false
                if (candidate.variantKey != variant.key || candidate.runModeKey != runMode.key ||
                    candidate.installedAtMs != expectedInstalledAtMs
                ) {
                    return@withLock false
                }
                clear(candidate, deleteBackup = true)
                true
            }
        }
    }

    suspend fun restoreCandidateCore(
        variant: ApiVariant,
        runMode: RunMode,
        expectedInstalledAtMs: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            mutationMutex.withLock {
                val candidate = load() ?: return@withLock false
                if (candidate.variantKey != variant.key || candidate.runModeKey != runMode.key ||
                    candidate.installedAtMs != expectedInstalledAtMs
                ) {
                    return@withLock false
                }
                if (candidate.backupDirPath.isBlank()) {
                    clear(candidate, deleteBackup = false)
                    return@withLock false
                }
                val targetDir = File(candidate.targetDirPath)
                val backupDir = File(candidate.backupDirPath)
                if (!backupDir.isDirectory) {
                    clear(candidate, deleteBackup = false)
                    return@withLock false
                }
                if (targetDir.exists() && !targetDir.deleteRecursively()) {
                    throw IOException("无法移除启动失败的候选核心")
                }
                moveCoreDirectory(backupDir, targetDir)
                if (!NodeProjectManager.hasValidCore(targetDir)) {
                    throw IOException("恢复点中的核心文件不完整")
                }
                if (runMode != RunMode.Normal) {
                    syncRoot(targetDir, candidate.rootDirPath)
                }
                clear(candidate, deleteBackup = false)
                refreshCoreInfo()
                true
            }
        }
    }

    private fun isPathValid(candidate: PersistedCoreCandidate): Boolean {
        val variant = ApiVariant.entries.firstOrNull { it.key == candidate.variantKey } ?: return false
        val mode = RunMode.fromKey(candidate.runModeKey) ?: return false
        val expected = locationOf(variant, mode)
        return CoreCandidatePolicy.isPathValid(
            candidate = candidate,
            expectedTargetDir = expected.normalDir,
            expectedRootDirPath = expected.rootDirPath
        )
    }
}
