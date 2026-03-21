package com.example.danmuapiapp.data.service

import android.app.ActivityManager
import android.content.Context
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode
import java.io.File

data class NormalModeRuntimeProfile(
    val strategyMode: NormalModeStabilityMode,
    val lowRamDevice: Boolean,
    val slowStorageWorkDir: Boolean,
    val conservativeMode: Boolean,
    val workerEnabled: Boolean,
    val hotReloadEnabled: Boolean,
    val startupPrimaryNoticeMs: Long,
    val startupSecondaryNoticeMs: Long,
    val startupReadyTimeoutMs: Long,
    val startupTotalTimeoutMs: Long,
    val startupStateGraceMs: Long,
    val startupRecheckIntervalMs: Long
) {
    val startupStaleTimeoutMs: Long
        get() = startupTotalTimeoutMs + startupStateGraceMs
}

object NormalModeRuntimeProfiles {

    private const val LOW_RAM_TOTAL_MEM_THRESHOLD_BYTES = 3L * 1024L * 1024L * 1024L
    private const val LOW_RAM_MEMORY_CLASS_THRESHOLD_MB = 192

    fun current(context: Context): NormalModeRuntimeProfile {
        val appContext = context.applicationContext
        val strategyMode = NormalModeStabilityPrefs.get(appContext)
        val lowRamDevice = isLowRamDevice(appContext)
        val slowStorageWorkDir = isSlowStorageWorkDir(appContext)
        val conservativeMode = when (strategyMode) {
            NormalModeStabilityMode.Auto -> lowRamDevice || slowStorageWorkDir
            NormalModeStabilityMode.PreferStability -> true
            NormalModeStabilityMode.PreferPerformance -> false
        }

        return if (conservativeMode) {
            NormalModeRuntimeProfile(
                strategyMode = strategyMode,
                lowRamDevice = lowRamDevice,
                slowStorageWorkDir = slowStorageWorkDir,
                conservativeMode = true,
                workerEnabled = false,
                hotReloadEnabled = false,
                startupPrimaryNoticeMs = 20_000L,
                startupSecondaryNoticeMs = 35_000L,
                startupReadyTimeoutMs = 30_000L,
                startupTotalTimeoutMs = 75_000L,
                startupStateGraceMs = 10_000L,
                startupRecheckIntervalMs = 2_500L
            )
        } else {
            NormalModeRuntimeProfile(
                strategyMode = strategyMode,
                lowRamDevice = lowRamDevice,
                slowStorageWorkDir = slowStorageWorkDir,
                conservativeMode = false,
                workerEnabled = true,
                hotReloadEnabled = true,
                startupPrimaryNoticeMs = 15_000L,
                startupSecondaryNoticeMs = 20_000L,
                startupReadyTimeoutMs = 20_000L,
                startupTotalTimeoutMs = 35_000L,
                startupStateGraceMs = 5_000L,
                startupRecheckIntervalMs = 2_000L
            )
        }
    }

    fun isSlowStorageWorkDir(context: Context): Boolean {
        val appContext = context.applicationContext
        val normalBaseDir = canonicalFile(RuntimePaths.normalBaseDir(appContext))
        val internalBaseDir = canonicalFile(RuntimePaths.defaultBaseDir(appContext))
        if (isUnder(internalBaseDir, normalBaseDir)) return false
        return isLikelySharedStoragePath(normalBaseDir.path)
    }

    private fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        val totalMem = runCatching {
            am.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        }.getOrDefault(0L)
        return when {
            am.isLowRamDevice -> true
            totalMem > 0L -> totalMem < LOW_RAM_TOTAL_MEM_THRESHOLD_BYTES
            else -> am.memoryClass <= LOW_RAM_MEMORY_CLASS_THRESHOLD_MB
        }
    }

    private fun isLikelySharedStoragePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized == "/sdcard" ||
            normalized.startsWith("/sdcard/") ||
            normalized.startsWith("/storage/") ||
            normalized == "/mnt" ||
            normalized.startsWith("/mnt/")
    }

    private fun canonicalFile(file: File): File {
        return runCatching { file.canonicalFile }.getOrElse { file }
    }

    private fun isUnder(root: File, target: File): Boolean {
        var cursor: File? = target
        while (cursor != null) {
            if (cursor == root) return true
            cursor = cursor.parentFile
        }
        return false
    }
}
