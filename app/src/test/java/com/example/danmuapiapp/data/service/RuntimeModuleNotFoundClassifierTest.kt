package com.example.danmuapiapp.data.service

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeModuleNotFoundClassifierTest {

    @Test
    fun `识别普通包与 scoped 包的根包名`() {
        assertEquals(
            "@scope/pkg",
            RuntimeModuleNotFoundClassifier.extractPackageName(
                "Error [ERR_MODULE_NOT_FOUND]: Cannot find package '@scope/pkg' imported from /data/core/worker.js"
            )
        )
        assertEquals(
            "lodash",
            RuntimeModuleNotFoundClassifier.extractPackageName(
                "Error: Cannot find module 'lodash/fp'\nRequire stack: /data/core/worker.js"
            )
        )
    }

    @Test
    fun `相对路径绝对路径和 Node 内置模块不判为依赖包`() {
        assertNull(RuntimeModuleNotFoundClassifier.extractPackageName("Cannot find module './local.js'"))
        assertNull(RuntimeModuleNotFoundClassifier.extractPackageName("Cannot find module '/data/local.js'"))
        assertNull(RuntimeModuleNotFoundClassifier.extractPackageName("Cannot find package 'node:fs'"))
    }

    @Test
    fun `只有核心已声明的包才能形成强制修复项`() {
        val coreDir = Files.createTempDirectory("declared-runtime-dependency").toFile()
        try {
            coreDir.resolve("package.json").writeText(
                """{"dependencies":{"opencc-js":"^1.4.1","@scope/pkg":"~2.0.0"}}"""
            )

            assertEquals(
                "opencc-js@^1.4.1",
                NodeProjectManager.runtimeDependencyRequirementForCore(coreDir, "opencc-js")
            )
            assertEquals(
                "@scope/pkg@~2.0.0",
                NodeProjectManager.runtimeDependencyRequirementForCore(coreDir, "@scope/pkg")
            )
            assertNull(NodeProjectManager.runtimeDependencyRequirementForCore(coreDir, "undeclared"))
            assertNull(NodeProjectManager.runtimeDependencyRequirementForCore(coreDir, "../escape"))
        } finally {
            coreDir.deleteRecursively()
        }
    }
}
