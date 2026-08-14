package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreRevisionVersionResolverTest {
    @Test
    fun versionOnlyComesFromGlobalsVersionConstant() {
        assertEquals(
            "1.20.6",
            CoreRevisionVersionResolver.parseGlobalsVersion(
                "export const Globals = { VERSION: '1.20.6', MAX_LOGS: 1000 };"
            )
        )
        assertNull(CoreRevisionVersionResolver.parseGlobalsVersion("{ \"version\": \"9.9.9\" }"))
        assertNull(CoreRevisionVersionResolver.parseGlobalsVersion("release v8.8.8"))
    }

    @Test
    fun lookupPathsOnlyTargetGlobalsJsAtTheCommit() {
        assertEquals(
            listOf(
                "configs/globals.js",
                "config/globals.js",
                "globals.js",
                "danmu_api/configs/globals.js",
                "danmu_api/config/globals.js",
                "danmu_api/globals.js",
                "danmu-api/configs/globals.js",
                "danmu-api/config/globals.js",
                "danmu-api/globals.js"
            ),
            CoreRevisionVersionResolver.globalsFilePaths()
        )
        assertEquals(
            listOf(
                "abc123/configs/globals.js",
                "abc123/config/globals.js",
                "abc123/globals.js",
                "abc123/danmu_api/configs/globals.js",
                "abc123/danmu_api/config/globals.js",
                "abc123/danmu_api/globals.js",
                "abc123/danmu-api/configs/globals.js",
                "abc123/danmu-api/config/globals.js",
                "abc123/danmu-api/globals.js"
            ),
            CoreRevisionVersionResolver.globalsPaths("abc123")
        )
    }
}
