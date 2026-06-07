package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeManagementPathsTest {

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
