package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class AppUpdatePromptMetadataTest {
    private fun result(version: String = "1.0.5.106", complete: Boolean = false, notes: String = "notes", hasUpdate: Boolean = true): AppUpdateService.CheckResult {
        val asset = if (complete) AppUpdateService.ApkAsset("app-arm64-v8a.apk", "https://github.com/owner/repo/releases/download/v$version/app-arm64-v8a.apk", 100) else null
        return AppUpdateService.CheckResult("1.0.5.102", version, hasUpdate, "https://github.com/owner/repo/releases/tag/v$version", notes, asset, asset?.let { listOf(it.url) }.orEmpty())
    }
    @Test fun `same-version check replaces stale HTML result and unlocks download`() {
        val old = result()
        val current = result(complete = true, notes = "- item 1\n- item 2")
        assertEquals(current, mergeForegroundAppUpdateResult(old, current))
        assertTrue(mergeForegroundAppUpdateResult(old, current).downloadUrls.isNotEmpty())
    }
    @Test fun `temporary asset failure does not erase a working same-version download`() {
        val old = result(complete = true)
        val incoming = result(notes = "updated notes")
        val merged = mergeForegroundAppUpdateResult(old, incoming)
        assertEquals(old.downloadUrls, merged.downloadUrls)
        assertEquals(old.bestAsset, merged.bestAsset)
        assertEquals(incoming.releaseNotes, merged.releaseNotes)
    }
    @Test fun `a different version never inherits an older APK`() {
        val incoming = result(version = "1.0.5.107")
        assertEquals(incoming, mergeForegroundAppUpdateResult(result(complete = true), incoming))
        assertEquals(incoming, mergeForegroundAppUpdateResult(null, incoming))
    }
    @Test fun `no update result never inherits the previous download`() {
        val incoming = result(hasUpdate = false)
        assertEquals(incoming, mergeForegroundAppUpdateResult(result(complete = true), incoming))
    }
}
