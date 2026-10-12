package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreBranchSnapshot
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import org.junit.Assert.*
import org.junit.Test

class CoreRemoteInfoCacheTest {
    private val snapshot = CoreBranchSnapshot("owner/core", "main",
        CoreRemoteCommit("abc1234", "最新提交", "提交说明", "author", "2026-10-11T12:00:00Z",
            "https://github.com/owner/core/commit/abc1234"))
    private val entry = CachedCoreRemoteInfo.from(ApiVariant.Stable, "", snapshot, 1_000_000L)

    @Test fun `cold startup restores full remote snapshot and its original detection time`() {
        val restored = CoreRemoteInfoCodec.decode(CoreRemoteInfoCodec.encode(listOf(entry))).single()
        assertEquals(snapshot, restored.snapshot())
        assertEquals(1_000_000L, restored.checkedAt)
        assertEquals("stable:owner/core:", restored.key)
    }

    @Test fun `variant repository and requested branch keep remote displays separate`() {
        val dev = entry.copy(variantKey = ApiVariant.Dev.key)
        val otherRepo = entry.copy(repo = "other/core")
        val otherBranch = entry.copy(requestedBranch = "develop", branch = "develop")
        val entries = listOf(entry, dev, otherRepo, otherBranch)
        val restored = CoreRemoteInfoCodec.decode(CoreRemoteInfoCodec.encode(entries)).associateBy { it.key }
        assertEquals(4, restored.size)
        entries.forEach { assertEquals(it, restored[it.key]) }
    }

    @Test fun `incomplete remote results cannot masquerade as detected information`() {
        val invalid = listOf(entry.copy(sha = ""), entry.copy(checkedAt = 0),
            entry.copy(branch = ""), entry.copy(repo = ""), entry.copy(variantKey = "unknown"))
        assertEquals(listOf(entry), CoreRemoteInfoCodec.decode(CoreRemoteInfoCodec.encode(invalid + entry)))
    }

    @Test fun `missing damaged and duplicate remote cache entries are handled safely`() {
        assertTrue(CoreRemoteInfoCodec.decode(null).isEmpty())
        assertTrue(CoreRemoteInfoCodec.decode("broken json").isEmpty())
        assertEquals(listOf(entry), CoreRemoteInfoCodec.decode(CoreRemoteInfoCodec.encode(listOf(entry, entry))))
    }
}
