package com.example.danmuapiapp.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeApiAccessResolverTest {

    @Test
    fun `token path falls back to loopback path`() {
        val access = RuntimeApiAccess(
            port = 9321,
            runtimeToken = "87654321",
            tokenPaths = listOf("/87654321")
        )

        assertEquals(listOf("/87654321", ""), access.localPathCandidates())
    }

    @Test
    fun `missing runtime token only uses the loopback path`() {
        val access = RuntimeApiAccess(
            port = 9321,
            runtimeToken = "",
            tokenPaths = emptyList()
        )

        assertEquals(listOf(""), access.localPathCandidates())
    }

    @Test
    fun `candidate paths are normalized and deduplicated`() {
        val access = RuntimeApiAccess(
            port = 9321,
            runtimeToken = "token",
            tokenPaths = listOf("token", "/token/", "/")
        )

        assertEquals(listOf("/token", ""), access.localPathCandidates())
    }
}
