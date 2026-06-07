package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellTest {

    @Test
    fun `persistent root session wraps commands in subshell so exit does not close session`() {
        val payload = RootShell.buildSessionCommand(
            command = """
                printf 'before-exit\n'
                exit 7
            """.trimIndent(),
            marker = "TESTMARKER"
        ) + "printf 'after-wrapper\n'\n"

        val process = ProcessBuilder("sh", "-c", payload)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        val exitCode = process.waitFor()

        assertEquals(output, 0, exitCode)
        assertTrue(output, output.contains("__DANMU_ROOT_BEGIN_TESTMARKER"))
        assertTrue(output, output.contains("before-exit"))
        assertTrue(output, output.contains("__DANMU_ROOT_END_TESTMARKER:7"))
        assertTrue(output, output.contains("after-wrapper"))
    }

    @Test
    fun `persistent root session payload keeps stderr observable in merged output`() {
        val payload = RootShell.buildSessionCommand(
            command = "printf 'visible-error\n' >&2",
            marker = "ERRMARKER"
        )

        val process = ProcessBuilder("sh", "-c", payload)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        val exitCode = process.waitFor()

        assertEquals(output, 0, exitCode)
        assertTrue(output, output.contains("visible-error"))
        assertTrue(output, output.contains("__DANMU_ROOT_END_ERRMARKER:0"))
    }
}
