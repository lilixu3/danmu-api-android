package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreDirWatchPolicyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `core payloads and generated directories are ignored at any depth`() {
        val root = folder.newFolder("work")
        listOf(
            "danmu_api_stable",
            "danmu_api_dev/package.json",
            "danmu_api_custom/logs/x.log",
            ".app_version",
            "logs/app.log",
            ".cache/chunk",
            "node_modules/axios/index.js"
        ).forEach { relative ->
            assertTrue(relative, CoreDirWatchPolicy.shouldIgnore(root, File(root, relative)))
        }
    }

    @Test fun `ordinary project files and the root itself remain observable`() {
        val root = folder.newFolder("work")
        assertFalse(CoreDirWatchPolicy.shouldIgnore(root, root))
        assertFalse(CoreDirWatchPolicy.shouldIgnore(root, File(root, "package.json")))
        assertFalse(CoreDirWatchPolicy.shouldIgnore(root, File(root, "configs/prod.env")))
        assertFalse(CoreDirWatchPolicy.shouldIgnore(root, File(root, "danmu_api_stable_notes.txt")))
    }

    @Test fun `relative paths are canonical and use forward slashes`() {
        val root = folder.newFolder("work")
        val nested = File(root, "configs/prod.env").apply { parentFile?.mkdirs(); writeText("x") }
        assertEquals("configs/prod.env", CoreDirWatchPolicy.relativePath(root, nested))
    }
}
