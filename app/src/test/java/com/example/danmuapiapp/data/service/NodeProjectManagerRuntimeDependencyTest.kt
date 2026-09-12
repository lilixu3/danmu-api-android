package com.example.danmuapiapp.data.service

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `新工作目录解压前用内置版本预判依赖`() {
        val root = Files.createTempDirectory("core-runtime-deps-bundled-fallback").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """{"dependencies":{"brotli":"^1.3.3","future-package":"^1.0.0"}}"""
            )

            assertEquals(
                listOf("future-package@^1.0.0"),
                NodeProjectManager.collectMissingRuntimeDepsForCoreAgainstBundledRuntime(coreDir)
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
    fun `未列入清单但运行目录存在匹配版本时应通过依赖校验`() {
        val root = Files.createTempDirectory("core-runtime-deps-unknown-ready").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """{"dependencies":{"future-runtime-package":"^2.0.0"}}"""
            )
            runtimeNodeModulesDir.resolve("future-runtime-package").mkdirs()
            runtimeNodeModulesDir.resolve("future-runtime-package/package.json").writeText(
                """{"name":"future-runtime-package","version":"2.0.0"}"""
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
    fun `App 内置直接依赖应纳入运行时依赖清单`() {
        val names = NodeProjectManager.bundledRuntimeDependencyNames()

        assertTrue("缺少 brotli", "brotli" in names)
        assertTrue("缺少 base64-js", "base64-js" in names)
        assertTrue("缺少 @dan-uni/dan-any", "@dan-uni/dan-any" in names)
        assertTrue("缺少 opencc-js", "opencc-js" in names)
    }

    @Test
    fun `web-streams-polyfill 不应再被视为内置依赖`() {
        val names = NodeProjectManager.bundledRuntimeDependencyNames()
        assertFalse("web-streams-polyfill 已从运行时排除，不能再列入期望清单", "web-streams-polyfill" in names)
        assertEquals(
            NodeProjectManager.bundledRuntimeDependencyManifest().keys,
            NodeProjectManager.bundledRuntimeDependencySentinels().keys
        )
    }

    @Test
    fun `构建排除清单不能出现在内置依赖期望清单中`() {
        val buildScript = resolveProjectFile("app/build.gradle.kts")
        val text = buildScript.readText(Charsets.UTF_8)
        val excluded = Regex(
            """androidRuntimeExcludedNodeModules\s*=\s*setOf\(([\s\S]*?)\)"""
        ).find(text)?.groupValues?.getOrNull(1)
            ?.let { block ->
                Regex("\"([^\"]+)\"").findAll(block)
                    .map { it.groupValues[1] }
                    .toSet()
            }
            .orEmpty()
        assertTrue("未从 ${buildScript.path} 解析到 androidRuntimeExcludedNodeModules", excluded.isNotEmpty())
        assertEquals(
            "NodeProjectManager 的 Android 排除清单与 build.gradle.kts 不一致",
            excluded,
            NodeProjectManager.bundledRuntimeDependencyExclusions()
        )
        val conflicts = NodeProjectManager.bundledRuntimeDependencyManifest().keys.intersect(excluded)
        assertTrue("内置依赖与构建排除清单冲突：$conflicts", conflicts.isEmpty())
    }

    @Test
    fun `核心旧清单声明的 Android 排除依赖不应再要求修复`() {
        val root = Files.createTempDirectory("core-runtime-deps-android-excluded").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """
                {
                  "dependencies": {
                    "brotli": "^1.3.3",
                    "web-streams-polyfill": "3.3.3"
                  }
                }
                """.trimIndent()
            )
            runtimeNodeModulesDir.resolve("brotli").mkdirs()
            runtimeNodeModulesDir.resolve("brotli/package.json").writeText(
                """{"name":"brotli","version":"1.3.3"}"""
            )
            runtimeNodeModulesDir.resolve("brotli/decompress.js").writeText("module.exports = {}\n")
            runtimeNodeModulesDir.resolve("brotli/dec").mkdirs()
            runtimeNodeModulesDir.resolve("brotli/dec/dictionary-data.js").writeText("module.exports = {}\n")

            assertEquals(
                emptyList<String>(),
                NodeProjectManager.collectMissingRuntimeDepsForCore(coreDir, runtimeNodeModulesDir)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `内置依赖清单中的包和哨兵文件必须实际存在于运行时资产`() {
        val nodeModulesDir = resolveProjectFile("app/src/main/assets/nodejs-project/node_modules")
        assertTrue("缺少内置 node_modules：${nodeModulesDir.absolutePath}", nodeModulesDir.isDirectory)
        val manifest = NodeProjectManager.bundledRuntimeDependencyManifest()
        val sentinels = NodeProjectManager.bundledRuntimeDependencySentinels()
        assertEquals(manifest.keys, sentinels.keys)
        manifest.forEach { (name, version) ->
            val packageDir = File(nodeModulesDir, name)
            assertTrue("内置依赖缺失：$name@$version", File(packageDir, "package.json").isFile)
            sentinels[name].orEmpty().forEach { relativePath ->
                assertTrue(
                    "内置依赖哨兵缺失：$name/$relativePath",
                    File(packageDir, relativePath).isFile
                )
            }
        }
    }

    private fun resolveProjectFile(relativePath: String): File {
        return sequenceOf(File(relativePath), File("../$relativePath"))
            .firstOrNull { it.exists() }
            ?: File(relativePath)
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
            runtimeNodeModulesDir.resolve("brotli/decompress.js").writeText("module.exports = {}\n")
            runtimeNodeModulesDir.resolve("brotli/dec").mkdirs()
            runtimeNodeModulesDir.resolve("brotli/dec/dictionary-data.js").writeText("module.exports = {}\n")

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
    fun `损坏的核心私有依赖不能由共享目录同名包掩盖`() {
        val root = Files.createTempDirectory("core-runtime-deps-shadowed").toFile()
        try {
            val coreDir = root.resolve("core").apply { mkdirs() }
            val runtimeNodeModulesDir = root.resolve("node_modules").apply { mkdirs() }
            coreDir.resolve("package.json").writeText(
                """{"dependencies":{"brotli":"^1.3.3"}}"""
            )
            coreDir.resolve("node_modules/brotli").mkdirs()
            coreDir.resolve("node_modules/brotli/package.json").writeText(
                """{"name":"brotli","version":"1.3.3"}"""
            )
            runtimeNodeModulesDir.resolve("brotli/dec").mkdirs()
            runtimeNodeModulesDir.resolve("brotli/package.json").writeText(
                """{"name":"brotli","version":"1.3.3"}"""
            )
            runtimeNodeModulesDir.resolve("brotli/decompress.js").writeText("module.exports = {}\n")
            runtimeNodeModulesDir.resolve("brotli/dec/dictionary-data.js").writeText("module.exports = {}\n")

            assertEquals(
                listOf("brotli@^1.3.3"),
                NodeProjectManager.collectMissingRuntimeDepsForCore(coreDir, runtimeNodeModulesDir)
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
                """{"dependencies":{"future-runtime-package":"^2.0.0"}}"""
            )
            coreDir.resolve("node_modules/future-runtime-package").mkdirs()
            coreDir.resolve("node_modules/future-runtime-package/package.json").writeText(
                """{"name":"future-runtime-package","version":"2.4.1"}"""
            )

            assertEquals(
                emptyList<String>(),
                NodeProjectManager.collectMissingRuntimeDepsForCore(coreDir, runtimeNodeModulesDir)
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
                NodeProjectManager.collectMissingRuntimeDepsForCore(coreDir, runtimeNodeModulesDir)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `Root 开机快检清单只记录实际运行依赖并保持排序`() {
        val root = Files.createTempDirectory("core-runtime-requirements").toFile()
        try {
            root.resolve("package.json").writeText(
                """{"dependencies":{"z-runtime":"^1.0.0","redis":"^5.0.0","@scope/a":"1.2.3"},"optionalDependencies":{"a-runtime":"~2.0.0"}}"""
            )

            NodeProjectManager.writeRuntimeDependencyRequirements(root)

            assertEquals(
                "@scope/a\na-runtime\nz-runtime\n",
                root.resolve(NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE).readText()
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
