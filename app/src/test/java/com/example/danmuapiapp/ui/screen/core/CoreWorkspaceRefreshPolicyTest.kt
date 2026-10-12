package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import org.junit.Assert.*
import org.junit.Test

class CoreWorkspaceRefreshPolicyTest {
    private val checkedAt = 1_000_000L
    private val checkedInfo = CoreInfo(
        variant = ApiVariant.Stable, version = "1.21.5", isInstalled = true,
        remoteBranch = "main", updateCheckedAtEpochMillis = checkedAt,
        remoteCommit = CoreRemoteCommit("abc1234", "Latest", htmlUrl = "https://github.com/owner/core/commit/abc1234")
    )

    @Test fun `reentering with fresh remote data skips refresh until strictly more than ten minutes`() {
        assertFalse(shouldRefreshCoreWorkspace(true, checkedAt, checkedAt + 60_000L))
        assertFalse(shouldRefreshCoreWorkspace(true, checkedAt, checkedAt + 600_000L))
        assertTrue(shouldRefreshCoreWorkspace(true, checkedAt, checkedAt + 600_001L))
    }

    @Test fun `missing remote data or missing successful timestamp refreshes on entry`() {
        assertTrue(shouldRefreshCoreWorkspace(false, checkedAt, checkedAt + 1L))
        assertTrue(shouldRefreshCoreWorkspace(true, 0L, checkedAt))
    }

    @Test fun `manual and pull refresh bypass cache age`() {
        assertTrue(shouldRefreshCoreWorkspace(true, checkedAt, checkedAt, force = true))
    }

    @Test fun `clock moved backwards cannot make cache valid forever`() {
        assertTrue(shouldRefreshCoreWorkspace(true, checkedAt, checkedAt - 1L))
    }

    @Test fun `successful update check can supply remote cache for selected source`() {
        val snapshot = coreWorkspaceUpdateSnapshot(checkedInfo, "owner/core", "main")!!
        assertEquals(checkedAt, snapshot.first)
        assertEquals("owner/core", snapshot.second.repo)
        assertEquals("main", snapshot.second.branch)
        assertEquals(checkedInfo.remoteCommit, snapshot.second.commit)
        assertFalse(shouldRefreshCoreWorkspace(true, snapshot.first, checkedAt + 1L))
        assertNotNull(coreWorkspaceUpdateSnapshot(checkedInfo, "OWNER/CORE", ""))
    }

    @Test fun `different repository or branch never reuses old remote data`() {
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo, "other/core", "main"))
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo, "owner/core", "dev"))
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo, "owner/core", "Main"))
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo.copy(remoteCommit = checkedInfo.remoteCommit!!.copy(
            htmlUrl = "https://github.com/owner/core-other/commit/abc1234"
        )), "owner/core", "main"))
    }

    @Test fun `failed update check does not advance last successful remote detection`() {
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo.copy(updateCheckError = "offline", updateCheckedAtEpochMillis = checkedAt + 600_001L), "owner/core", "main"))
        assertTrue(shouldRefreshCoreWorkspace(true, checkedAt, checkedAt + 600_001L))
    }

    @Test fun `incomplete remote update info stays missing`() {
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo.copy(remoteCommit = null), "owner/core", "main"))
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo.copy(remoteBranch = null), "owner/core", "main"))
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo.copy(updateCheckedAtEpochMillis = null), "owner/core", "main"))
        assertNull(coreWorkspaceUpdateSnapshot(checkedInfo.copy(remoteCommit = checkedInfo.remoteCommit!!.copy(sha = "")), "owner/core", "main"))
    }
}
