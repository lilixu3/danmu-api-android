package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRuntimeControllerNodeModulesTest {

    @Test
    fun `Root node_modules integrity probe discovers packages from source tree`() {
        val script = RootRuntimeController.buildNodeModuleIntegrityProbeShell(
            srcNodeModulesVar = "SRC_NM",
            dstNodeModulesVar = "DST_NM"
        )

        assertTrue(script.contains("for ENTRY in \"${'$'}SRC_ROOT\"/*"))
        assertTrue(script.contains("check_node_package"))
        assertTrue(script.contains("NEED_SYNC=1"))
        assertFalse(script.contains("PKG='node-fetch'"))
    }

    @Test
    fun `Root node_modules integrity verification exits when a source package is still missing`() {
        val script = RootRuntimeController.buildNodeModuleIntegrityVerifyShell(
            srcNodeModulesVar = "SRC_NM",
            dstNodeModulesVar = "DST_NM"
        )

        assertTrue(script.contains("check_node_package"))
        assertTrue(script.contains("exit 2"))
    }

    @Test
    fun `Root node_modules probe marks sync needed when any source package is missing from destination`() {
        val tempDir = Files.createTempDirectory("root-node-modules-probe").toFile()
        try {
            val srcNodeModules = tempDir.resolve("src")
            val dstNodeModules = tempDir.resolve("dst")
            srcNodeModules.resolve("unlisted-dep").mkdirs()
            dstNodeModules.mkdirs()
            srcNodeModules.resolve("unlisted-dep/package.json").writeText("{\"version\":\"1.0.0\"}\n")

            val script = RootRuntimeController.buildNodeModuleIntegrityProbeShell(
                srcNodeModulesVar = "SRC_NM",
                dstNodeModulesVar = "DST_NM"
            )
            val command = """
                SRC_NM=${shellQuote(srcNodeModules.absolutePath)}
                DST_NM=${shellQuote(dstNodeModules.absolutePath)}
                NEED_SYNC=0
                $script
                [ "${'$'}NEED_SYNC" = "1" ]
            """.trimIndent()

            assertEquals(0, runShell(command))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `Root node_modules verify exits non-zero when any source package remains missing`() {
        val tempDir = Files.createTempDirectory("root-node-modules-verify").toFile()
        try {
            val srcNodeModules = tempDir.resolve("src")
            val dstNodeModules = tempDir.resolve("dst")
            srcNodeModules.resolve("unlisted-dep").mkdirs()
            dstNodeModules.mkdirs()
            srcNodeModules.resolve("unlisted-dep/package.json").writeText("{\"version\":\"1.0.0\"}\n")

            val script = RootRuntimeController.buildNodeModuleIntegrityVerifyShell(
                srcNodeModulesVar = "SRC_NM",
                dstNodeModulesVar = "DST_NM"
            )
            val command = """
                SRC_NM=${shellQuote(srcNodeModules.absolutePath)}
                DST_NM=${shellQuote(dstNodeModules.absolutePath)}
                $script
                exit 0
            """.trimIndent()

            assertEquals(2, runShell(command))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `Root node_modules repair copies only missing or mismatched packages including scoped packages`() {
        val tempDir = Files.createTempDirectory("root-node-modules-repair").toFile()
        try {
            val srcNodeModules = tempDir.resolve("src")
            val dstNodeModules = tempDir.resolve("dst")
            srcNodeModules.resolve("unlisted-dep/lib").mkdirs()
            srcNodeModules.resolve("@scope/scoped-dep").mkdirs()
            dstNodeModules.resolve("existing-dep").mkdirs()
            srcNodeModules.resolve("unlisted-dep/package.json").writeText("{\"version\":\"1.0.0\"}\n")
            srcNodeModules.resolve("unlisted-dep/lib/index.js").writeText("module.exports = 1\n")
            srcNodeModules.resolve("@scope/scoped-dep/package.json").writeText("{\"version\":\"2.0.0\"}\n")
            dstNodeModules.resolve("existing-dep/package.json").writeText("{\"version\":\"9.9.9\"}\n")

            val repair = RootRuntimeController.buildNodeModulePackageRepairShell(
                srcNodeModulesVar = "SRC_NM",
                dstNodeModulesVar = "DST_NM"
            )
            val verify = RootRuntimeController.buildNodeModuleIntegrityVerifyShell(
                srcNodeModulesVar = "SRC_NM",
                dstNodeModulesVar = "DST_NM"
            )
            val command = """
                SRC_NM=${shellQuote(srcNodeModules.absolutePath)}
                DST_NM=${shellQuote(dstNodeModules.absolutePath)}
                $repair
                $verify
            """.trimIndent()

            assertEquals(0, runShell(command))
            assertEquals(
                "{\"version\":\"1.0.0\"}\n",
                dstNodeModules.resolve("unlisted-dep/package.json").readText()
            )
            assertEquals(
                "module.exports = 1\n",
                dstNodeModules.resolve("unlisted-dep/lib/index.js").readText()
            )
            assertEquals(
                "{\"version\":\"2.0.0\"}\n",
                dstNodeModules.resolve("@scope/scoped-dep/package.json").readText()
            )
            assertEquals(
                "{\"version\":\"9.9.9\"}\n",
                dstNodeModules.resolve("existing-dep/package.json").readText()
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `Root incremental project sync copies wrapper files without traversing runtime directories`() {
        val tempDir = Files.createTempDirectory("root-project-incremental-sync").toFile()
        try {
            val srcProject = tempDir.resolve("src")
            val dstProject = tempDir.resolve("dst")
            srcProject.mkdirs()
            dstProject.mkdirs()

            srcProject.resolve("main.js").writeText("new-main\n")
            srcProject.resolve("android-server.mjs").writeText("new-server\n")
            srcProject.resolve(".app_version").writeText("new-version\n")
            srcProject.resolve("node_modules/node-fetch").mkdirs()
            srcProject.resolve("node_modules/node-fetch/package.json").writeText("src-dep\n")
            srcProject.resolve("danmu_api_stable").mkdirs()
            srcProject.resolve("danmu_api_stable/worker.js").writeText("src-core\n")
            srcProject.resolve("config").mkdirs()
            srcProject.resolve("config/.env").writeText("src-env\n")

            dstProject.resolve("node_modules/existing").mkdirs()
            dstProject.resolve("node_modules/existing/package.json").writeText("dst-dep\n")
            dstProject.resolve("danmu_api_stable").mkdirs()
            dstProject.resolve("danmu_api_stable/worker.js").writeText("dst-core\n")
            dstProject.resolve("config").mkdirs()
            dstProject.resolve("config/.env").writeText("dst-env\n")

            val script = RootRuntimeController.buildRootProjectIncrementalSyncShell(
                srcProjectPath = srcProject.absolutePath,
                dstProjectPath = dstProject.absolutePath
            )

            assertFalse(script.contains(".tmp_app_sync"))
            assertFalse(script.contains("SRC/."))
            assertEquals(0, runShell(script))
            assertEquals("new-main\n", dstProject.resolve("main.js").readText())
            assertEquals("new-server\n", dstProject.resolve("android-server.mjs").readText())
            assertEquals("new-version\n", dstProject.resolve(".app_version").readText())
            assertEquals("dst-dep\n", dstProject.resolve("node_modules/existing/package.json").readText())
            assertEquals("dst-core\n", dstProject.resolve("danmu_api_stable/worker.js").readText())
            assertEquals("dst-env\n", dstProject.resolve("config/.env").readText())
            assertFalse(dstProject.resolve("node_modules/node-fetch/package.json").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `Root hot permission normalization avoids recursive node_modules and core scan`() {
        val hotScript = RootRuntimeController.buildRootProjectPermissionNormalizeShell(
            rootProjectPath = "/data/adb/danmuapi_runtime/test/nodejs-project",
            fullScan = false
        )
        val fullScript = RootRuntimeController.buildRootProjectPermissionNormalizeShell(
            rootProjectPath = "/data/adb/danmuapi_runtime/test/nodejs-project",
            fullScan = true
        )

        assertFalse(hotScript.contains("chown -R"))
        assertFalse(hotScript.contains("chmod -R"))
        assertTrue(hotScript.contains("package-lock.json"))
        assertTrue(hotScript.contains("chmod 0640"))
        assertTrue(fullScript.contains("chown -R"))
        assertTrue(fullScript.contains("chmod -R"))
    }

    private fun runShell(command: String): Int {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        val exitCode = process.waitFor()
        if (exitCode != 0 && output.isNotBlank()) {
            println(output)
        }
        return exitCode
    }
}
