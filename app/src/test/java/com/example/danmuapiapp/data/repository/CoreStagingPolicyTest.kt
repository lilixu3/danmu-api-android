package com.example.danmuapiapp.data.repository

import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreStagingPolicyTest {
    @Test
    fun `staging directory is created beside the target and marker records time`() {
        val root = Files.createTempDirectory("core-staging").toFile()
        val target = root.resolve("danmu_api_stable")
        try {
            val staging = CoreStagingPolicy.prepareStagingDirectory(target, "staging", now = 123L)
            assertEquals(root, staging.parentFile)
            assertEquals("danmu_api_stable.staging-123", staging.name)
            assertTrue(staging.isDirectory)

            CoreStagingPolicy.writeOperationMarker(staging, operationId = 7L, now = 456L)
            assertEquals(listOf("7", "456"), CoreArtifactPolicy.markerFile(staging).readLines())

            CoreStagingPolicy.clearOperationMarker(staging)
            assertFalse(CoreArtifactPolicy.markerFile(staging).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IOException::class)
    fun `invalid target without parent is rejected`() {
        CoreStagingPolicy.prepareStagingDirectory(java.io.File("relative-core"), "staging", now = 1L)
    }
}
