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

        val pidFile = parsed[ARG_PID_FILE]
        if (!pidFile.isNullOrBlank()) {
            runCatching { writePidFile(pidFile) }
        }

        val startedAtFile = parsed[ARG_STARTED_AT_FILE]
        if (!startedAtFile.isNullOrBlank()) {
            runCatching { writeStartedAtFile(startedAtFile) }
        }

        try {
            NodeBridge.startNodeWithArguments(arrayOf("node", entry))
        } catch (t: Throwable) {
            System.err.println("RootNodeEntry crashed: ${t.message}")
            t.printStackTrace()
        } finally {
            if (!pidFile.isNullOrBlank()) {
                runCatching { File(pidFile).delete() }
            }
            if (!startedAtFile.isNullOrBlank()) {
                runCatching { File(startedAtFile).delete() }
            }
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
    private fun writeStartedAtFile(path: String) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeText(System.currentTimeMillis().toString() + "\n", Charsets.UTF_8)
        runCatching { f.setReadable(true, false) }
        runCatching { f.setWritable(true, true) }
    }
}
