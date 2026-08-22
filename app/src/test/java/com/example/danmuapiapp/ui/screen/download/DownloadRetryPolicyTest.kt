package com.example.danmuapiapp.ui.screen.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test
    fun `stale http failures request chain rebuild`() {
        assertTrue(DownloadRetryPolicy.shouldRebuildChainForStaleFailure(404, "请求失败"))
        assertTrue(
            DownloadRetryPolicy.shouldRebuildChainForStaleFailure(
                null,
                "请求失败：episodeId 不存在"
            )
        )
    }

    @Test
    fun `known permanent failures do not retry`() {
        assertFalse(DownloadRetryPolicy.shouldRetryFailure(404, "HTTP 404"))
        assertFalse(DownloadRetryPolicy.shouldRetryFailure(null, "返回内容不是 XML"))
        assertFalse(DownloadRetryPolicy.shouldRetryFailure(null, "保存目录不可写"))
    }

    @Test
    fun `transport and upstream failures remain retryable with backoff`() {
        assertTrue(DownloadRetryPolicy.shouldRetryFailure(429, "HTTP 429"))
        assertTrue(DownloadRetryPolicy.shouldRetryFailure(503, "HTTP 503"))
        assertTrue(DownloadRetryPolicy.shouldRetryFailure(null, "connection reset by peer"))
        assertTrue(DownloadRetryPolicy.shouldTriggerBackoff(429, "HTTP 429"))
        assertTrue(DownloadRetryPolicy.shouldTriggerBackoff(null, "连接超时"))
    }

    @Test
    fun `unknown exceptions keep compatibility retry behavior`() {
        assertTrue(DownloadRetryPolicy.shouldRetryFailure(null, "未知下载错误"))
        assertFalse(DownloadRetryPolicy.shouldRetryFailure(401, "HTTP 401"))
    }
}
