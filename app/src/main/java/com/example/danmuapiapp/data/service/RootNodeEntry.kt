package com.example.danmuapiapp.data.service

import android.annotation.SuppressLint
import android.os.Process
import com.example.danmuapiapp.NodeBridge
import java.io.File

/**
 * Root 模式入口：由 app_process 直接拉起。
 */
object RootNodeEntry {

    private const val ARG_ENTRY = "--entry"
    private const val ARG_PID_FILE = "--pidfile"
    private const val ARG_STARTED_AT_FILE = "--started-at-file"

    @JvmStatic
    fun main(args: Array<String>) {
        val parsed = parseArgs(args)
        val entry = parsed[ARG_ENTRY]
        if (entry.isNullOrBlank()) {
            System.err.println("RootNodeEntry: missing --entry")
            return
        }

        val runtimeRoot = File(entry).parentFile?.parentFile
        val pidFiles = listOfNotNull(parsed[ARG_PID_FILE]?.takeIf { it.isNotBlank() }?.let(::File),
            runtimeRoot?.resolve("root_node.pid")).distinctBy { it.absolutePath }
        val startedAtFiles = listOfNotNull(parsed[ARG_STARTED_AT_FILE]?.takeIf { it.isNotBlank() }?.let(::File),
            runtimeRoot?.resolve("root_node_started_at_ms")).distinctBy { it.absolutePath }
        val processPid = Process.myPid()
        val startedAt = System.currentTimeMillis()
        pidFiles.forEach { file -> runCatching { writePidFile(file.absolutePath) } }
        startedAtFiles.forEach { file -> runCatching { writeStartedAtFile(file.absolutePath, startedAt) } }

        try {
            // Node 24 运行时要求：Root 模式同样在启动前提供 TMPDIR/HOME。
            // HOME 锚定运行时根目录，避免 os.homedir() 回退到 su 环境的 /。
            NodeRuntimeEnv.install(
                tmpDir = runtimeRoot?.resolve("cache/tmp") ?: File("/data/local/tmp"),
                homeDir = runtimeRoot
            )
            NodeBridge.startNodeWithArguments(arrayOf("node", entry))
        } catch (t: Throwable) {
            System.err.println("RootNodeEntry crashed: ${t.message}")
            t.printStackTrace()
        } finally {
            // An older exiting runtime must not erase a newer runtime's markers.
            pidFiles.forEach { file ->
                runCatching {
                    if (file.readText(Charsets.UTF_8).trim().toIntOrNull() == processPid) {
                        file.delete()
                        startedAtFiles.filter { it.parentFile == file.parentFile }.forEach { it.delete() }
                    }
                }
            }
            // app_process may keep Binder/runtime threads after Node returns.
            // The standalone process has completed and must not become an orphan.
            Process.killProcess(processPid)
        }
    }

    private fun parseArgs(args: Array<String>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val key = args[i]
            if (key == ARG_ENTRY || key == ARG_PID_FILE || key == ARG_STARTED_AT_FILE) {
                val value = if (i + 1 < args.size) args[i + 1] else ""
                out[key] = value
                i += 2
            } else {
                i++
            }
        }
        return out
    }

    @SuppressLint("SetWorldReadable")
    private fun writePidFile(path: String) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeText(Process.myPid().toString() + "\n", Charsets.UTF_8)
        runCatching { f.setReadable(true, false) }
        runCatching { f.setWritable(true, true) }
    }

    @SuppressLint("SetWorldReadable")
    private fun writeStartedAtFile(path: String, startedAt: Long) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeText(startedAt.toString() + "\n", Charsets.UTF_8)
        runCatching { f.setReadable(true, false) }
        runCatching { f.setWritable(true, true) }
    }
}
