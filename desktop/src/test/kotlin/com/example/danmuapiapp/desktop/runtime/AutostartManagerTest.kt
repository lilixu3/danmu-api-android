package com.example.danmuapiapp.desktop.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutostartManagerTest {

    @Test
    fun devRunIsNotSupported() {
        // 测试进程是 java.exe，不具备独立可执行入口
        assertEquals(null, AutostartManager.resolveExecutablePath())
        assertFalse(AutostartManager.isSupported())
    }

    @Test
    fun enableReportsUnsupportedInDevRun() {
        val error = AutostartManager.enable()
        assertTrue(error.orEmpty().contains("仅打包版"))
    }

    @Test
    fun registryRoundTripWithSpacedPath() {
        // 与 AutostartManager 相同的写入方式做一次真实读写回环（HKCU，无管理员要求），
        // 覆盖"路径含空格 + 嵌套引号"的既有缺陷场景（reg.exe 无法携带该值，须走 PowerShell）
        val fakeValue = "\"C:\\Program Files\\Danmu Api\\DanmuApiDesktop.exe\" --autostart"
        val (addCode, addOut) = AutostartManager.writeRunValue(fakeValue, name = "DanmuApiDesktopTest")
        assertEquals("写入应成功：$addOut", 0, addCode)
        try {
            val (queryCode, queryOut) = runReg(
                "query", "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", "DanmuApiDesktopTest",
            )
            assertEquals(0, queryCode)
            assertTrue(queryOut.contains("DanmuApiDesktopTest"))
            assertTrue(queryOut.contains("--autostart"))
            assertTrue(queryOut.contains("Danmu Api"))
        } finally {
            runReg("delete", "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "DanmuApiDesktopTest", "/f")
        }
    }

    @Test
    fun autostartArgDetection() {
        assertTrue(AutostartManager.isAutostartLaunch(arrayOf("--autostart")))
        assertFalse(AutostartManager.isAutostartLaunch(emptyArray()))
    }

    private fun runReg(vararg command: String): Pair<Int, String> {
        val process = ProcessBuilder("reg", *command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }
}
