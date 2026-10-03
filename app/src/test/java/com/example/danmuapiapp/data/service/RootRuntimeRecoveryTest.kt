package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RootRuntimeRecoveryTest {
    private val mainClass = "com.example.danmuapiapp.data.service.RootNodeEntry"
    private val project = "/data/adb/danmuapi_runtime/pkg/nodejs-project"

    private fun fixture(root: File, pid: Int, uid: Int = 0, home: String = project,
                        exe: String = "/system/bin/app_process64", cmd: String = "danmuapi_rootnode   ",
                        ticks: Long = 700L, state: String = "S") {
        val dir = File(root, "$pid").apply { mkdirs() }
        File(dir, "status").writeText("Name:\tMainThread\nUid:\t$uid\t$uid\t$uid\t$uid\n")
        File(dir, "cmdline").writeBytes((cmd + "\u0000").toByteArray())
        Files.createSymbolicLink(File(dir, "cwd").toPath(), File(home).toPath())
        Files.createSymbolicLink(File(dir, "exe").toPath(), File(exe).toPath())
        File(dir, "stat").writeText("$pid (Main Thread) $state " + List(18) { "0" }.joinToString(" ") + " $ticks 0\n")
    }

    private fun shell(script: String): String {
        val process = ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText()
        assertEquals(text, 0, process.waitFor())
        return text
    }

    @Test fun missingAppMarkersAndRewrittenArgvDoNotPreventDiscovery() {
        val root = Files.createTempDirectory("root-proc-").toFile()
        try {
            fixture(root, 501)
            fixture(root, 502, cmd = mainClass, ticks = 900)
            val processes = RootRuntimeRecovery.parseProcesses(shell(RootRuntimeRecovery.buildScanShell(project, mainClass, root.path)))
            assertEquals(listOf(RootRuntimeRecovery.Process(501, 700), RootRuntimeRecovery.Process(502, 900)), processes)
        } finally { root.deleteRecursively() }
    }

    @Test fun wrongUidProjectExecutableNameAndZombieAreRejected() {
        val root = Files.createTempDirectory("root-proc-").toFile()
        try {
            fixture(root, 501, uid = 10123)
            fixture(root, 502, home = "$project-other")
            fixture(root, 503, exe = "/data/local/tmp/node")
            fixture(root, 504, cmd = "unrelated-daemon")
            fixture(root, 505, state = "Z")
            fixture(root, 506)
            val processes = RootRuntimeRecovery.parseProcesses(shell(RootRuntimeRecovery.buildScanShell(project, mainClass, root.path)))
            assertEquals(listOf(RootRuntimeRecovery.Process(506, 700)), processes)
        } finally { root.deleteRecursively() }
    }

    @Test fun reusedPidAndForeignProcessesNeverReceiveSignals() {
        val root = Files.createTempDirectory("root-proc-").toFile()
        try {
            fixture(root, 501, ticks = 900)
            fixture(root, 502, home = "/data/adb/other/nodejs-project")
            fixture(root, 503, ticks = 700)
            // Override kill with a recording function; no real process is signaled.
            val script = "kill() { printf 'SIGNAL %s %s\\n' \"\$1\" \"\$2\"; }\n" +
                RootRuntimeRecovery.buildSignalShell(project, mainClass, listOf(
                    RootRuntimeRecovery.Process(501, 700), RootRuntimeRecovery.Process(502, 700),
                    RootRuntimeRecovery.Process(503, 700)), false, root.path)
            assertEquals("SIGNAL -TERM 503\n", shell(script))
        } finally { root.deleteRecursively() }
    }

    @Test fun shellQuotesPathsAndRejectsMalformedProcessRecords() {
        val root = Files.createTempDirectory("root-proc-'quote-").toFile()
        try {
            val home = "$project'quoted"
            fixture(root, 501, home = home)
            assertEquals(1, RootRuntimeRecovery.parseProcesses(shell(RootRuntimeRecovery.buildScanShell(home, mainClass, root.path))).size)
            assertTrue(RootRuntimeRecovery.parseProcesses("OWNED_ROOT 0 700\nOWNED_ROOT -12 800\nOWNED_ROOT 13 x\nnoise").isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun oldRomWithoutPsColumnsFallsBackToVerifiedProcScan() {
        val root = Files.createTempDirectory("root-proc-").toFile()
        try {
            fixture(root, 501)
            val script = "ps() { return 1; }\n" +
                RootRuntimeRecovery.buildScanShell(project, mainClass, root.path)
                    .replace("= '/proc'", "= '" + root.path + "'")
            assertEquals(listOf(RootRuntimeRecovery.Process(501, 700)), RootRuntimeRecovery.parseProcesses(shell(script)))
        } finally { root.deleteRecursively() }
    }

    @Test fun reinstallWithAnUnresponsiveListenerTriggersOneRecoveryButExistingNormalModeDoesNot() {
        assertTrue(RootRuntimeRecovery.shouldTryAfterDataLoss(true, false, false, true))
        assertTrue(RootRuntimeRecovery.shouldTryAfterDataLoss(true, false, true, false))
        assertFalse(RootRuntimeRecovery.shouldTryAfterDataLoss(false, false, false, true))
        assertFalse(RootRuntimeRecovery.shouldTryAfterDataLoss(true, true, true, true))
        assertFalse(RootRuntimeRecovery.shouldTryAfterDataLoss(true, false, false, false))
    }

    @Test fun exportReadOnlyDeviceDiscoveryScript() {
        val target = File("build/tmp/root-reinstall-discovery.sh")
        target.parentFile?.mkdirs()
        target.writeText(RootRuntimeRecovery.buildScanShell(
            "/data/adb/danmuapi_runtime/com.example.danmuapiapp/nodejs-project", mainClass))
        assertFalse(target.readText().contains("kill -"))
    }
}
