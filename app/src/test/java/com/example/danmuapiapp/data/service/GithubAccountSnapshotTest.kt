package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.model.GithubAccountStatus
import org.junit.Assert.*
import org.junit.Test

class GithubAccountSnapshotTest {
    private val token = "test-credential-a"
    private val status = GithubAccountStatus(tokenConfigured = true, tokenValid = true,
        login = "account", coreLimit = 5000, coreRemaining = 4995, coreResetEpochSeconds = 2_000_000L)

    @Test fun `saved quota restores numbers without loading or storing raw credentials`() {
        val raw = GithubAccountSnapshotCodec.encode(token, status.copy(isLoading = true, error = "old error"), 1_000_000L)
        assertFalse(raw.contains(token))
        val restored = GithubAccountSnapshotCodec.decode(raw, token)!!
        assertEquals(status, restored.status)
        assertEquals(1_000_000L, restored.checkedAt)
    }

    @Test fun `different or removed credentials cannot reuse the old authenticated quota`() {
        val raw = GithubAccountSnapshotCodec.encode(token, status, 1_000_000L)
        assertNull(GithubAccountSnapshotCodec.decode(raw, "test-credential-b"))
        assertNull(GithubAccountSnapshotCodec.decode(raw, ""))
    }

    @Test fun `failed background request preserves known quota and reports the failure`() {
        val failed = GithubAccountStatus(tokenConfigured = true, error = "network unavailable")
        assertEquals(status.copy(error = failed.error), retainGithubAccountSnapshot(status, failed))
        // 身份验证因网络失败后返回的匿名额度，也不能把已有的已认证额度清空。
        assertEquals(status.copy(error = failed.error), retainGithubAccountSnapshot(status,
            failed.copy(coreLimit = 60, coreRemaining = 59)))
    }

    @Test fun `explicitly invalid credentials and fresh successful quota replace the old values`() {
        val invalid = GithubAccountStatus(tokenConfigured = true, tokenValid = false,
            coreLimit = 60, coreRemaining = 59, error = "invalid token")
        assertEquals(invalid, retainGithubAccountSnapshot(status, invalid))
        val fresh = status.copy(coreRemaining = 4990)
        assertEquals(fresh, retainGithubAccountSnapshot(status, fresh))
    }

    @Test fun `missing damaged and invalid quota snapshots are ignored`() {
        assertNull(GithubAccountSnapshotCodec.decode(null, token))
        assertNull(GithubAccountSnapshotCodec.decode("broken json", token))
        assertNull(GithubAccountSnapshotCodec.decode(GithubAccountSnapshotCodec.encode(token, status, 0), token))
        assertNull(GithubAccountSnapshotCodec.decode(GithubAccountSnapshotCodec.encode(token,
            status.copy(coreLimit = 0), 1_000_000L), token))
        assertNull(GithubAccountSnapshotCodec.decode(GithubAccountSnapshotCodec.encode(token,
            status.copy(coreRemaining = 5001), 1_000_000L), token))
    }
}
