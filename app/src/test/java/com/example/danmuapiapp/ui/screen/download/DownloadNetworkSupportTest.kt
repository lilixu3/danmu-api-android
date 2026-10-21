package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.ui.screen.download.support.resolveDownloadApiBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadNetworkSupportTest {

    @Test fun `blank source base resolves to null`() {
        assertNull(resolveDownloadApiBaseUrl("", "tok"))
        assertNull(resolveDownloadApiBaseUrl("   ", "tok"))
    }

    @Test fun `missing scheme is completed and trailing slash trimmed`() {
        assertEquals("http://127.0.0.1:9321", resolveDownloadApiBaseUrl("127.0.0.1:9321/", ""))
        assertEquals("https://example.com/base", resolveDownloadApiBaseUrl("https://example.com/base/", ""))
    }

    @Test fun `null like token is ignored`() {
        assertEquals("http://127.0.0.1:9321", resolveDownloadApiBaseUrl("http://127.0.0.1:9321", "null"))
    }

    @Test fun `runtime token is appended when missing`() {
        assertEquals("http://127.0.0.1:9321/tok", resolveDownloadApiBaseUrl("http://127.0.0.1:9321", "tok"))
        assertEquals(
            "http://127.0.0.1:9321/base/tok",
            resolveDownloadApiBaseUrl("http://127.0.0.1:9321/base", "tok")
        )
    }

    @Test fun `runtime token already in first path segment is not duplicated`() {
        assertEquals(
            "http://127.0.0.1:9321/tok",
            resolveDownloadApiBaseUrl("http://127.0.0.1:9321/tok", "tok")
        )
    }
}
