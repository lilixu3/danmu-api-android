package com.example.danmuapiapp.data.service

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePathsMigrationTest {

    @Test
    fun `工作目录身份使用规范路径且根目录保持有效`() {
        assertEquals("/work/one", RuntimePaths.workDirIdentity(File("/work/./one/../one")))
        assertEquals(File.separator, RuntimePaths.workDirIdentity(File(File.separator)))
    }

    @Test
    fun `路径和 URI 偏好变化都会触发目录切换刷新`() {
        assertTrue(RuntimePaths.isWorkDirSelectionPreference(RuntimePaths.KEY_CUSTOM_BASE_PATH))
        assertTrue(RuntimePaths.isWorkDirSelectionPreference(RuntimePaths.KEY_CUSTOM_BASE_URI))
        assertFalse(RuntimePaths.isWorkDirSelectionPreference("unrelated"))
        assertFalse(RuntimePaths.isWorkDirSelectionPreference(null))
    }

    @Test
    fun `核心选择沿用运行时 偏好 旧偏好 环境变量 优先级`() {
        assertEquals(
            "dev",
            RuntimePaths.resolveSelectedRuntimeVariantKey(
                runtimeVariant = "dev",
                legacyVariant = "custom",
                envVariant = "stable"
            )
        )
        assertEquals(
            "custom",
            RuntimePaths.resolveSelectedRuntimeVariantKey(
                runtimeVariant = "unknown",
                legacyVariant = "custom",
                envVariant = "stable"
            )
        )
        assertEquals(
            "dev",
            RuntimePaths.resolveSelectedRuntimeVariantKey(
                runtimeVariant = null,
                legacyVariant = "",
                envVariant = "development"
            )
        )
        assertEquals(
            "stable",
            RuntimePaths.resolveSelectedRuntimeVariantKey(
                runtimeVariant = null,
                legacyVariant = null,
                envVariant = null
            )
        )
    }

    @Test
    fun `只迁移选中的核心与配置`() {
        val oldBase = Files.createTempDirectory("work-dir-migration-old").toFile()
        val newBase = Files.createTempDirectory("work-dir-migration-new").toFile()
        try {
            val oldProject = File(oldBase, "nodejs-project")
            oldProject.resolve("config/.env").apply {
                parentFile?.mkdirs()
                writeText("DANMU_API_VARIANT=dev\n")
            }
            oldProject.resolve("logs/old.log").apply {
                parentFile?.mkdirs()
                writeText("old log")
            }
            oldProject.resolve(".cache/old-cache.json").apply {
                parentFile?.mkdirs()
                writeText("old cache")
            }
            oldProject.resolve(".cache/favoritesCache").writeText(
                """{"迁移收藏":{"timestamp":100,"results":[]}}"""
            )
            createCore(oldProject, "stable", "stable-source")
            createCore(oldProject, "dev", "dev-source")
            createCore(oldProject, "custom", "custom-source")
            oldProject.resolve("danmu_api_dev/node_modules/large-package").mkdirs()
            oldProject.resolve("danmu_api_dev/node_modules/large-package/package.json")
                .writeText("{\"version\":\"1.0.0\"}\n")
            oldProject.resolve("danmu_api_dev/.cache/bangumi-data-cache.json").apply {
                parentFile?.mkdirs()
                writeText("generated cache")
            }
            oldProject.resolve("danmu_api_dev/logs/runtime.log").apply {
                parentFile?.mkdirs()
                writeText("runtime log")
            }
            oldProject.resolve("danmu_api_dev/bangumi-data-cache").apply {
                mkdirs()
                resolve("data.json").writeText("generated cache")
            }
            oldProject.resolve("danmu_api_dev/bangumi-data-cache.json")
                .writeText("generated cache")
            oldProject.resolve("danmu_api_dev/utils/bangumi-data-util.js").apply {
                parentFile?.mkdirs()
                writeText("export const loadBangumiData = () => true\n")
            }

            RuntimePaths.migrateSelectedCoreAndConfig(oldBase, newBase, "development")

            val newProject = File(newBase, "nodejs-project")
            assertEquals("DANMU_API_VARIANT=dev\n", newProject.resolve("config/.env").readText())
            assertEquals("dev-source", newProject.resolve("danmu_api_dev/worker.js").readText())
            assertFalse(newProject.resolve("danmu_api_dev/node_modules").exists())
            assertFalse(newProject.resolve("danmu_api_dev/.cache").exists())
            assertFalse(newProject.resolve("danmu_api_dev/logs").exists())
            assertFalse(newProject.resolve("danmu_api_dev/bangumi-data-cache").exists())
            assertFalse(newProject.resolve("danmu_api_dev/bangumi-data-cache.json").exists())
            assertTrue(newProject.resolve("danmu_api_dev/utils/bangumi-data-util.js").isFile)
            assertFalse(newProject.resolve("danmu_api_stable").exists())
            assertFalse(newProject.resolve("danmu_api_custom").exists())
            assertFalse(newProject.resolve("logs/old.log").exists())
            assertFalse(newProject.resolve(".cache/old-cache.json").exists())
            assertTrue(newProject.resolve(".cache/favoritesCache").readText().contains("迁移收藏"))
            assertTrue(oldProject.resolve("danmu_api_stable/worker.js").exists())
            assertTrue(oldProject.resolve("logs/old.log").exists())
            assertTrue(oldProject.resolve(".cache/old-cache.json").exists())
        } finally {
            oldBase.deleteRecursively()
            newBase.deleteRecursively()
        }
    }

    @Test
    fun `目录写入探针会真实读写并清理临时文件`() {
        val directory = Files.createTempDirectory("work-dir-write-probe").toFile()
        try {
            assertTrue(RuntimePaths.verifyDirectoryWritable(directory))
            assertTrue(
                directory.listFiles().orEmpty().none {
                    it.name.startsWith(".danmu-write-probe-")
                }
            )

            val regularFile = File(directory, "not-a-directory").apply { writeText("file") }
            assertFalse(RuntimePaths.verifyDirectoryWritable(regularFile))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `偏好提交失败时完整恢复目标配置和核心`() {
        val oldBase = Files.createTempDirectory("work-dir-rollback-old").toFile()
        val newBase = Files.createTempDirectory("work-dir-rollback-new").toFile()
        try {
            val oldProject = File(oldBase, "nodejs-project")
            oldProject.resolve("config/.env").apply {
                parentFile?.mkdirs()
                writeText("SOURCE_CONFIG=1\n")
            }
            oldProject.resolve("config/source-only.json").writeText("source")
            createCore(oldProject, "dev", "source-core")

            val newProject = File(newBase, "nodejs-project")
            newProject.resolve("config/.env").apply {
                parentFile?.mkdirs()
                writeText("TARGET_CONFIG=1\n")
            }
            newProject.resolve("danmu_api_dev").apply { mkdirs() }
                .resolve("legacy.txt").writeText("keep-target")
            oldProject.resolve(".cache/favoritesCache").apply {
                parentFile?.mkdirs()
                writeText("""{"来源收藏":{"timestamp":100}}""")
            }
            newProject.resolve(".cache/favoritesCache").apply {
                parentFile?.mkdirs()
                writeText("""{"目标收藏":{"timestamp":200}}""")
            }

            val result = runCatching {
                RuntimePaths.performWorkDirMigrationTransaction(
                    oldBase = oldBase,
                    newBase = newBase,
                    selectedVariantKey = "dev",
                    commitPreference = { false }
                )
            }

            assertTrue(result.isFailure)
            assertEquals("TARGET_CONFIG=1\n", newProject.resolve("config/.env").readText())
            assertFalse(newProject.resolve("config/source-only.json").exists())
            assertEquals("keep-target", newProject.resolve("danmu_api_dev/legacy.txt").readText())
            assertFalse(newProject.resolve("danmu_api_dev/worker.js").exists())
            assertTrue(newProject.resolve(".cache/favoritesCache").readText().contains("目标收藏"))
            assertFalse(newProject.resolve(".cache/favoritesCache").readText().contains("来源收藏"))
            assertTrue(oldProject.resolve("danmu_api_dev/worker.js").isFile)
            assertTrue(
                newProject.listFiles().orEmpty().none {
                    it.name.contains("-migration-") || it.name.contains("-backup-")
                }
            )
        } finally {
            oldBase.deleteRecursively()
            newBase.deleteRecursively()
        }
    }

    @Test
    fun `迁移目录不能互相包含但同目录不算冲突`() {
        val root = Files.createTempDirectory("work-dir-overlap").toFile()
        try {
            val child = File(root, "child").apply { mkdirs() }
            val sibling = Files.createTempDirectory("work-dir-sibling").toFile()
            try {
                assertTrue(RuntimePaths.workDirectoriesOverlap(root, child))
                assertTrue(RuntimePaths.workDirectoriesOverlap(child, root))
                assertFalse(RuntimePaths.workDirectoriesOverlap(root, root))
                assertFalse(RuntimePaths.workDirectoriesOverlap(root, sibling))
            } finally {
                sibling.deleteRecursively()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `目标已有配置和当前核心时不覆盖且不合并日志缓存`() {
        val oldBase = Files.createTempDirectory("work-dir-migration-existing-old").toFile()
        val newBase = Files.createTempDirectory("work-dir-migration-existing-new").toFile()
        try {
            val oldProject = File(oldBase, "nodejs-project")
            oldProject.resolve("config/.env").apply {
                parentFile?.mkdirs()
                writeText("SOURCE_CONFIG=1\n")
            }
            oldProject.resolve("config/source-only.json").writeText("source")
            oldProject.resolve("logs/source.log").apply {
                parentFile?.mkdirs()
                writeText("source log")
            }
            oldProject.resolve(".cache/source-cache.json").apply {
                parentFile?.mkdirs()
                writeText("source cache")
            }
            oldProject.resolve(".cache/favoritesCache").writeText(
                """{"共同收藏":{"timestamp":300,"source":"source"},"来源收藏":{"timestamp":100}}"""
            )
            createCore(oldProject, "custom", "source-core")

            val newProject = File(newBase, "nodejs-project")
            newProject.resolve("config/.env").apply {
                parentFile?.mkdirs()
                writeText("TARGET_CONFIG=1\n")
            }
            newProject.resolve("logs/target.log").apply {
                parentFile?.mkdirs()
                writeText("target log")
            }
            newProject.resolve(".cache/target-cache.json").apply {
                parentFile?.mkdirs()
                writeText("target cache")
            }
            newProject.resolve(".cache/favoritesCache").writeText(
                """{"共同收藏":{"timestamp":200,"source":"target"},"目标收藏":{"timestamp":100}}"""
            )
            createCore(newProject, "custom", "target-core")

            RuntimePaths.migrateSelectedCoreAndConfig(oldBase, newBase, "custom")

            assertEquals("TARGET_CONFIG=1\n", newProject.resolve("config/.env").readText())
            assertEquals("source", newProject.resolve("config/source-only.json").readText())
            assertEquals("target-core", newProject.resolve("danmu_api_custom/worker.js").readText())
            assertFalse(newProject.resolve("logs/source.log").exists())
            assertEquals("target log", newProject.resolve("logs/target.log").readText())
            assertFalse(newProject.resolve(".cache/source-cache.json").exists())
            assertEquals("target cache", newProject.resolve(".cache/target-cache.json").readText())
            val favorites = org.json.JSONObject(newProject.resolve(".cache/favoritesCache").readText())
            assertEquals(3, favorites.length())
            assertEquals("source", favorites.getJSONObject("共同收藏").getString("source"))
        } finally {
            oldBase.deleteRecursively()
            newBase.deleteRecursively()
        }
    }

    private fun createCore(projectDir: File, variantKey: String, workerContent: String) {
        projectDir.resolve("danmu_api_$variantKey/configs").mkdirs()
        projectDir.resolve("danmu_api_$variantKey/worker.js").writeText(workerContent)
        projectDir.resolve("danmu_api_$variantKey/configs/globals.js").writeText("export const VERSION = '1.0.0'\n")
    }
}
