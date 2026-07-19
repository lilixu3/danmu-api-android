package com.example.danmuapiapp.data.service

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeProjectManagerRuntimeDependencyTest {

    @Test
    fun `核心声明 brotli 而运行目录缺包时应报告缺失`() {
        val root = Files.createTempDirectory("core-runtime-deps-brotli").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """
                {
                  "dependencies": {
                    "brotli": "^1.3.3"
                  }
                }
                """.trimIndent()
            )

            assertEquals(
                listOf("brotli@^1.3.3"),
                NodeProjectManager.collectMissingRuntimeDepsForCore(
                    coreDir = coreDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `核心声明未识别依赖时应阻止更新`() {
        val root = Files.createTempDirectory("core-runtime-deps-unknown").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """
                {
                  "dependencies": {
                    "future-runtime-package": "^2.0.0"
                  }
                }
                """.trimIndent()
            )

            assertEquals(
                listOf("future-runtime-package@^2.0.0"),
                NodeProjectManager.collectMissingRuntimeDepsForCore(
                    coreDir = coreDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `缺少 brotli 深层运行文件时不应复用旧 node_modules`() {
        val nodeModulesDir = Files.createTempDirectory("runtime-deps-brotli-deep-file").toFile()
        try {
            nodeModulesDir.resolve("data-uri-to-buffer/dist").mkdirs()
            nodeModulesDir.resolve("data-uri-to-buffer/dist/index.js").writeText("export {}\n")

            assertEquals(
                false,
                NodeProjectManager.hasRequiredRuntimeDependencyFiles(nodeModulesDir)
            )
        } finally {
            nodeModulesDir.deleteRecursively()
        }
    }

    @Test
    fun `Brotli 及其传递依赖应纳入运行时依赖清单`() {
        val names = NodeProjectManager.bundledRuntimeDependencyNames()

        assertTrue("缺少 brotli", "brotli" in names)
        assertTrue("缺少 base64-js", "base64-js" in names)
    }

    @Test
    fun `核心声明的构建和可选依赖不应误拦截 Android 更新`() {
        val root = Files.createTempDirectory("core-runtime-deps-managed-outside").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """
                {
                  "dependencies": {
                    "chokidar": "^4.0.3",
                    "dotenv": "^16.4.7",
                    "esbuild": "^0.25.10",
                    "redis": "^5.11.0"
                  }
                }
                """.trimIndent()
            )

            assertEquals(
                emptyList<String>(),
                NodeProjectManager.collectMissingRuntimeDepsForCore(
                    coreDir = coreDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `运行目录包含匹配版本 brotli 时应通过依赖校验`() {
        val root = Files.createTempDirectory("core-runtime-deps-brotli-ready").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """{"dependencies":{"brotli":"^1.3.3"}}"""
            )
            runtimeNodeModulesDir.resolve("brotli").mkdirs()
            runtimeNodeModulesDir.resolve("brotli/package.json").writeText(
                """{"name":"brotli","version":"1.3.3"}"""
            )

            assertEquals(
                emptyList<String>(),
                NodeProjectManager.collectMissingRuntimeDepsForCore(
                    coreDir = coreDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `Brotli 深层运行文件齐全时 node_modules 应视为可用`() {
        val nodeModulesDir = Files.createTempDirectory("runtime-deps-brotli-complete").toFile()
        try {
            listOf(
                "data-uri-to-buffer/dist/index.js",
                "brotli/package.json",
                "brotli/decompress.js",
                "brotli/dec/dictionary-data.js"
            ).forEach { relativePath ->
                nodeModulesDir.resolve(relativePath).apply {
                    parentFile?.mkdirs()
                    writeText("// fixture\n")
                }
            }

            assertTrue(NodeProjectManager.hasRequiredRuntimeDependencyFiles(nodeModulesDir))
        } finally {
            nodeModulesDir.deleteRecursively()
        }
    }

    @Test
    fun `核心本地 node_modules 应优先于公共运行目录并支持 caret 范围`() {
        val root = Files.createTempDirectory("core-runtime-deps-local-pack").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """{"dependencies":{"brotli":"^1.3.3","pako":"^2.1.0"}}"""
            )
            coreDir.resolve("node_modules/brotli").mkdirs()
            coreDir.resolve("node_modules/brotli/package.json").writeText(
                """{"name":"brotli","version":"1.3.3"}"""
            )
            coreDir.resolve("node_modules/pako").mkdirs()
            coreDir.resolve("node_modules/pako/package.json").writeText(
                """{"name":"pako","version":"2.2.0"}"""
            )

            assertEquals(
                emptyList<String>(),
                NodeProjectManager.collectMissingRuntimeDepsForCore(
                    coreDir = coreDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `optionalDependencies 也参与核心运行时依赖检查`() {
        val root = Files.createTempDirectory("core-runtime-deps-optional").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """{"optionalDependencies":{"future-optional":"^1.0.0"}}"""
            )

            assertEquals(
                listOf("future-optional@^1.0.0"),
                NodeProjectManager.collectMissingRuntimeDepsForCore(
                    coreDir = coreDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
