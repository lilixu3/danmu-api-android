package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreCandidateStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val target: File
        get() = File(folder.root, "work/danmu_api_stable")

    private fun candidate(
        targetPath: String = target.absolutePath,
        rootPath: String = "/data/adb/danmu_api_stable",
        backupPath: String = ""
    ) = PersistedCoreCandidate(
        variantKey = ApiVariant.Stable.key,
        runModeKey = RunMode.Normal.key,
        actionLabel = "更新",
        installedAtMs = 123L,
        targetDirPath = targetPath,
        rootDirPath = rootPath,
        backupDirPath = backupPath
    )

    @Test fun `target must match the canonical core directory`() {
        assertTrue(CoreCandidatePolicy.isPathValid(candidate(), target, "/data/adb/danmu_api_stable"))
        assertFalse(
            CoreCandidatePolicy.isPathValid(
                candidate(targetPath = File(folder.root, "other/danmu_api_stable").absolutePath),
                target,
                "/data/adb/danmu_api_stable"
            )
        )
    }

    @Test fun `root path and backup must stay in the owning core directory`() {
        val backup = File(target.parentFile, "danmu_api_stable.backup-100").apply { mkdirs() }
        assertTrue(
            CoreCandidatePolicy.isPathValid(
                candidate(backupPath = backup.absolutePath),
                target,
                "/data/adb/danmu_api_stable"
            )
        )
        assertFalse(
            CoreCandidatePolicy.isPathValid(
                candidate(backupPath = File(folder.root, "elsewhere/backup").absolutePath),
                target,
                "/data/adb/danmu_api_stable"
            )
        )
        assertFalse(
            CoreCandidatePolicy.isPathValid(
                candidate(rootPath = "/data/adb/danmu_api_dev"),
                target,
                "/data/adb/danmu_api_stable"
            )
        )
    }

    @Test fun `public state preserves installation identity and recovery availability`() {
        val state = CoreCandidatePolicy.toPublicState(candidate(backupPath = "/missing"), hasRecoveryPoint = false)!!
        assertEquals(ApiVariant.Stable, state.variant)
        assertEquals(RunMode.Normal, state.runMode)
        assertEquals("更新", state.actionLabel)
        assertEquals(123L, state.installedAtMs)
        assertFalse(state.hasRecoveryPoint)
        assertNull(
            CoreCandidatePolicy.toPublicState(
                candidate().copy(variantKey = "unknown"),
                hasRecoveryPoint = false
            )
        )
        assertNull(
            CoreCandidatePolicy.toPublicState(
                candidate().copy(runModeKey = "unknown"),
                hasRecoveryPoint = false
            )
        )
    }
}
