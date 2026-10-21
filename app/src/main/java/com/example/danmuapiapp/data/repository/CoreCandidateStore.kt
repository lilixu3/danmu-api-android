package com.example.danmuapiapp.data.repository

import android.content.SharedPreferences
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreCandidateState
import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

internal object CoreCandidatePolicy {
    fun isPathValid(
        candidate: PersistedCoreCandidate,
        expectedTargetDir: File,
        expectedRootDirPath: String
    ): Boolean {
        val target = runCatching { File(candidate.targetDirPath).canonicalFile }.getOrNull() ?: return false
        val expectedTarget = runCatching { expectedTargetDir.canonicalFile }.getOrNull() ?: return false
        if (target != expectedTarget || candidate.rootDirPath != expectedRootDirPath) return false
        if (candidate.backupDirPath.isBlank()) return true
        val backup = runCatching { File(candidate.backupDirPath).canonicalFile }.getOrNull() ?: return false
        return backup.parentFile == expectedTarget.parentFile &&
            backup.name.startsWith("${expectedTarget.name}.backup-")
    }

    fun toPublicState(
        candidate: PersistedCoreCandidate,
        hasRecoveryPoint: Boolean
    ): CoreCandidateState? {
        val variant = ApiVariant.entries.firstOrNull { it.key == candidate.variantKey } ?: return null
        val mode = RunMode.fromKey(candidate.runModeKey) ?: return null
        return CoreCandidateState(
            variant = variant,
            runMode = mode,
            actionLabel = candidate.actionLabel,
            installedAtMs = candidate.installedAtMs,
            hasRecoveryPoint = hasRecoveryPoint
        )
    }
}

internal class CoreCandidateStore(
    private val prefs: SharedPreferences,
    private val json: Json,
    private val isPathValid: (PersistedCoreCandidate) -> Boolean
) {
    companion object {
        internal const val PREFS_NAME = "core_candidate_recovery"
        internal const val CANDIDATE_KEY = "pending_candidate"
    }

    fun load(): PersistedCoreCandidate? {
        val raw = prefs.getString(CANDIDATE_KEY, null)?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<PersistedCoreCandidate>(raw) }
            .getOrNull()
            ?.takeIf(isPathValid)
    }

    fun publicState(candidate: PersistedCoreCandidate): CoreCandidateState? {
        return CoreCandidatePolicy.toPublicState(
            candidate = candidate,
            hasRecoveryPoint = candidate.backupDirPath.isNotBlank() && File(candidate.backupDirPath).isDirectory
        )
    }

    fun save(candidate: PersistedCoreCandidate) {
        val previous = load()
        if (previous?.backupDirPath?.isNotBlank() == true &&
            previous.backupDirPath != candidate.backupDirPath
        ) {
            runCatching { File(previous.backupDirPath).deleteRecursively() }
        }
        if (!prefs.edit().putString(CANDIDATE_KEY, json.encodeToString(candidate)).commit()) {
            throw IOException("无法保存核心启动观察记录")
        }
    }

    fun clear(candidate: PersistedCoreCandidate, deleteBackup: Boolean) {
        if (deleteBackup && candidate.backupDirPath.isNotBlank()) {
            runCatching { File(candidate.backupDirPath).deleteRecursively() }
        }
        prefs.edit().remove(CANDIDATE_KEY).commit()
    }
}
