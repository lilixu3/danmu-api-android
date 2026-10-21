package com.example.danmuapiapp.data.service

import android.content.Context
import java.io.File

/**
 * Root 模式控制器：负责独立 Root 进程的启动与停止。
 *
 * 本对象只保留对外门面，实现分布在：
 * - [RootRuntimeProcessControl]：启动、停止与重启；
 * - [RootRuntimeHealth]：探活、归属判定与健康检查；
 * - [RootRuntimeSync]：运行目录、环境变量、核心与依赖同步；
 * - [RootRuntimeScripts]：Shell 脚本构建；
 * - [RootRuntimeLocation]：进程名、控制标记与 Root 目录位置。
 */
object RootRuntimeController {

    data class OpResult(
        val ok: Boolean,
        val message: String,
        val detail: String = "",
        val startOutcome: StartOutcome = StartOutcome.NotStarted
    )

    enum class StartOutcome {
        NotStarted,
        AlreadyRunning,
        StartedNewProcess
    }

    data class RecoveredRuntime(
        val pid: Int,
        val port: Int,
        val startedAtMs: Long?,
        val env: Map<String, String>,
        val apiReady: Boolean
    )

    // ---- 进程生命周期 ----

    /**
     * 兼容旧调用签名，避免增参后出现 NoSuchMethodError。
     */
    fun start(context: Context, port: Int, quickMode: Boolean): OpResult {
        return RootRuntimeProcessControl.start(context, port, quickMode)
    }

    fun start(
        context: Context,
        port: Int,
        quickMode: Boolean = false,
        skipSync: Boolean = false
    ): OpResult {
        return RootRuntimeProcessControl.start(context, port, quickMode, skipSync)
    }

    fun stop(context: Context, port: Int): OpResult {
        return RootRuntimeProcessControl.stop(context, port)
    }

    fun restart(context: Context, port: Int): OpResult {
        return RootRuntimeProcessControl.restart(context, port)
    }

    // ---- 探活与健康检查 ----

    fun isRunningFast(port: Int): Boolean {
        return RootRuntimeHealth.isRunningFast(port)
    }

    fun isRuntimeOwnedByAppPassive(context: Context, port: Int): Boolean {
        return RootRuntimeHealth.isRuntimeOwnedByAppPassive(context, port)
    }

    fun isRunning(context: Context, port: Int): Boolean {
        return RootRuntimeHealth.isRunning(context, port)
    }

    /**
     * Passive liveness hint used by UI/state reconciliation paths.
     *
     * This deliberately avoids RootShell/su so foreground resume, QS tile listening,
     * and periodic status reconciliation do not trigger Root manager authorization
     * notifications. Active start/stop paths still use [isRunning] when they need an
     * exact answer.
     */
    fun isProbablyRunning(context: Context, port: Int): Boolean {
        return RootRuntimeHealth.isProbablyRunning(context, port)
    }

    /** Cheap startup hint that never opens a socket or a Root shell. */
    fun hasPersistedRuntimeHint(context: Context): Boolean {
        return RootRuntimeHealth.hasPersistedRuntimeHint(context)
    }

    /** Requires an explicit Root operation; never used by periodic passive checks. */
    fun recoverRuntime(context: Context, preferredPort: Int): RecoveredRuntime? {
        return RootRuntimeHealth.recoverRuntime(context, preferredPort)
    }

    /** A same-home health reply can recover the mode without opening a Root shell. */
    fun recoverRuntimePassive(context: Context, port: Int): RecoveredRuntime? {
        return RootRuntimeHealth.recoverRuntimePassive(context, port)
    }

    fun getPid(context: Context): Int? = RootRuntimeHealth.getPid(context)

    fun getProcessStartedAtMs(context: Context): Long? {
        return RootRuntimeHealth.getProcessStartedAtMs(context)
    }

    fun getPidFileLastModified(context: Context): Long? {
        return RootRuntimeHealth.getPidFileLastModified(context)
    }

    internal fun discoverProcesses(context: Context): List<RootRuntimeRecovery.Process>? {
        return RootRuntimeHealth.discoverProcesses(context)
    }

    // ---- 目录与环境同步 ----

    fun syncRuntimeEnvOnly(context: Context): OpResult {
        return RootRuntimeSync.syncRuntimeEnvOnly(context)
    }

    fun ensureRootRuntimeReady(
        context: Context,
        refreshEnvWhenReady: Boolean = true
    ): OpResult {
        return RootRuntimeSync.ensureRootRuntimeReady(context, refreshEnvWhenReady)
    }

    fun syncWorkDirToRoot(context: Context): OpResult {
        return RootRuntimeSync.syncWorkDirToRoot(context)
    }

    fun syncCoreDirectoryFromNormal(sourceDir: File, destinationPath: String): OpResult {
        return RootRuntimeSync.syncCoreDirectoryFromNormal(sourceDir, destinationPath)
    }

    // ---- Shell 脚本构建（保留 internal 入口，供内部调用与测试断言） ----

    internal fun buildAtomicCoreSyncShell(
        sourcePath: String,
        destinationPath: String,
        operationToken: String
    ): String {
        return RootRuntimeScripts.buildAtomicCoreSyncShell(sourcePath, destinationPath, operationToken)
    }

    internal fun buildAtomicProjectBootstrapShell(
        sourcePath: String,
        destinationPath: String,
        operationToken: String
    ): String {
        return RootRuntimeScripts.buildAtomicProjectBootstrapShell(sourcePath, destinationPath, operationToken)
    }

    internal fun buildAtomicNodeModulesSyncShell(
        sourcePath: String,
        destinationPath: String,
        fingerprint: String,
        operationToken: String
    ): String {
        return RootRuntimeScripts.buildAtomicNodeModulesSyncShell(
            sourcePath = sourcePath,
            destinationPath = destinationPath,
            fingerprint = fingerprint,
            operationToken = operationToken
        )
    }

    internal fun buildClearRuntimeMarkersShell(pidPath: String, startedAtPath: String): String {
        return RootRuntimeScripts.buildClearRuntimeMarkersShell(pidPath, startedAtPath)
    }

    internal fun buildRootProjectIncrementalSyncShell(
        srcProjectPath: String,
        dstProjectPath: String,
        managedPaths: List<String> = listOf("main.js")
    ): String {
        return RootRuntimeScripts.buildRootProjectIncrementalSyncShell(
            srcProjectPath = srcProjectPath,
            dstProjectPath = dstProjectPath,
            managedPaths = managedPaths
        )
    }

    internal fun buildRootProjectPermissionNormalizeShell(
        rootProjectPath: String,
        fullScan: Boolean,
        managedPaths: List<String> = listOf("main.js")
    ): String {
        return RootRuntimeScripts.buildRootProjectPermissionNormalizeShell(
            rootProjectPath = rootProjectPath,
            fullScan = fullScan,
            managedPaths = managedPaths
        )
    }

    internal fun buildRootRuntimeDependencyProbeShell(
        rootProjectPath: String,
        variantKey: String
    ): String {
        return RootRuntimeScripts.buildRootRuntimeDependencyProbeShell(rootProjectPath, variantKey)
    }

    internal fun buildNodeModuleIntegrityProbeShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return RootRuntimeScripts.buildNodeModuleIntegrityProbeShell(srcNodeModulesVar, dstNodeModulesVar)
    }

    internal fun buildNodeModulePackageRepairShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return RootRuntimeScripts.buildNodeModulePackageRepairShell(srcNodeModulesVar, dstNodeModulesVar)
    }

    internal fun buildNodeModuleIntegrityVerifyShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return RootRuntimeScripts.buildNodeModuleIntegrityVerifyShell(srcNodeModulesVar, dstNodeModulesVar)
    }
}
