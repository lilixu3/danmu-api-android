package com.example.danmuapiapp.desktop.core

import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import java.io.File

/**
 * Read-only foreground checker for installed cores. It deliberately does not install or restart
 * anything; the caller owns the confirmation and execution UI.
 */
class CoreUpdateCoordinator(
    private val controller: DesktopRuntimeController,
    private val scriptDir: File,
) {
    fun installedVariants(): List<DesktopCoreVariant> = DesktopCoreVariant.entries.filter { variant ->
        val info = DesktopCoreInstaller.inspect(
            scriptDir = scriptDir,
            variant = variant,
            repository = variant.defaultRepository.orEmpty(),
            branch = variant.defaultBranch,
        )
        info.valid && info.source?.commitSha?.isNotBlank() == true
    }

    fun shouldCheck(variant: DesktopCoreVariant, nowMs: Long): Boolean {
        require(nowMs >= 0L) { "当前时间不能为负数" }
        val last = controller.settings.lastCoreUpdateCheckAt(variant.key)
        val intervalMs = controller.settings.coreUpdateCheckIntervalMinutes * 60_000L
        return last <= 0L || nowMs < last || nowMs - last >= intervalMs
    }

    fun check(variant: DesktopCoreVariant): CoreUpdateCheck? {
        val repository = variant.defaultRepository ?: return null
        val info = DesktopCoreInstaller.inspect(
            scriptDir = scriptDir,
            variant = variant,
            repository = repository,
            branch = variant.defaultBranch,
        )
        if (!info.valid || info.source?.commitSha.isNullOrBlank()) return null
        val remote = com.example.danmuapiapp.desktop.core.GithubCoreRemote(
            repository = repository,
            proxyId = controller.settings.githubProxyId,
            token = controller.settings.githubToken,
        ).branchHead(variant.defaultBranch)
        val result = compareCoreUpdate(info, remote)
        controller.settings.setLastCoreUpdateCheckAt(variant.key, System.currentTimeMillis())
        return result.takeIf { it.available }
    }
}
