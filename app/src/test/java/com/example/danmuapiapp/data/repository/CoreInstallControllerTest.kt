package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreInstallControllerTest {
    private fun pending(
        staging: File,
        target: File,
        rootPath: String = "/root/core",
        mode: RunMode = RunMode.Normal,
        actionLabel: String = "更新",
        type: PendingCoreMutationType = PendingCoreMutationType.ReplaceCore
    ) = PendingCoreMutation(
        operationId = 7L,
        repair = CoreDependencyRepairRequest(
            operationId = 7L,
            variant = ApiVariant.Stable,
            actionLabel = actionLabel,
            missingDependencies = emptyList()
        ),
        stagingDir = staging,
        targetDir = target,
        mode = mode,
        rootDirPath = rootPath,
        sourceMetadata = null,
        type = type
    )

    private fun controller(
        rootValid: Boolean = true,
        syncRoot: (File, String) -> Unit = { _, _ -> },
        previous: PersistedCoreCandidate? = null,
        saved: MutableList<PersistedCoreCandidate> = mutableListOf(),
        verifyStaged: (File) -> Unit = {},
        verifyInstalled: (File) -> Unit = {}
    ) = CoreInstallController(
        verifyStagedDependencies = verifyStaged,
        verifyInstalledDependencies = verifyInstalled,
        syncRoot = syncRoot,
        hasValidRootCore = { rootValid },
        loadPreviousCandidate = { previous },
        saveCandidate = { saved += it },
        now = { 1234L }
    )

    private fun coreDirectory(parent: File, name: String, marker: String): File =
        File(parent, name).apply {
            mkdirs()
            File(this, "worker.js").writeText(marker)
            File(this, "configs").mkdirs()
            File(this, "configs/globals.js").writeText("export const version = '1.0.0'")
            File(this, "package.json").writeText("{\"type\":\"module\"}")
        }

    @Test
    fun `successful replacement preserves recovery backup and saves candidate`() {
        val parent = Files.createTempDirectory("core-install").toFile()
        val target = coreDirectory(parent, "danmu_api_stable", "old")
        val staging = coreDirectory(parent, "danmu_api_stable.staging-1", "new")
        val saved = mutableListOf<PersistedCoreCandidate>()
        try {
            controller(saved = saved).applyStagedCore(pending(staging, target))

            assertFalse(staging.exists())
            assertEquals("new", File(target, "worker.js").readText())
            val candidate = saved.single()
            assertEquals(1234L, candidate.installedAtMs)
            assertEquals("更新", candidate.actionLabel)
            assertEquals(target.absolutePath, candidate.targetDirPath)
            assertEquals("/root/core", candidate.rootDirPath)
            assertTrue(candidate.backupDirPath.isNotBlank())
            assertTrue(File(candidate.backupDirPath).isDirectory)
            assertEquals("old", File(candidate.backupDirPath, "worker.js").readText())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `root validation failure restores the previous core`() {
        val parent = Files.createTempDirectory("core-install-root").toFile()
        val target = coreDirectory(parent, "danmu_api_stable", "old")
        val staging = coreDirectory(parent, "danmu_api_stable.staging-2", "new")
        try {
            val error = runCatching {
                controller(rootValid = false).applyStagedCore(
                    pending(staging, target, mode = RunMode.Root)
                )
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertEquals("Root 核心同步后仍缺少关键入口文件", error?.message)
            assertEquals("old", File(target, "worker.js").readText())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `root installation syncs and validates before saving`() {
        val parent = Files.createTempDirectory("core-install-sync").toFile()
        val target = coreDirectory(parent, "danmu_api_stable", "old")
        val staging = coreDirectory(parent, "danmu_api_stable.staging-3", "new")
        val syncs = mutableListOf<Pair<String, String>>()
        val saved = mutableListOf<PersistedCoreCandidate>()
        try {
            controller(
                syncRoot = { dir, path -> syncs += dir.absolutePath to path },
                saved = saved
            ).applyStagedCore(pending(staging, target, rootPath = "/root/stable", mode = RunMode.Root))

            assertEquals(listOf(target.absolutePath to "/root/stable"), syncs)
            assertEquals(1, saved.size)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `dependency repair replaces node modules and verifies both stages`() {
        val parent = Files.createTempDirectory("core-install-deps").toFile()
        val target = coreDirectory(parent, "danmu_api_stable", "old")
        File(target, "node_modules/old").mkdirs()
        val staging = coreDirectory(parent, "danmu_api_stable.dependency-repair-4", "new")
        File(staging, "node_modules/new").mkdirs()
        val verified = mutableListOf<String>()
        try {
            controller(
                verifyStaged = { verified += "staged:${it.name}" },
                verifyInstalled = { verified += "installed:${it.name}" }
            ).applyStagedDependencies(
                pending(staging, target, type = PendingCoreMutationType.RepairInstalledDependencies)
            )

            assertTrue(File(target, "node_modules/new").isDirectory)
            assertFalse(File(target, "node_modules/old").exists())
            assertEquals(
                listOf("staged:danmu_api_stable.dependency-repair-4", "installed:danmu_api_stable"),
                verified
            )
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `staging preparation validates core and records missing dependencies`() {
        val parent = Files.createTempDirectory("core-install-prepare").toFile()
        val staging = coreDirectory(parent, "danmu_api_stable.staging-5", "candidate")
        try {
            val pending = controller().prepareStagedCore(
                operationId = 9L,
                variant = ApiVariant.Dev,
                actionLabel = "安装",
                stagingDir = staging,
                targetDir = File(parent, "danmu_api_dev"),
                mode = RunMode.Normal,
                rootDirPath = "/root/dev",
                versionHint = "1.2.3",
                sourceMetadata = null,
                collectMissingDependencies = { listOf("express@4") }
            )

            assertEquals(9L, pending.operationId)
            assertEquals(ApiVariant.Dev, pending.repair.variant)
            assertEquals(listOf("express@4"), pending.repair.missingDependencies)
            assertEquals("1.2.3", pending.repair.candidateVersion)
            assertTrue(File(staging, "worker.js").isFile)
            assertTrue(File(staging, "package.json").isFile)
        } finally {
            parent.deleteRecursively()
        }
    }
}
