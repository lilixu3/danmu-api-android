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

    @Test
    fun `Root 后置同步失败时恢复 Normal 旧依赖`() {
        val root = Files.createTempDirectory("installed-dependency-root-rollback").toFile()
        val installedCore = root.resolve("danmu_api_stable").apply { mkdirs() }
        val stagingCore = root.resolve("danmu_api_stable.dependency-repair").apply { mkdirs() }
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
                replaceInstalledCoreDependencies(
                    stagingCoreDir = stagingCore,
                    installedCoreDir = installedCore,
                    afterInstall = { throw IOException("模拟 Root 原子同步失败") },
                    verifyInstalled = {
                        assertTrue(installedCore.resolve("node_modules/new-package/package.json").isFile)
                    }
                )
            }

            assertTrue(result.isFailure)
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
    @Test
    fun `stale cleanup removes expired artifacts but keeps the active recovery point`() {
        val root = Files.createTempDirectory("core-stale-cleanup").toFile()
        val expired = root.resolve("danmu_api_stable.staging-1").apply { mkdirs() }
        CoreArtifactPolicy.markerFile(expired).writeText("1\n1\n")
        val active = root.resolve("danmu_api_stable.backup-2").apply { mkdirs() }
        CoreArtifactPolicy.markerFile(active).writeText("2\n1\n")
        val ordinary = root.resolve("ordinary-dir").apply { mkdirs() }

        try {
            cleanupStaleCoreDirectories(
                projectDir = root,
                activeBackupPath = active.canonicalPath,
                now = CoreArtifactPolicy.MARKED_STAGING_RETENTION_MS + 10L
            )

            assertFalse(expired.exists())
            assertTrue(active.exists())
            assertTrue(ordinary.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `replacement failure restores the previous core directory`() {
        val root = Files.createTempDirectory("core-replace-rollback").toFile()
        val target = root.resolve("danmu_api_stable").apply { mkdirs() }
        target.resolve("worker.js").writeText("old\n")
        val missingStaging = root.resolve("missing-staging")

        try {
            val error = runCatching { replaceCoreDirectory(target, missingStaging) }.exceptionOrNull()
            assertTrue(error is IOException)
            assertEquals("临时核心目录不存在", error?.message)
            assertEquals("old\n", target.resolve("worker.js").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful replacement moves staging into place and returns backup`() {
        val root = Files.createTempDirectory("core-replace-success").toFile()
        val target = root.resolve("danmu_api_stable").apply { mkdirs() }
        target.resolve("worker.js").writeText("old\n")
        val staging = root.resolve("danmu_api_stable.staging-1").apply { mkdirs() }
        staging.resolve("worker.js").writeText("new\n")

        try {
            val backup = replaceCoreDirectory(target, staging)
            assertTrue(backup != null && backup.isDirectory)
            assertEquals("new\n", target.resolve("worker.js").readText())
            assertFalse(staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }

}
