package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoreUpdateChoiceControllerTest {
    private fun plan(missing: List<Int> = listOf(492), unknown: List<Int> = emptyList()) = CoreUpdatePlan(
        variant = ApiVariant.Stable, runMode = RunMode.Root, repo = "owner/core", branch = "main",
        targetCommitSha = "a".repeat(40), versionLabel = "1.21.3",
        release = GithubRelease("", "", "", "", "https://example.invalid/core.zip"),
        installedSourceFingerprint = "installed-source", pullRequestNumbers = listOf(492, 500, 501),
        notIncludedPullRequestNumbers = missing, unknownPullRequestNumbers = unknown
    )

    @Test fun `missing PR waits and direct update keeps exactly confirmed target`() = runBlocking {
        val controller = CoreUpdateChoiceController()
        val expected = plan()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { controller.awaitChoice(expected) }
        assertFalse(waiting.isCompleted)
        assertEquals(expected, controller.pendingPlan)
        controller.choose(false)
        assertEquals(CoreUpdateRequest(expected, false), waiting.await())
        assertNull(controller.pendingPlan)
    }

    @Test fun `keep PR choice reapplies missing and unknown PRs in original order`() = runBlocking {
        val controller = CoreUpdateChoiceController()
        val expected = plan(missing = listOf(501), unknown = listOf(492))
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { controller.awaitChoice(expected) }
        controller.choose(true)
        val request = waiting.await()!!
        assertTrue(request.keepPullRequests)
        assertEquals(listOf(492, 501), request.plan.pullRequestsToReapply)
        assertFalse(500 in request.plan.pullRequestsToReapply)
    }

    @Test fun `unknown inclusion always requires an explicit choice`() = runBlocking {
        val controller = CoreUpdateChoiceController()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            controller.awaitChoice(plan(missing = emptyList(), unknown = listOf(492)))
        }
        assertNotNull(controller.pendingPlan)
        assertFalse(waiting.isCompleted)
        controller.dismiss()
        assertNull(waiting.await())
    }

    @Test fun `cancel and repeated taps cannot produce an update request`() = runBlocking {
        val controller = CoreUpdateChoiceController()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { controller.awaitChoice(plan()) }
        controller.dismiss()
        controller.choose(true)
        controller.dismiss()
        assertNull(waiting.await())
        assertNull(controller.pendingPlan)
    }

    @Test fun `cancelled coroutine removes stale prompt`() = runBlocking {
        val controller = CoreUpdateChoiceController()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { controller.awaitChoice(plan()) }
        waiting.cancelAndJoin()
        assertNull(controller.pendingPlan)
        controller.choose(true)
    }

    @Test fun `fully included PRs update normally without asking to merge again`() = runBlocking {
        val controller = CoreUpdateChoiceController()
        val expected = plan(missing = emptyList())
        assertEquals(CoreUpdateRequest(expected, false), controller.awaitChoice(expected))
        assertTrue(expected.pullRequestsToReapply.isEmpty())
        assertNull(controller.pendingPlan)
    }
}
