package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeManagementPathsTest {

    @Test
    fun `admin-only path uses the active admin session token`() {
        assertEquals("/admin", RuntimeManagementPaths.adminTokenPath(true, "admin"))
    }

    @Test
    fun `admin-only path normalizes whitespace and surrounding slashes`() {
        assertEquals("/admin", RuntimeManagementPaths.adminTokenPath(true, " /admin/ "))
    }

    @Test
    fun `admin-only path is unavailable outside admin mode even if a token remains`() {
        assertNull(RuntimeManagementPaths.adminTokenPath(false, "admin"))
    }

    @Test
    fun `admin-only path rejects empty or placeholder session tokens`() {
        listOf("", " ", "/", " /// ", "null", " NULL ", "undefined", " UNDEFINED ").forEach { token ->
            assertNull(RuntimeManagementPaths.adminTokenPath(true, token))
        }
    }

    @Test
    fun `admin-only endpoints work independently of the ordinary runtime token`() {
        val tokenPath = requireNotNull(RuntimeManagementPaths.adminTokenPath(true, "administrator"))

        assertEquals(
            "http://127.0.0.1:9321/administrator/api/logs/clear",
            RuntimeApiUrls.local(9321, tokenPath, "api/logs/clear")
        )
        assertEquals(
            "http://127.0.0.1:9321/administrator/api/cache/animes",
            RuntimeApiUrls.local(9321, tokenPath, "api/cache/animes")
        )
    }

    @Test
    fun `ordinary management paths retain runtime access outside admin mode`() {
        val paths = RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = listOf("/runtime"),
            adminMode = false,
            adminToken = "admin"
        )

        assertEquals(listOf("/runtime", ""), paths)
    }

    @Test
    fun `admin path is tried before runtime path and bare fallback is last`() {
        val paths = RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = listOf("/rt", "rt"),
            adminMode = true,
            adminToken = "admin"
        )

        assertEquals(listOf("/admin", "/rt", ""), paths)
    }

    @Test
    fun `bare fallback remains when runtime token was explicitly cleared`() {
        val paths = RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = emptyList(),
            adminMode = false,
            adminToken = ""
        )

        assertEquals(listOf(""), paths)
    }
}
