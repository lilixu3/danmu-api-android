package com.example.danmuapiapp.data.service

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeProjectManagerValidationTest {

    @Test
    fun `仅有 worker 文件时不应视为核心完整`() {
        val coreDir = Files.createTempDirectory("core-validation-worker-only").toFile()
        try {
            coreDir.resolve("worker.js").writeText("export default {}\n")

            assertFalse(hasRequiredCoreFiles(coreDir))
        } finally {
            coreDir.deleteRecursively()
        }
    }

    @Test
    fun `存在 worker 与 configs globals 时应视为核心完整`() {
        val coreDir = Files.createTempDirectory("core-validation-configs").toFile()
        try {
            coreDir.resolve("configs").mkdirs()
            coreDir.resolve("worker.js").writeText("export default {}\n")
            coreDir.resolve("configs/globals.js").writeText("export const VERSION = '1.0.0'\n")

            assertTrue(hasRequiredCoreFiles(coreDir))
        } finally {
            coreDir.deleteRecursively()
        }
    }

    @Test
    fun `存在 worker 与旧版 config globals 时也应视为核心完整`() {
        val coreDir = Files.createTempDirectory("core-validation-config").toFile()
        try {
            coreDir.resolve("config").mkdirs()
            coreDir.resolve("worker.js").writeText("export default {}\n")
            coreDir.resolve("config/globals.js").writeText("export const VERSION = '1.0.0'\n")

            assertTrue(hasRequiredCoreFiles(coreDir))
        } finally {
            coreDir.deleteRecursively()
        }
    }
}
