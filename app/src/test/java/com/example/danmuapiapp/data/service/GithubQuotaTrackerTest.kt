package com.example.danmuapiapp.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class GithubQuotaTrackerTest {
    private var now = 1000L
    private val tracker = GithubQuotaTracker { now }

    @Test fun unknownQuotaNeverBlocks() {
        assertNull(tracker.blocked())
        tracker.observe("core", -1, 2000)
        assertNull(tracker.blocked())
    }

    @Test fun zeroQuotaBlocksUntilExactResetAndNotForever() {
        tracker.observe("core", 0, 1060)
        assertEquals(1060L, tracker.blocked()?.resetEpochSeconds)
        now = 1059
        assertNotNull(tracker.blocked())
        now = 1060
        assertNull(tracker.blocked())
    }

    @Test fun tokenImmediatelyBypassesAnonymousExhaustionAndClearingRestoresIt() {
        tracker.observe("core", 0, 2000)
        assertNull(tracker.blocked(tokenConfigured = true))
        assertNotNull(tracker.blocked(tokenConfigured = false))
    }

    @Test fun searchQuotaDoesNotSuspendCoreChecks() {
        tracker.observe("search", 0, 1060)
        assertNull(tracker.blocked("core"))
        assertEquals("search", tracker.blocked("search")?.resource)
        tracker.observe("core", 50, 2000)
        assertNotNull(tracker.blocked("search"))
    }

    @Test fun coreExhaustionAlsoGuardsSearchOperations() {
        tracker.observe("core", 0, 2000)
        assertEquals("core", tracker.blocked("search")?.resource)
    }

    @Test fun positiveQuotaInNewWindowRestoresAccess() {
        tracker.observe("core", 0, 2000)
        tracker.observe("core", 60, 3000)
        assertNull(tracker.blocked())
        assertEquals(0L, tracker.resetFor("core"))
    }

    @Test fun olderParallelResponseDoesNotReopenExhaustedWindow() {
        tracker.observe("core", 0, 2000)
        tracker.observe("core", 5, 2000)
        assertNotNull(tracker.blocked())
    }

    @Test fun badResetUsesBoundedCooldown() {
        listOf(null, 0L, 999L, 1000L).forEach { reset ->
            tracker.observe("core", 0, reset)
            assertEquals(1060L, tracker.blocked()?.resetEpochSeconds)
        }
        now = 1060
        assertNull(tracker.blocked())
    }

    @Test fun expiredPersistedQuotaDoesNotStartAnotherCooldown() {
        tracker.restore("core", 999)
        assertNull(tracker.blocked())
        tracker.restore("core", 2000)
        assertNotNull(tracker.blocked())
        now = 2001
        assertNull(tracker.blocked())
    }

    @Test fun otherResourcesAndMalformedRemainingDoNotPoisonCoreBudget() {
        tracker.observe("graphql", 0, 2000)
        tracker.observe("core", -1, 2000)
        assertNull(tracker.blocked())
        tracker.observe("core", 0, 2000)
        tracker.observe("core", -1, 2000)
        assertNotNull(tracker.blocked())
    }

    @Test fun waitMessageUsesActualResetInsteadOfFixedFewMinutes() {
        assertTrue(GithubQuotaNotice(4600).message(now).contains("60 分钟"))
        assertTrue(GithubQuotaNotice(1061).message(now).contains("2 分钟"))
        assertTrue(GithubQuotaNotice(1060, "search").message(now).contains("搜索额度"))
        assertFalse(GithubQuotaNotice(900).message(now).contains("-"))
    }

    @Test fun automaticContextSurvivesDispatcherChangesAndDoesNotLeak() = runBlocking {
        assertFalse(GithubQuotaRequestContext.isAutomatic())
        GithubQuotaRequestContext.automatically {
            assertTrue(GithubQuotaRequestContext.isAutomatic())
            withContext(Dispatchers.IO) { assertTrue(GithubQuotaRequestContext.isAutomatic()) }
            async(Dispatchers.Default) { assertTrue(GithubQuotaRequestContext.isAutomatic()) }.await()
        }
        assertFalse(GithubQuotaRequestContext.isAutomatic())
    }

    @Test fun automaticFailureRestoresManualContext() = runBlocking {
        val result = runCatching {
            GithubQuotaRequestContext.automatically { throw IllegalStateException("fixture") }
        }
        assertTrue(result.isFailure)
        assertFalse(GithubQuotaRequestContext.isAutomatic())
    }
}
