package com.example.danmuapiapp.data.tunnel

import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class RootTunnelLogSinkTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `root log FIFO caps size exits on EOF and releases the control lock`() {
        assumeTrue("Android ELF integration runs on Termux", File("/system/bin/sh").canExecute())
        val helper = listOf(File("../runtime/outbound/arm64-v8a/libdanmu_outbound.so"), File("runtime/outbound/arm64-v8a/libdanmu_outbound.so")).firstOrNull { it.isFile }?.canonicalFile
        assumeTrue("needs prepared helper", helper != null && helper.canExecute())
        val dir = temporary.newFolder("log-sink")
        val log = File(dir, "frpc.log")
        val script = """
            export PATH=/system/bin:/system/xbin
            FRP_DIR=${shellQuote(dir.path)}
            FRP_LOG=${shellQuote(log.path)}
            FRP_LOG_HELPER=${shellQuote(helper!!.path)}
            ${RootTunnelScripts.lock()}
            ${RootTunnelScripts.logSink()}
            (dd if=/dev/zero bs=8192 count=384 2>/dev/null; printf 'LATEST') > "${'$'}FRP_PIPE"
            rm -f "${'$'}FRP_PIPE"
            wait "${'$'}FRP_LOG_PID"
            echo done
        """.trimIndent()
        val process = ProcessBuilder("/system/bin/sh", "-c", script).redirectErrorStream(true).start()
        try {
            assertTrue("sink did not exit at EOF", process.waitFor(10, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertTrue(log.length() <= 1024 * 1024 + 8192)
            assertTrue(log.readText().endsWith("LATEST"))
            val lock = ProcessBuilder("/system/bin/sh", "-c", "export PATH=/system/bin:/system/xbin; FRP_DIR=${shellQuote(dir.path)}; ${RootTunnelScripts.lock()}").redirectErrorStream(true).start()
            assertTrue(lock.waitFor(3, TimeUnit.SECONDS))
            assertEquals(lock.inputStream.bufferedReader().readText(), 0, lock.exitValue())
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}
