package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRevisionVersionCacheTest {
    @Test
    fun `success caches by normalized repo and commit and remembers the winning path`() {
        val cache = CoreRevisionVersionCache()
        cache.recordSuccess("Owner/Core", "ABC123", "danmu_api/globals.js", "1.2.3")

        assertEquals("1.2.3", cache.cachedVersion(" owner/core ", "abc123"))
        assertEquals(
            listOf("danmu_api/globals.js") + CoreRevisionVersionResolver.globalsFilePaths()
                .filterNot { it == "danmu_api/globals.js" },
            cache.lookupPaths("OWNER/CORE")
        )
    }

    @Test
    fun `failures are suppressed only during the ttl`() {
        val cache = CoreRevisionVersionCache()
        cache.recordFailure("owner/core", "abc", now = 1_000L)

        assertTrue(cache.isFailureCoolingDown("OWNER/CORE", "ABC", now = 60_999L))
        assertFalse(cache.isFailureCoolingDown("owner/core", "abc", now = 61_000L))
        assertFalse(cache.isFailureCoolingDown("owner/core", "abc", now = 61_001L))
    }

    @Test
    fun `success clears an existing failure and rejects stale ttl checks`() {
        val cache = CoreRevisionVersionCache()
        cache.recordFailure("owner/core", "abc", now = 1_000L)
        cache.recordSuccess("owner/core", "abc", "globals.js", "2.0.0")

        assertFalse(cache.isFailureCoolingDown("owner/core", "abc", now = 1_001L))
        assertEquals("2.0.0", cache.cachedVersion("owner/core", "abc"))
    }

    @Test
    fun `cache clears versions and failures at the entry limit`() {
        val cache = CoreRevisionVersionCache(maxEntries = 2)
        cache.recordSuccess("owner/core", "one", "globals.js", "1.0.0")
        cache.recordFailure("owner/core", "failed", now = 1_000L)
        cache.recordSuccess("owner/core", "two", "globals.js", "2.0.0")
        cache.recordSuccess("owner/core", "three", "globals.js", "3.0.0")

        assertNull(cache.cachedVersion("owner/core", "one"))
        assertFalse(cache.isFailureCoolingDown("owner/core", "failed", now = 1_001L))
        assertEquals("3.0.0", cache.cachedVersion("owner/core", "three"))
    }

    @Test
    fun `commit shas only match case-insensitively after trimming`() {
        val cache = CoreRevisionVersionCache()
        cache.recordSuccess("owner/core", " ABC ", "globals.js", "9.9.9")

        assertEquals("9.9.9", cache.cachedVersion("owner/core", "abc"))
        assertNull(cache.cachedVersion("owner/core", "ab"))
    }

    @Test
    fun `lookup paths move an existing preferred path to the front`() {
        val cache = CoreRevisionVersionCache()
        cache.recordSuccess("owner/core", "abc", "globals.js", "1.0.0")

        assertEquals(
            listOf("globals.js") + CoreRevisionVersionResolver.globalsFilePaths()
                .filterNot { it == "globals.js" },
            cache.lookupPaths("owner/core")
        )
    }
}
