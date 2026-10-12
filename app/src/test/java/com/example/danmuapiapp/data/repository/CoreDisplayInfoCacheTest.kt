package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CoreDisplayInfoCacheTest {
    private val scope = "root:/data/adb/danmuapi_runtime/app"
    private val installed = CoreInfo(
        ApiVariant.Stable, "1.21.5", isInstalled = true,
        sourceCommitSha = "a".repeat(40),
        pullRequestNumbers = listOf(492, 500),
        sourceInstalledAtEpochMillis = 1791763200000L,
        sourceCommitPublishedAt = "2026-10-11T12:16:00Z"
    )
    private val cores = ApiVariant.entries.map {
        if (it == installed.variant) installed else CoreInfo(it, null, false)
    }

    @Test fun `startup display restores installed version and local metadata`() {
        val cached = CoreDisplayInfoCodec.decode(CoreDisplayInfoCodec.encode(scope, cores), scope)!!
        assertEquals(installed, cached.installedInfo(ApiVariant.Stable))
        assertNull(cached.installedInfo(ApiVariant.Dev))
        assertNull(cached.installedInfo(ApiVariant.Custom))
    }

    @Test fun `different mode or working directory cannot reuse old installation`() {
        val raw = CoreDisplayInfoCodec.encode(scope, cores)
        assertNull(CoreDisplayInfoCodec.decode(raw, "normal:/data/user/0/app/files"))
        assertNull(CoreDisplayInfoCodec.decode(raw, "root:/data/adb/danmuapi_runtime/other-app"))
    }

    @Test fun `uninstalled cached result stays unknown until the local scan finishes`() {
        val raw = CoreDisplayInfoCodec.encode(scope, cores.map { it.copy(isInstalled = false) })
        val cached = CoreDisplayInfoCodec.decode(raw, scope)!!
        ApiVariant.entries.forEach { assertNull(cached.installedInfo(it)) }
    }

    @Test fun `display cache does not restore operation or update decisions`() {
        val transient = installed.copy(isUpdating = true, sourceMismatch = true,
            hasVersionUpdate = true, availableVersion = "next", updateCheckError = "failed")
        val raw = CoreDisplayInfoCodec.encode(scope, cores.map { if (it.variant == installed.variant) transient else it })
        assertEquals(installed, CoreDisplayInfoCodec.decode(raw, scope)!!.installedInfo(installed.variant))
    }

    @Test fun `missing damaged or unsupported snapshots are ignored`() {
        assertNull(CoreDisplayInfoCodec.decode(null, scope))
        assertNull(CoreDisplayInfoCodec.decode("", scope))
        assertNull(CoreDisplayInfoCodec.decode("broken json", scope))
        val cached = CoreDisplayInfoCodec.decode(CoreDisplayInfoCodec.encode(scope, cores), scope)!!
        assertNull(CoreDisplayInfoCodec.decode(Json.encodeToString(cached.copy(schemaVersion = 2)), scope))
    }

    @Test fun `incomplete or duplicate variant snapshots are ignored`() {
        assertNull(CoreDisplayInfoCodec.decode(CoreDisplayInfoCodec.encode(scope, listOf(installed)), scope))
        assertNull(CoreDisplayInfoCodec.decode(CoreDisplayInfoCodec.encode(scope, List(3) { installed }), scope))
    }
}
