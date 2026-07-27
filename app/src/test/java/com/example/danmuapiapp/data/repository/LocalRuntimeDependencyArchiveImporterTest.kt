package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuntimeDependencyArchiveImporterTest {
    @Test
    fun `任意文件名和两层外目录可以导入完整依赖闭包`() {
        val root = Files.createTempDirectory("local-runtime-import-ok").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val runtimeNodeModules = File(root, "runtime-node-modules").apply { mkdirs() }
            File(coreDir, "package.json").writeText(
                """{"dependencies":{"future-package":"^1.0.0"}}"""
            )
            val archive = File(root, "offline-dependencies.data")
            writeZip(
                archive,
                linkedMapOf(
                    "backup/export/node_modules/future-package/package.json" to
                        """{"name":"future-package","version":"1.2.0","dependencies":{"child-package":"^2.0.0"}}""",
                    "backup/export/node_modules/future-package/index.js" to "module.exports = true;",
                    "backup/export/node_modules/child-package/package.json" to
                        """{"name":"child-package","version":"2.1.0"}"""
                )
            )

            val result = LocalRuntimeDependencyArchiveImporter.verifyAndInstall(
                archive = archive,
                coreDir = coreDir,
                runtimeNodeModulesDir = runtimeNodeModules
            )

            assertEquals(2, result.packageCount)
            assertTrue(File(coreDir, "node_modules/future-package/index.js").isFile)
            assertTrue(File(coreDir, "node_modules/child-package/package.json").isFile)
            assertTrue(File(coreDir, ".danmuapiapp-runtime-import.json").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `传递依赖缺失时拒绝导入且不写入候选核心`() {
        val root = Files.createTempDirectory("local-runtime-import-closure").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val runtimeNodeModules = File(root, "runtime-node-modules").apply { mkdirs() }
            File(coreDir, "package.json").writeText(
                """{"dependencies":{"future-package":"^1.0.0"}}"""
            )
            val archive = File(root, "deps.zip")
            writeZip(
                archive,
                linkedMapOf(
                    "node_modules/future-package/package.json" to
                        """{"name":"future-package","version":"1.2.0","dependencies":{"missing-child":"^1.0.0"}}"""
                )
            )

            val error = runCatching {
                LocalRuntimeDependencyArchiveImporter.verifyAndInstall(
                    archive,
                    coreDir,
                    runtimeNodeModules
                )
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertTrue(error?.message.orEmpty().contains("闭包不完整"))
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `路径穿越和原生模块都会在解压阶段拒绝`() {
        val root = Files.createTempDirectory("local-runtime-import-safety").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val runtimeNodeModules = File(root, "runtime-node-modules").apply { mkdirs() }
            File(coreDir, "package.json").writeText(
                """{"dependencies":{"future-package":"1.0.0"}}"""
            )

            val traversal = File(root, "traversal.zip")
            writeZip(
                traversal,
                linkedMapOf("../node_modules/future-package/package.json" to "{}")
            )
            assertTrue(
                runCatching {
                    LocalRuntimeDependencyArchiveImporter.verifyAndInstall(
                        traversal,
                        coreDir,
                        runtimeNodeModules
                    )
                }.exceptionOrNull() is IOException
            )

            val native = File(root, "native.zip")
            writeZip(
                native,
                linkedMapOf(
                    "node_modules/future-package/package.json" to
                        """{"name":"future-package","version":"1.0.0"}""",
                    "node_modules/future-package/build/addon.node" to "binary"
                )
            )
            val nativeError = runCatching {
                LocalRuntimeDependencyArchiveImporter.verifyAndInstall(
                    native,
                    coreDir,
                    runtimeNodeModules
                )
            }.exceptionOrNull()
            assertTrue(nativeError is IOException)
            assertTrue(nativeError?.message.orEmpty().contains("原生文件"))
            assertFalse(File(root, "node_modules").exists())
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeZip(file: File, entries: LinkedHashMap<String, String>) {
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
    }
}
