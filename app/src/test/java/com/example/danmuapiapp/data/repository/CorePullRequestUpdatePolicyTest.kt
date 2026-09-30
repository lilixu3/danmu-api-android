package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import org.junit.Assert.*
import org.junit.Test

class CorePullRequestUpdatePolicyTest {
    private val installed = CoreSourceMetadata(
        commitSha = "a".repeat(40), versionLabel = "1.21.3", localMergeSha = "f".repeat(40),
        pullRequestNumbers = listOf(492, 500), pullRequestHeadShas = listOf("b".repeat(40), "c".repeat(40))
    )

    @Test fun `main new commit reports update after local PR merge with unchanged version`() {
        assertEquals(CoreUpdateRelation.Changed,
            CorePullRequestUpdatePolicy.branchRelation(installed.commitSha, "d".repeat(40)))
        assertEquals(CoreUpdateRelation.Identical,
            CorePullRequestUpdatePolicy.branchRelation(installed.commitSha, installed.commitSha))
        assertEquals(CoreUpdateRelation.Identical,
            CorePullRequestUpdatePolicy.branchRelation(installed.commitSha.take(7).uppercase(), installed.commitSha))
    }

    @Test fun `PR new head reports update even when main and version are unchanged`() {
        val changed = CorePullRequestUpdatePolicy.changedHeads(installed, mapOf(492 to "e".repeat(40), 500 to "c".repeat(40)))
        assertEquals(mapOf(492 to "e".repeat(40)), changed)
        assertTrue(CorePullRequestUpdatePolicy.relation(CoreUpdateRelation.Identical, changed).hasRemoteUpdate)
        assertTrue(CorePullRequestUpdatePolicy.relation(CoreUpdateRelation.LocalAhead, changed).hasRemoteUpdate)
    }

    @Test fun `unchanged PR heads do not create perpetual updates`() {
        val changed = CorePullRequestUpdatePolicy.changedHeads(installed, mapOf(492 to "B".repeat(40), 500 to "c".repeat(40)))
        assertTrue(changed.isEmpty())
        assertEquals(CoreUpdateRelation.Identical, CorePullRequestUpdatePolicy.relation(CoreUpdateRelation.Identical, changed))
    }

    @Test fun `refresh after remerge or direct update clears consumed PR updates`() {
        val remote = mapOf(492 to "e".repeat(40))
        val refreshed = installed.copy(pullRequestHeadShas = listOf("e".repeat(40), "c".repeat(40)))
        assertTrue(CorePullRequestUpdatePolicy.changedHeads(refreshed, remote).isEmpty())
        assertTrue(CorePullRequestUpdatePolicy.changedHeads(installed.copy(pullRequestNumbers = emptyList()), remote).isEmpty())
    }

    @Test fun `missing head metadata can recover without inventing a remote head`() {
        assertTrue(CorePullRequestUpdatePolicy.changedHeads(installed, emptyMap()).isEmpty())
        assertEquals(setOf(492), CorePullRequestUpdatePolicy.changedHeads(
            installed.copy(pullRequestHeadShas = emptyList()), mapOf(492 to "b".repeat(40))
        ).keys)
    }

    @Test fun `opening main comparison cannot erase an available PR update`() {
        val base = CoreUpdateComparisonParser.identical("owner/core", "main", CoreRemoteCommit(installed.commitSha, "base"))
        val comparison = CorePullRequestUpdatePolicy.comparison(base, mapOf(492 to "e".repeat(40)))
        assertTrue(comparison.relation.hasRemoteUpdate)
        assertTrue(comparison.summary.headline.contains("#492"))
        assertEquals(base.commits, comparison.commits)
        assertEquals(base.files, comparison.files)
    }
}
