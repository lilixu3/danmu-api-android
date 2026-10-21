package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDownloadPolicyTest {
    @Test
    fun `download failures keep the existing user-facing messages`() {
        assertEquals(
            "下载失败：GitHub 拒绝访问（HTTP 403），请检查 Token、仓库权限或代理线路",
            CoreDownloadPolicy.failureMessage(403)
        )
        assertEquals(
            "下载失败：仓库、分支或版本不存在（HTTP 404）",
            CoreDownloadPolicy.failureMessage(404)
        )
        assertEquals(
            "下载失败：GitHub 返回 HTTP 500",
            CoreDownloadPolicy.failureMessage(500)
        )
    }

    @Test
    fun `progress emits every interval or at content length`() {
        assertFalse(CoreDownloadPolicy.shouldEmitProgress(
            now = 1_299L,
            lastEmitAt = 1_000L,
            downloadedBytes = 10L,
            totalBytes = 100L
        ))
        assertTrue(CoreDownloadPolicy.shouldEmitProgress(
            now = 1_300L,
            lastEmitAt = 1_000L,
            downloadedBytes = 10L,
            totalBytes = 100L
        ))
        assertTrue(CoreDownloadPolicy.shouldEmitProgress(
            now = 1_001L,
            lastEmitAt = 1_000L,
            downloadedBytes = 100L,
            totalBytes = 100L
        ))
    }

    @Test
    fun `unknown length reports no progress and interval still applies`() {
        assertFalse(CoreDownloadPolicy.shouldEmitProgress(
            now = 1_001L,
            lastEmitAt = 1_000L,
            downloadedBytes = 100L,
            totalBytes = -1L
        ))
        assertNull(CoreDownloadPolicy.progress(downloadedBytes = 100L, totalBytes = -1L))
        assertNull(CoreDownloadPolicy.progress(downloadedBytes = 100L, totalBytes = 0L))
    }

    @Test
    fun `known length clamps progress and completion bytes`() {
        assertEquals(0.5f, CoreDownloadPolicy.progress(50L, 100L)!!, 0.0001f)
        assertEquals(1f, CoreDownloadPolicy.progress(150L, 100L)!!, 0.0001f)
        assertEquals(0f, CoreDownloadPolicy.progress(-10L, 100L)!!, 0.0001f)
        assertEquals(100L, CoreDownloadPolicy.completedBytes(100L, 60L))
        assertEquals(60L, CoreDownloadPolicy.completedBytes(-1L, 60L))
    }
}
