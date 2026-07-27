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

class RuntimePackArchiveInstallerTest {
    @Test
    fun `整包与包清单校验通过后替换 node_modules 并保留审计清单`() {
        val root = Files.createTempDirectory("runtime-pack-installer-ok").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            File(coreDir, "node_modules/old-package").apply { mkdirs() }
            File(coreDir, RuntimeDependencyPackProtocol.LEGACY_INSTALLED_LOCK_FILE).writeText("old")
            val archive = File(root, "node_modules.zip")
            writeZip(
                archive,
                linkedMapOf(
                    "node_modules/future-package/package.json" to
                        """{"name":"future-package","version":"1.2.3"}""".toByteArray(),
                    "node_modules/future-package/index.js" to
                        "export const ready = true;\n".toByteArray()
                )
            )
            val manifest = manifestFor(
                archive,
                listOf(
                    RuntimePackPackage(
                        name = "future-package",
                        version = "1.2.3",
                        integrity = "sha512-fixture",
                        path = "node_modules/future-package"
                    )
                )
            )
            val manifestBytes = "{\"signed\":true}".toByteArray()

            RuntimePackArchiveInstaller.verifyAndInstall(
                archive = archive,
                manifest = manifest,
                manifestBytes = manifestBytes,
                coreDir = coreDir
            )

            assertEquals(
                "export const ready = true;\n",
                File(coreDir, "node_modules/future-package/index.js").readText()
            )
            assertFalse(File(coreDir, "node_modules/old-package").exists())
            assertFalse(File(coreDir, RuntimeDependencyPackProtocol.LEGACY_INSTALLED_LOCK_FILE).exists())
            assertTrue(
                File(coreDir, RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE)
                    .readBytes()
                    .contentEquals(manifestBytes)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `整包哈希不一致时拒绝安装`() {
        val root = Files.createTempDirectory("runtime-pack-installer-hash").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val archive = File(root, "node_modules.zip")
            writeZip(
                archive,
                linkedMapOf(
                    "node_modules/future-package/package.json" to
                        """{"name":"future-package","version":"1.2.3"}""".toByteArray()
                )
            )
            val manifest = manifestFor(archive, packages = emptyList()).copy(
                artifactSha256 = "0".repeat(64)
            )

            val error = runCatching {
                RuntimePackArchiveInstaller.verifyAndInstall(archive, manifest, byteArrayOf(), coreDir)
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `实际包集合与签名清单不一致时拒绝安装`() {
        val root = Files.createTempDirectory("runtime-pack-installer-inventory").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val archive = File(root, "node_modules.zip")
            writeZip(
                archive,
                linkedMapOf(
                    "node_modules/future-package/package.json" to
                        """{"name":"future-package","version":"1.2.3"}""".toByteArray()
                )
            )
            val manifest = manifestFor(
                archive,
                listOf(
                    RuntimePackPackage(
                        name = "future-package",
                        version = "9.9.9",
                        path = "node_modules/future-package"
                    )
                )
            )

            val error = runCatching {
                RuntimePackArchiveInstaller.verifyAndInstall(archive, manifest, byteArrayOf(), coreDir)
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertTrue(error?.message.orEmpty().contains("版本"))
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
            val archive = File(root, "node_modules.zip")
            writeZip(archive, linkedMapOf("../escape.js" to "bad".toByteArray()))

            val error = runCatching {
                RuntimePackArchiveInstaller.verifyAndInstall(
                    archive,
                    manifestFor(archive, emptyList()),
                    byteArrayOf(),
                    coreDir
                )
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertFalse(File(root, "escape.js").exists())
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `原生模块在写出核心目录前被拒绝`() {
        val root = Files.createTempDirectory("runtime-pack-installer-native").toFile()
        try {
            val coreDir = File(root, "core").apply { mkdirs() }
            val archive = File(root, "node_modules.zip")
            writeZip(
                archive,
                linkedMapOf(
                    "node_modules/native-package/package.json" to
                        """{"name":"native-package","version":"1.0.0"}""".toByteArray(),
                    "node_modules/native-package/build/addon.node" to byteArrayOf(1, 2, 3)
                )
            )

            val error = runCatching {
                RuntimePackArchiveInstaller.verifyAndInstall(
                    archive,
                    manifestFor(archive, emptyList()),
                    byteArrayOf(),
                    coreDir
                )
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertTrue(error?.message.orEmpty().contains("原生文件"))
            assertFalse(File(coreDir, "node_modules").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun manifestFor(
        archive: File,
        packages: List<RuntimePackPackage>
    ): RuntimePackManifest = RuntimePackManifest(
        schema = RuntimeDependencyPackProtocol.MANIFEST_SCHEMA,
        serial = 1L,
        runtimeProtocol = RuntimeDependencyPackProtocol.RUNTIME_PROTOCOL,
        nodeMajor = RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR,
        runtimeLockSha256 = "a".repeat(64),
        dependencyFingerprint = "b".repeat(64),
        artifactUrl = "https://github.com/lilixu3/danmu-api-runtime-packs/releases/download/test/node_modules.zip",
        artifactSha256 = RuntimeDependencyPackProtocol.sha256(archive),
        artifactSize = archive.length(),
        packages = packages
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
