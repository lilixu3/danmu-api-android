package com.example.danmuapiapp.data.network

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GithubConnectionPoolCleanupTest {
    @Test fun tlsCloseRunsOffTheSelectionCallingThread() {
        val caller = Thread.currentThread()
        val finished = CountDownLatch(1)
        val actual = AtomicReference<Thread>()
        GithubConnectionPoolCleanup {
            actual.set(Thread.currentThread())
            finished.countDown()
        }.schedule()
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertNotSame(caller, actual.get())
        assertEquals("github-connection-cleanup", actual.get().name)
    }

    @Test fun slowSocketCloseDoesNotBlockSelectionCallback() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val cleanup = GithubConnectionPoolCleanup {
            started.countDown()
            try { release.await(3, TimeUnit.SECONDS) } finally { finished.countDown() }
        }
        val caller = Thread({ cleanup.schedule(); returned.countDown() }, "selection-ui-test")
        try {
            caller.start()
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue("selection waited for TLS shutdown", returned.await(300, TimeUnit.MILLISECONDS))
            assertEquals(1L, finished.count)
        } finally {
            release.countDown()
            caller.join(2000)
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun cleanupFailureDoesNotStopLaterRouteChanges() {
        val failed = CountDownLatch(1)
        val finished = CountDownLatch(1)
        GithubConnectionPoolCleanup { failed.countDown(); throw IllegalStateException("simulated socket close failure") }.schedule()
        GithubConnectionPoolCleanup { finished.countDown() }.schedule()
        assertTrue(failed.await(2, TimeUnit.SECONDS))
        assertTrue(finished.await(2, TimeUnit.SECONDS))
    }
}
