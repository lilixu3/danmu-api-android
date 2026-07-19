package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePackArchiveInstallerTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun `通过清单校验的包安装到核心本地 node_modules 并保留审计文件`() {
        val root = Files.createTempDirectory("runtime-pack-installer-ok").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val archive = File(root, "pack.zip")
            val packageJson = """{"name":"future-package","version":"1.2.3"}""".toByteArray()
            val indexJs = "export const ready = true;\n".toByteArray()
            val packageRecord = RuntimePackPackage(
                name = "future-package",
                version = "1.2.3",
                integrity = "sha512-fixture",
                path = "node_modules/future-package"
            )
            val manifest = RuntimePackManifest(
                schema = 1,
                coreRepo = RuntimeDependencyPackProtocol.UPSTREAM_CORE_REPO,
                coreSha = "a".repeat(40),
                coreVersion = "1.0.0",
                runtimeProtocol = RuntimeDependencyPackProtocol.RUNTIME_PROTOCOL,
                nodeMajor = RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR,
                dependencyFingerprint = "b".repeat(64),
                packages = listOf(packageRecord),
                files = listOf(
                    runtimeFile("node_modules/future-package/package.json", packageJson),
                    runtimeFile("node_modules/future-package/index.js", indexJs)
                )
            )
            val manifestBytes = json.encodeToString(manifest).toByteArray()
            writeZip(
                archive,
                linkedMapOf(
                    "manifest.json" to manifestBytes,
                    "runtime-lock.json" to "{}".toByteArray(),
                    "node_modules/future-package/package.json" to packageJson,
                    "node_modules/future-package/index.js" to indexJs
                )
            )
            val entry = RuntimePackEntry(
                coreRepo = manifest.coreRepo,
                coreSha = manifest.coreSha,
                coreVersion = manifest.coreVersion,
                runtimeProtocol = manifest.runtimeProtocol,
                dependencyFingerprint = manifest.dependencyFingerprint,
                manifestSha256 = RuntimeDependencyPackProtocol.sha256(manifestBytes),
                packages = manifest.packages
            )

            RuntimePackArchiveInstaller.verifyAndInstall(archive, entry, coreDir)

            assertEquals(
                "export const ready = true;\n",
                File(coreDir, "node_modules/future-package/index.js").readText()
            )
            assertTrue(File(coreDir, RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE).isFile)
            assertTrue(File(coreDir, RuntimeDependencyPackProtocol.INSTALLED_LOCK_FILE).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `依赖包 Node 主版本与内嵌运行时不一致时拒绝安装`() {
        val root = Files.createTempDirectory("runtime-pack-installer-node-major").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val archive = File(root, "pack.zip")
            val packageJson = """{"name":"future-package","version":"1.2.3"}""".toByteArray()
            val packageRecord = RuntimePackPackage(
                name = "future-package",
                version = "1.2.3",
                path = "node_modules/future-package"
            )
            val manifest = RuntimePackManifest(
                schema = 1,
                coreRepo = RuntimeDependencyPackProtocol.UPSTREAM_CORE_REPO,
                coreSha = "a".repeat(40),
                runtimeProtocol = RuntimeDependencyPackProtocol.RUNTIME_PROTOCOL,
                nodeMajor = RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR + 1,
                dependencyFingerprint = "b".repeat(64),
                packages = listOf(packageRecord),
                files = listOf(runtimeFile("node_modules/future-package/package.json", packageJson))
            )
            val manifestBytes = json.encodeToString(manifest).toByteArray()
            writeZip(
                archive,
                linkedMapOf(
                    "manifest.json" to manifestBytes,
                    "runtime-lock.json" to "{}".toByteArray(),
                    "node_modules/future-package/package.json" to packageJson
                )
            )
            val entry = RuntimePackEntry(
                coreRepo = manifest.coreRepo,
                coreSha = manifest.coreSha,
                runtimeProtocol = manifest.runtimeProtocol,
                dependencyFingerprint = manifest.dependencyFingerprint,
                manifestSha256 = RuntimeDependencyPackProtocol.sha256(manifestBytes),
                packages = manifest.packages
            )

            var failed = false
            try {
                RuntimePackArchiveInstaller.verifyAndInstall(archive, entry, coreDir)
            } catch (_: IOException) {
                failed = true
            }

            assertTrue(failed)
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `路径穿越包在写出核心目录前被拒绝`() {
        val root = Files.createTempDirectory("runtime-pack-installer-traversal").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val archive = File(root, "pack.zip")
            writeZip(archive, linkedMapOf("../escape.js" to "bad".toByteArray()))
            val entry = RuntimePackEntry(coreSha = "a".repeat(40))

            var failed = false
            try {
                RuntimePackArchiveInstaller.verifyAndInstall(archive, entry, coreDir)
            } catch (_: IOException) {
                failed = true
            }

            assertTrue(failed)
            assertFalse(File(root, "escape.js").exists())
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runtimeFile(path: String, content: ByteArray): RuntimePackFile =
        RuntimePackFile(
            path = path,
            size = content.size.toLong(),
            sha256 = RuntimeDependencyPackProtocol.sha256(content)
        )

    private fun writeZip(file: File, entries: LinkedHashMap<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name))
                output.write(content)
                output.closeEntry()
            }
        }
    }
}
