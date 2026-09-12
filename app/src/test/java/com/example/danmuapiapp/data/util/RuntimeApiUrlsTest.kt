package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeApiUrlsTest {

    @Test
    fun `builds bare local base url`() {
        assertEquals("http://127.0.0.1:9321", RuntimeApiUrls.local(9321))
    }

    @Test
    fun `accepts token with or without leading slash`() {
        assertEquals(
            "http://127.0.0.1:9321/danmu/api/cache/clear",
            RuntimeApiUrls.local(9321, "danmu", "api/cache/clear")
        )
        assertEquals(
            "http://127.0.0.1:9321/danmu/api/cache/clear",
            RuntimeApiUrls.local(9321, "/danmu", "/api/cache/clear")
        )
    }

    @Test
    fun `keeps the previous bare-path behaviour when no token is present`() {
        assertEquals(
            "http://127.0.0.1:9321/api/reqrecords",
            RuntimeApiUrls.local(9321, "", "api/reqrecords")
        )
        assertEquals(
            "http://127.0.0.1:9321/__health",
            RuntimeApiUrls.local(9321, path = "__health")
        )
        assertEquals(
            "http://127.0.0.1:9321/token/__access-control",
            RuntimeApiUrls.local(9321, "/token", "__access-control")
        )
    }
}
