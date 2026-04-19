package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertFalse
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
}
