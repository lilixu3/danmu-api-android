package com.example.danmuapiapp.desktop.runtime

import java.io.File

/**
 * 把打进应用 jar 的随包运行时资源（runtime 目录，清单文件 runtime-manifest.txt）
 * 解压到可写数据目录。首启或缺失时执行；目标目录会整体重建。
 */
object ClasspathRuntimeExtractor {

    private const val MANIFEST = "runtime-manifest.txt"
    private const val PREFIX = "runtime/"

    fun isRuntimeExtracted(targetDir: File): Boolean {
        return File(targetDir, "nodejs-project/main.js").isFile && File(targetDir, "node.exe").isFile
    }

    fun extract(targetDir: File): Int {
        val loader = Thread.currentThread().contextClassLoader
        val manifestStream = loader.getResourceAsStream(MANIFEST)
            ?: error("classpath 缺少 $MANIFEST（开发运行请先执行 :desktop:processResources）")
        val entries = manifestStream.use { input ->
            input.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        }
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        entries.forEach { entry ->
            require(entry.startsWith(PREFIX) && !entry.contains("..")) {
                "随包资源清单包含非法路径: $entry"
            }
            val output = File(targetDir, entry.removePrefix(PREFIX))
            output.parentFile?.mkdirs()
            val input = loader.getResourceAsStream(entry)
                ?: error("随包资源缺失: $entry")
            input.use { source -> output.outputStream().use { source.copyTo(it) } }
        }
        return entries.size
    }
}
