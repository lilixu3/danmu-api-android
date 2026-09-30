package com.example.danmuapiapp.data.tunnel

import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootTunnelScriptsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val shell: String = if (File("/system/bin/sh").canExecute()) "/system/bin/sh" else "sh"
    private val platformPath: String = if (shell.startsWith("/system")) "export PATH=/system/bin:/system/xbin\n" else ""

    private fun execute(script: String): String {
        val process = ProcessBuilder(shell, "-c", platformPath + script).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output.trim()
    }

    @Test fun `stale invalid or reused pid must never receive a signal`() {
        for (scenario in listOf("stale", "invalid", "different-exe", "different-config", "different-start", "owned")) {
            val dir = temporary.newFolder(scenario)
            val proc = File(dir, "proc").apply { mkdirs() }
            val pid = if (scenario == "invalid") "-1" else "23456"
            val pidFile = File(dir, "frpc-root.pid").apply { writeText(pid) }
            val library = File(dir, "libfrpc.so").apply { writeText("dummy") }
            val entry = File(proc, pid).apply { mkdirs() }
            if (scenario != "stale") {
                Files.createSymbolicLink(File(entry, "exe").toPath(), File(dir, if (scenario == "different-exe") "unrelated" else "libfrpc.so").toPath())
                val config = if (scenario == "different-config") "/other/frpc.conf" else "$dir/frpc.conf"
                File(entry, "cmdline").writeBytes("$library\u0000-c\u0000$config\u0000".toByteArray())
                File(entry, "stat").writeText("$pid (frpc worker) S " + List(18) { "0" }.joinToString(" ") + " 12345 0")
                File(dir, "frpc-root.pid.start").writeText(if (scenario == "different-start") "999" else "12345")
            }
            // 所有 signal 都由 shell 函数拦截，仅操作临时 proc fixture。
            val calls = File(dir, "signals")
            val script = """
                kill() { echo "${'$'}*" >> ${shellQuote(calls.path)}; rm -f ${shellQuote(File(entry, "exe").path)}; }
                sleep() { :; }
                ${RootTunnelScripts.stop(dir.path, library.path, proc.path)}
            """.trimIndent()
            execute(script)
            assertFalse(pidFile.exists())
            if (scenario == "owned") assertEquals(pid, calls.readText().trim())
            else assertFalse("$scenario must not signal another process", calls.exists())
        }
    }

    @Test fun `kernel selection prefers executable download and otherwise bundled binary`() {
        val dir = temporary.newFolder("frp")
        val bundled = File(dir, "bundled.so").apply { writeText("bundled") }
        val custom = File(dir, "kernel/libfrpc.so").apply { parentFile!!.mkdirs(); writeText("custom"); setExecutable(true) }
        fun selected(): String = execute("""
            FRP_DIR=${shellQuote(dir.path)}
            FRP_LIB=${shellQuote(bundled.path)}
            ${RootTunnelScripts.selectKernel()}
            echo "${'$'}FRP_LIB"
        """.trimIndent())
        assertEquals(custom.path, selected())
        custom.setExecutable(false, false)
        assertEquals(bundled.path, selected())
    }

    @Test fun `stop finds deleted APK and duplicate processes without pid file`() {
        val app = temporary.newFolder("com.example.danmuapiapp")
        val dir = File(app, "files/frp").apply { mkdirs() }
        val proc = temporary.newFolder("proc")
        val library = "/data/app/~~new/com.example.danmuapiapp-new/lib/arm64/libfrpc.so"
        fun entry(pid: String, exe: String, arg0: String, config: String) {
            val item = File(proc, pid).apply { mkdirs() }
            Files.createSymbolicLink(File(item, "exe").toPath(), File(exe).toPath())
            File(item, "comm").writeText("libfrpc.so\n")
            File(item, "cmdline").writeBytes("$arg0\u0000-c\u0000$config\u0000".toByteArray())
            File(item, "stat").writeText("$pid (libfrpc.so) S " + List(18) { "0" }.joinToString(" ") + " 12345 0")
        }
        val oldExe = "/data/app/old==deleted==/old==deleted==/lib/arm64/libfrpc.so (deleted)"
        entry("23456", oldExe, "/data/app/~~old/com.example.danmuapiapp-old/lib/arm64/libfrpc.so", "$dir/frpc.toml")
        entry("23457", library, library, "$dir/frpc.conf")
        entry("23458", oldExe, "/data/app/~~old/another.app-old/lib/arm64/libfrpc.so", "$dir/frpc.conf")
        entry("23459", library, library, "/other/app/frpc.conf")
        val calls = File(dir, "signals")
        execute("""
            kill() { echo "${'$'}*" >> ${shellQuote(calls.path)}; rm -f ${shellQuote(proc.path)}/"${'$'}1"/exe; }
            sleep() { :; }
            ${RootTunnelScripts.stop(dir.path, library, proc.path)}
        """.trimIndent())
        assertEquals(setOf("23456", "23457"), calls.readLines().toSet())
        assertTrue(File(proc, "23458/exe").toPath().let(Files::isSymbolicLink))
        assertTrue(File(proc, "23459/exe").toPath().let(Files::isSymbolicLink))
    }

    @Test fun `failed stop keeps process record and returns failure`() {
        val dir = temporary.newFolder("failed-stop")
        val proc = File(dir, "proc").apply { mkdirs() }
        val entry = File(proc, "23456").apply { mkdirs() }
        val library = File(dir, "libfrpc.so")
        Files.createSymbolicLink(File(entry, "exe").toPath(), library.toPath())
        File(entry, "cmdline").writeBytes("$library\u0000-c\u0000$dir/frpc.conf\u0000".toByteArray())
        File(entry, "stat").writeText("23456 (frpc) S " + List(18) { "0" }.joinToString(" ") + " 12345 0")
        val pidFile = File(dir, "frpc-root.pid").apply { writeText("23456") }
        val script = "kill() { return 1; }; sleep() { :; };\n" + RootTunnelScripts.stop(dir.path, library.path, proc.path)
        val child = ProcessBuilder(shell, "-c", platformPath + script).redirectErrorStream(true).start()
        child.inputStream.close()
        assertNotEquals(0, child.waitFor())
        assertTrue(pidFile.exists())
    }

    @Test fun `control lock prevents simultaneous stop or launch`() {
        val dir = temporary.newFolder("locked")
        val owner = ProcessBuilder(shell, "-c", platformPath + """
            FRP_DIR=${shellQuote(dir.path)}
            ${RootTunnelScripts.lock()}
            echo ready
            read release
        """.trimIndent()).redirectErrorStream(true).start()
        try {
            assertEquals("ready", owner.inputStream.bufferedReader().readLine())
            val contender = ProcessBuilder(shell, "-c", platformPath + RootTunnelScripts.stop(dir.path, "/unused/libfrpc.so"))
                .redirectErrorStream(true).start()
            contender.inputStream.bufferedReader().readText()
            assertEquals(8, contender.waitFor())
        } finally {
            owner.outputStream.close()
            owner.waitFor()
        }
    }
}
