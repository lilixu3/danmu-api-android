package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorePullRequestStatusTest {
    @Test
    fun `remote open pull request remains open when it is not installed locally`() {
        val pullRequest = pullRequest(state = "open")

        assertEquals(CorePullRequestStatus.Open, pullRequest.effectiveStatus())
        assertTrue(pullRequest.matchesFilter(CorePullRequestFilter.Open))
        assertTrue(pullRequest.matchesFilter(CorePullRequestFilter.All))
        assertFalse(pullRequest.matchesFilter(CorePullRequestFilter.Merged))
    }

    @Test
    fun `locally merged pull request is removed from open and classified as merged`() {
        val pullRequest = pullRequest(state = "open")

        assertEquals(
            CorePullRequestStatus.Merged,
            pullRequest.effectiveStatus(locallyMerged = true)
        )
        assertFalse(
            pullRequest.matchesFilter(
                filter = CorePullRequestFilter.Open,
                locallyMerged = true
            )
        )
        assertTrue(
            pullRequest.matchesFilter(
                filter = CorePullRequestFilter.Merged,
                locallyMerged = true
            )
        )
    }

    @Test
    fun `github merged timestamp classifies a closed pull request as merged`() {
        val pullRequest = pullRequest(
            state = "closed",
            mergedAt = "2026-08-13T13:00:00Z"
        )

        assertEquals(CorePullRequestStatus.Merged, pullRequest.effectiveStatus())
        assertTrue(pullRequest.matchesFilter(CorePullRequestFilter.Merged))
        assertFalse(pullRequest.matchesFilter(CorePullRequestFilter.Closed))
    }

    @Test
    fun `closed pull request without merge timestamp remains closed`() {
        val pullRequest = pullRequest(state = "closed")

        assertEquals(CorePullRequestStatus.Closed, pullRequest.effectiveStatus())
        assertTrue(pullRequest.matchesFilter(CorePullRequestFilter.Closed))
        assertFalse(pullRequest.matchesFilter(CorePullRequestFilter.Merged))
    }

    private fun pullRequest(
        state: String,
        mergedAt: String? = null
    ) = CorePullRequest(
        number = 441,
        state = state,
        title = "Selective cache cleanup",
        body = "",
        author = "contributor",
        htmlUrl = "https://github.com/owner/repo/pull/441",
        draft = false,
        updatedAt = "2026-08-13T12:34:56Z",
        headSha = "1111111111111111111111111111111111111111",
        headLabel = "contributor:topic",
        baseSha = "2222222222222222222222222222222222222222",
        baseRef = "main",
        baseRepository = "owner/repo",
        mergedAt = mergedAt
    )
}
