package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreArchiveSourceResolverTest {
    @Test
    fun `extracts short commit from github archive root`() {
        assertEquals(
            "a1b2c3d",
            CoreArchiveSourceResolver.inferCommitSha(
                "owner-danmu_api-a1b2c3d/danmu_api/worker.js"
            )
        )
    }

    @Test
    fun `extracts full commit and normalizes case`() {
        val commit = "ABCDEF0123456789ABCDEF0123456789ABCDEF01"
        assertEquals(
            commit.lowercase(),
            CoreArchiveSourceResolver.inferCommitSha("repo-$commit/danmu-api/configs/globals.js")
        )
    }

    @Test
    fun `does not treat a branch label as a commit`() {
        assertNull(
            CoreArchiveSourceResolver.inferCommitSha(
                "owner-danmu-api-feature-deadbeef-preview/danmu_api/worker.js"
            )
        )
    }
}
