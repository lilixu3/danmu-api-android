package com.example.danmuapiapp.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownUriPolicyTest {
    @Test
    fun `links allow web and email schemes`() {
        assertTrue(MarkdownUriPolicy.canOpenLink("https://github.com/owner/repo/pull/1"))
        assertTrue(MarkdownUriPolicy.canOpenLink("http://example.com/path"))
        assertTrue(MarkdownUriPolicy.canOpenLink("mailto:owner@example.com"))
    }

    @Test
    fun `links reject local executable and malformed schemes`() {
        assertFalse(MarkdownUriPolicy.canOpenLink("javascript:alert(1)"))
        assertFalse(MarkdownUriPolicy.canOpenLink("file:///data/local/tmp/token"))
        assertFalse(MarkdownUriPolicy.canOpenLink("content://settings/system"))
        assertFalse(MarkdownUriPolicy.canOpenLink("/owner/repo/issues/1"))
        assertFalse(MarkdownUriPolicy.canOpenLink("https://"))
    }

    @Test
    fun `images require absolute https urls`() {
        assertTrue(MarkdownUriPolicy.canLoadImage("https://user-images.githubusercontent.com/a.png"))
        assertFalse(MarkdownUriPolicy.canLoadImage("http://example.com/a.png"))
        assertFalse(MarkdownUriPolicy.canLoadImage("file:///sdcard/a.png"))
        assertFalse(MarkdownUriPolicy.canLoadImage("//example.com/a.png"))
    }
}
