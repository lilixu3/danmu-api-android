package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDirectoryOpsTest {

    @Test(expected = IOException::class)
    fun `复制返回 false 时应抛出异常`() {
        val source = Files.createTempDirectory("core-copy-src").toFile()
        val target = File(source.parentFile, "core-copy-target-false")
        source.resolve("worker.js").writeText("export default {}\n")

        try {
            copyDirectoryOrThrow(source, target, copyBlock = { _, _ -> false })
        } finally {
            source.deleteRecursively()
            target.deleteRecursively()
        }
    }

    @Test(expected = IOException::class)
    fun `复制成功但源目录清理失败时应抛出异常`() {
        val source = Files.createTempDirectory("core-copy-src-cleanup").toFile()
        val target = File(source.parentFile, "core-copy-target-cleanup")
        source.resolve("worker.js").writeText("export default {}\n")

        try {
            copyDirectoryOrThrow(
                source,
                target,
                copyBlock = { src, dst -> src.copyRecursively(dst, overwrite = true) },
                cleanupBlock = { false }
            )
        } finally {
            source.deleteRecursively()
            target.deleteRecursively()
        }
    }

    @Test
    fun `复制成功且清理成功时应生成完整目标目录`() {
        val source = Files.createTempDirectory("core-copy-src-ok").toFile()
        val target = File(source.parentFile, "core-copy-target-ok")
        source.resolve("nested").mkdirs()
        source.resolve("nested/worker.js").writeText("export default {}\n")

        try {
            copyDirectoryOrThrow(source, target)

            assertTrue(target.resolve("nested/worker.js").exists())
            assertFalse(source.exists())
        } finally {
            source.deleteRecursively()
            target.deleteRecursively()
        }
    }

    @Test
    fun `已安装核心依赖修复只替换 node_modules`() {
        val root = Files.createTempDirectory("installed-dependency-repair").toFile()
        val installedCore = root.resolve("danmu_api_stable").apply { mkdirs() }
        val stagingCore = root.resolve("danmu_api_stable.dependency-repair").apply { mkdirs() }
        installedCore.resolve("main.js").writeText("export const core = 'keep'\n")
        installedCore.resolve("node_modules/old-package").mkdirs()
        installedCore.resolve("node_modules/old-package/package.json").writeText("{}\n")
        installedCore.resolve(RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE)
            .writeText("old manifest")
        stagingCore.resolve("node_modules/new-package").mkdirs()
        stagingCore.resolve("node_modules/new-package/package.json").writeText("{}\n")
        stagingCore.resolve(LocalRuntimeDependencyArchiveImporter.LOCAL_IMPORT_AUDIT_FILE)
            .writeText("new audit")

        try {
            replaceInstalledCoreDependencies(stagingCore, installedCore) {
                assertTrue(installedCore.resolve("node_modules/new-package/package.json").isFile)
            }

            assertEquals("export const core = 'keep'\n", installedCore.resolve("main.js").readText())
            assertTrue(installedCore.resolve("node_modules/new-package/package.json").isFile)
            assertFalse(installedCore.resolve("node_modules/old-package").exists())
            assertFalse(
                installedCore.resolve(RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE).exists()
            )
            assertEquals(
                "new audit",
                installedCore.resolve(LocalRuntimeDependencyArchiveImporter.LOCAL_IMPORT_AUDIT_FILE)
                    .readText()
            )
            assertFalse(installedCore.listFiles().orEmpty().any { it.name.startsWith(".node_modules-backup-") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `已安装核心依赖校验失败时恢复旧依赖与记录`() {
        val root = Files.createTempDirectory("installed-dependency-rollback").toFile()
        val installedCore = root.resolve("danmu_api_stable").apply { mkdirs() }
        val stagingCore = root.resolve("danmu_api_stable.dependency-repair").apply { mkdirs() }
        installedCore.resolve("main.js").writeText("export const core = 'keep'\n")
        installedCore.resolve("node_modules/old-package").mkdirs()
        installedCore.resolve("node_modules/old-package/package.json").writeText("old\n")
        installedCore.resolve(RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE)
            .writeText("old manifest")
        stagingCore.resolve("node_modules/new-package").mkdirs()
        stagingCore.resolve("node_modules/new-package/package.json").writeText("new\n")
        stagingCore.resolve(LocalRuntimeDependencyArchiveImporter.LOCAL_IMPORT_AUDIT_FILE)
            .writeText("new audit")

        try {
            val result = runCatching {
                replaceInstalledCoreDependencies(stagingCore, installedCore) {
                    throw IOException("模拟依赖校验失败")
                }
            }
            assertTrue(result.isFailure)

            assertEquals("export const core = 'keep'\n", installedCore.resolve("main.js").readText())
            assertEquals(
                "old\n",
                installedCore.resolve("node_modules/old-package/package.json").readText()
            )
            assertFalse(installedCore.resolve("node_modules/new-package").exists())
            assertEquals(
                "old manifest",
                installedCore.resolve(RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE).readText()
            )
            assertFalse(
                installedCore.resolve(LocalRuntimeDependencyArchiveImporter.LOCAL_IMPORT_AUDIT_FILE)
                    .exists()
            )
            assertFalse(installedCore.listFiles().orEmpty().any { it.name.startsWith(".node_modules-backup-") })
        } finally {
            root.deleteRecursively()
        }
    }
}
