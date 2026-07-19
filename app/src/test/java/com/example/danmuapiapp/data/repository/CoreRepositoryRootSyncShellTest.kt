package com.example.danmuapiapp.data.repository

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRepositoryRootSyncShellTest {
    @Test
    fun `Root 核心和本地依赖通过临时目录原子切换`() {
        val root = Files.createTempDirectory("root-core-atomic-sync").toFile()
        try {
            val source = root.resolve("source core 'quoted'").apply { mkdirs() }
            val destination = root.resolve("root-core").apply { mkdirs() }
            source.resolve("worker.js").writeText("// new worker\n")
            source.resolve("node_modules/future-package").mkdirs()
            source.resolve("node_modules/future-package/index.js").writeText("export const ready = true;\n")
            source.resolve(".danmuapiapp-runtime-pack.json").writeText("{}\n")
            destination.resolve("worker.js").writeText("// old worker\n")
            destination.resolve("old-marker").writeText("old\n")

            val nonce = "unit-test"
            val result = runShell(
                CoreRepositoryImpl.buildAtomicRootCoreSyncShell(
                    sourcePath = source.absolutePath,
                    destinationPath = destination.absolutePath,
                    nonce = nonce
                )
            )

            assertEquals(result.output, 0, result.exitCode)
            assertEquals("// new worker\n", destination.resolve("worker.js").readText())
            assertTrue(destination.resolve("node_modules/future-package/index.js").isFile)
            assertTrue(destination.resolve(".danmuapiapp-runtime-pack.json").isFile)
            assertFalse(destination.resolve("old-marker").exists())
            assertFalse(root.resolve("root-core.incoming-$nonce").exists())
            assertFalse(root.resolve("root-core.backup-$nonce").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `Root staging 校验失败时保留旧核心`() {
        val root = Files.createTempDirectory("root-core-atomic-rollback").toFile()
        try {
            val source = root.resolve("invalid-source").apply { mkdirs() }
            val destination = root.resolve("root-core").apply { mkdirs() }
            source.resolve("node_modules/future-package").mkdirs()
            source.resolve("node_modules/future-package/index.js").writeText("broken\n")
            destination.resolve("worker.js").writeText("// old worker\n")
            destination.resolve("old-marker").writeText("old\n")

            val nonce = "rollback-test"
            val result = runShell(
                CoreRepositoryImpl.buildAtomicRootCoreSyncShell(
                    sourcePath = source.absolutePath,
                    destinationPath = destination.absolutePath,
                    nonce = nonce
                )
            )

            assertEquals(5, result.exitCode)
            assertEquals("// old worker\n", destination.resolve("worker.js").readText())
            assertTrue(destination.resolve("old-marker").isFile)
            assertFalse(root.resolve("root-core.incoming-$nonce").exists())
            assertFalse(root.resolve("root-core.backup-$nonce").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runShell(script: String): ShellResult {
        val process = ProcessBuilder("sh", "-c", script)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return ShellResult(process.waitFor(), output)
    }

    private data class ShellResult(val exitCode: Int, val output: String)
}
