package com.example.danmuapiapp.data.service

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePathResolverTest {

    @Test
    fun `规范化会把 nodejs-project 上溯到工作目录根`() {
        val base = Files.createTempDirectory("runtime-path-normalize").toFile()
        val project = File(base, "nodejs-project")
        assertTrue(project.mkdirs())
        assertEquals(base.canonicalFile, RuntimePathResolver.normalizeBaseDir(project))
        assertEquals(base.canonicalFile, RuntimePathResolver.normalizeBaseDir(project.path))
    }

    @Test
    fun `工作目录身份去掉末尾分隔符并对根目录保持有效`() {
        assertEquals("/work/one", RuntimePathResolver.workDirIdentity(File("/work/./one/../one")))
        assertEquals(File.separator, RuntimePathResolver.workDirIdentity(File(File.separator)))
    }

    @Test
    fun `互相包含的工作目录判定为重叠而同目录不算`() {
        val outer = Files.createTempDirectory("runtime-path-overlap").toFile()
        val inner = File(outer, "nested")
        assertTrue(inner.mkdirs())
        val sibling = Files.createTempDirectory("runtime-path-sibling").toFile()
        try {
            assertFalse(RuntimePathResolver.workDirectoriesOverlap(outer, outer))
            assertTrue(RuntimePathResolver.workDirectoriesOverlap(outer, inner))
            assertTrue(RuntimePathResolver.workDirectoriesOverlap(inner, outer))
            assertFalse(RuntimePathResolver.workDirectoriesOverlap(outer, sibling))
        } finally {
            outer.deleteRecursively()
            sibling.deleteRecursively()
        }
    }

    @Test
    fun `写入探针会真实读写并清理临时文件`() {
        val directory = Files.createTempDirectory("runtime-path-probe").toFile()
        val regularFile = File(directory, "file.txt").apply { writeText("x") }
        try {
            assertTrue(RuntimePathResolver.verifyDirectoryWritable(directory))
            assertFalse(RuntimePathResolver.verifyDirectoryWritable(regularFile))
            assertEquals(listOf("file.txt"), directory.list()?.sorted())
        } finally {
            directory.deleteRecursively()
        }
    }
}

