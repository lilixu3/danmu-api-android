package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopPathsTest {

    @Test
    fun pathsLiveUnderRoot() {
        val root = File("build/tmp-paths-test")
        val paths = DesktopPaths(root)
        assertEquals(File(root, "runtime"), paths.runtimeDir)
        assertEquals(File(root, "data"), paths.dataDir)
        assertEquals(File(root, "logs"), paths.logsDir)
        assertEquals(File(root, "core-cache"), paths.coreCacheDir)
    }

    @Test
    fun defaultRootIsNamedDanmuApi() {
        // 不触碰真实目录，仅验证目录命名约定（%LOCALAPPDATA%\DanmuApi）
        val paths = DesktopPaths()
        assertEquals("DanmuApi", paths.root.name)
    }

    @Test
    fun overrideTakesPrecedence() {
        val override = File("build/tmp-paths-override")
        assertEquals(override, DesktopPaths(override).root)
        assertTrue(true)
    }
}
