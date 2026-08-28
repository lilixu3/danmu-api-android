package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ClasspathRuntimeExtractorTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun extractsRuntimeFromTestClasspath() {
        // 测试 classpath 包含 main resources（processResources 产物）
        assumeTrue(
            "缺少 runtime-manifest.txt（先执行 :desktop:processResources）",
            Thread.currentThread().contextClassLoader.getResource("runtime-manifest.txt") != null,
        )
        val target = File(temp.root, "runtime")
        val count = ClasspathRuntimeExtractor.extract(target)
        assertTrue("至少应解压入口与清单文件", count > 0)
        assertTrue(File(target, "nodejs-project/main.js").isFile)
        assertTrue(ClasspathRuntimeExtractor.isRuntimeExtracted(target))
    }
}
