package com.example.danmuapiapp.data.service

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePathsMigrationTest {

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
            createCore(oldProject, "stable", "stable-source")
            createCore(oldProject, "dev", "dev-source")
            createCore(oldProject, "custom", "custom-source")

            RuntimePaths.migrateSelectedCoreAndConfig(oldBase, newBase, "development")

            val newProject = File(newBase, "nodejs-project")
            assertEquals("DANMU_API_VARIANT=dev\n", newProject.resolve("config/.env").readText())
            assertEquals("dev-source", newProject.resolve("danmu_api_dev/worker.js").readText())
            assertFalse(newProject.resolve("danmu_api_stable").exists())
            assertFalse(newProject.resolve("danmu_api_custom").exists())
            assertFalse(newProject.resolve("logs/old.log").exists())
            assertFalse(newProject.resolve(".cache/old-cache.json").exists())
            assertTrue(oldProject.resolve("danmu_api_stable/worker.js").exists())
            assertTrue(oldProject.resolve("logs/old.log").exists())
            assertTrue(oldProject.resolve(".cache/old-cache.json").exists())
        } finally {
            oldBase.deleteRecursively()
            newBase.deleteRecursively()
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
            createCore(newProject, "custom", "target-core")

            RuntimePaths.migrateSelectedCoreAndConfig(oldBase, newBase, "custom")

            assertEquals("TARGET_CONFIG=1\n", newProject.resolve("config/.env").readText())
            assertEquals("source", newProject.resolve("config/source-only.json").readText())
            assertEquals("target-core", newProject.resolve("danmu_api_custom/worker.js").readText())
            assertFalse(newProject.resolve("logs/source.log").exists())
            assertEquals("target log", newProject.resolve("logs/target.log").readText())
            assertFalse(newProject.resolve(".cache/source-cache.json").exists())
            assertEquals("target cache", newProject.resolve(".cache/target-cache.json").readText())
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
