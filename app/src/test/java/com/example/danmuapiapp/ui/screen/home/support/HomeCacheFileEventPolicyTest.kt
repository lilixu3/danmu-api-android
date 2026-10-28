package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.screen.home.shouldHandleHomeCacheFileChange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCacheFileEventPolicyTest {
    @Test
    fun handlesRequestRecordFiles() {
        assertTrue(shouldHandleHomeCacheFileChange("reqrecords"))
        assertTrue(shouldHandleHomeCacheFileChange("/data/data/app/.cache/reqrecords"))
        assertTrue(shouldHandleHomeCacheFileChange("todayreqnum"))
        assertTrue(shouldHandleHomeCacheFileChange("  TODAYREQNUM  "))
    }

    @Test
    fun ignoresUnrelatedOrEmptyPaths() {
        assertFalse(shouldHandleHomeCacheFileChange(null))
        assertFalse(shouldHandleHomeCacheFileChange(""))
        assertFalse(shouldHandleHomeCacheFileChange("   "))
        assertFalse(shouldHandleHomeCacheFileChange("/data/data/app/.cache/reqrecords.tmp"))
        assertFalse(shouldHandleHomeCacheFileChange("/data/data/app/.cache/other"))
    }
}
