package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RuntimeAssetPathTest {

    @Test
    fun `normalizes nested POSIX paths`() {
        assertEquals("server/runtime/app.js", normalizeRuntimeAssetPath("server\\runtime/app.js"))
    }

    @Test
    fun `rejects absolute and traversing paths`() {
        listOf("../secret", "/absolute", "server/../app.js", "", "server//app.js").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                normalizeRuntimeAssetPath(value)
            }
        }
    }
}
