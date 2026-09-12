package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuRequestRetryPolicyTest {

    @Test
    fun `retries network failures until the attempt limit`() {
        assertTrue(shouldRetryLocalDanmuRequest(LocalDanmuErrorKind.Network, attempt = 1, maxAttempts = 3))
        assertTrue(shouldRetryLocalDanmuRequest(LocalDanmuErrorKind.Network, attempt = 2, maxAttempts = 3))
        assertFalse(shouldRetryLocalDanmuRequest(LocalDanmuErrorKind.Network, attempt = 3, maxAttempts = 3))
    }

    @Test
    fun `does not retry business or state errors`() {
        val noRetry = listOf(
            LocalDanmuErrorKind.InvalidRequest,
            LocalDanmuErrorKind.Unauthorized,
            LocalDanmuErrorKind.Forbidden,
            LocalDanmuErrorKind.NotFound,
            LocalDanmuErrorKind.FileTooLarge,
            LocalDanmuErrorKind.Unsupported,
            LocalDanmuErrorKind.Server
        )
        noRetry.forEach { kind ->
            assertFalse("$kind 不应重试", shouldRetryLocalDanmuRequest(kind, attempt = 1, maxAttempts = 3))
        }
    }
}
