package com.example.danmuapiapp.data.util

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WatcherRegistryTest {
    @Test fun stoppedCallbacksCannotRecreateObservers() {
        val disposed = AtomicInteger()
        val registry = WatcherRegistry<Int> { disposed.incrementAndGet() }
        assertTrue(registry.register("core") { 1 })
        assertFalse(registry.register("core") { error("duplicate") })
        registry.stop()
        assertTrue(registry.isStopped)
        assertFalse(registry.register("new-event") { error("stopped callback recreated watcher") })
        registry.remove("core")
        registry.stop()
        assertEquals(1, disposed.get())
    }

    @Test fun concurrentRegistrationRemovalAndStopDisposeEveryCreatedObserverOnce() {
        val created = AtomicInteger()
        val disposed = AtomicInteger()
        val registry = WatcherRegistry<Int> { disposed.incrementAndGet() }
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        try {
            repeat(200) { registry.register("initial-$it") { created.incrementAndGet() } }
            val jobs = (0 until 5).map { worker -> executor.submit {
                start.await()
                repeat(600) { index ->
                    registry.register("$worker-$index") { created.incrementAndGet() }
                    registry.remove("initial-$index")
                }
            } } + executor.submit { start.await(); registry.stop() }
            start.countDown()
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            registry.stop()
            assertEquals(created.get(), disposed.get())
            assertTrue(registry.isStopped)
        } finally { executor.shutdownNow() }
    }

    @Test fun disposalFailureDoesNotLeakTheOtherObservers() {
        val disposed = AtomicInteger()
        val registry = WatcherRegistry<Int> { disposed.incrementAndGet(); if (it == 1) error("observer failure") }
        registry.register("first") { 1 }
        registry.register("second") { 2 }
        registry.stop()
        assertEquals(2, disposed.get())
        assertTrue(registry.isStopped)
    }
}
