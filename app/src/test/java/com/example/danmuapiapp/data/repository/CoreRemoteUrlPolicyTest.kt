package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreRemoteUrlPolicyTest {
    @Test
    fun `API zip URLs keep direct access first and collapse duplicate proxies`() {
        val zip = "https://api.github.com/repos/o/r/zipball/main"
        assertEquals(
            listOf(zip, "https://proxy/$zip"),
            CoreRemoteUrlPolicy.downloadCandidates(zip, listOf("https://proxy/$zip", zip))
        )
    }

    @Test
    fun `archive URLs keep proxy order and branch history falls back to default`() {
        val archive = "https://github.com/o/r/archive/refs/heads/main.zip"
        assertEquals(
            listOf("https://proxy/$archive", archive),
            CoreRemoteUrlPolicy.downloadCandidates(archive, listOf("https://proxy/$archive", archive))
        )
        val endpoints = CoreRemoteUrlPolicy.commitHistoryEndpoints("o/r", listOf("feature/x")) {
            listOf("https://api/$it")
        }
        assertEquals(
            listOf(
                "https://api/repos/o/r/commits?sha=feature%2Fx&per_page=20",
                "https://api/repos/o/r/commits?per_page=20"
            ),
            endpoints
        )
    }

    @Test
    fun `path components use percent encoding without plus signs`() {
        assertEquals("feature%2Fx%20y", CoreRemoteUrlPolicy.encodeUrlPart("feature/x y"))
    }
}
