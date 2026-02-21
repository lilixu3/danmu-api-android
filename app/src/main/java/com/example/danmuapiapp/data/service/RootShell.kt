package com.example.danmuapiapp.data.service

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 最小可用 Root 命令执行器。
 */
object RootShell {

    private const val MAX_CAPTURE_BYTES = 256 * 1024

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false
    ) {
        val ok: Boolean get() = !timedOut && exitCode == 0
    }

    fun hasRoot(timeoutMs: Long = 3000L): Boolean {
        return exec("id", timeoutMs).ok
    }

    fun exec(command: String, timeoutMs: Long = 15000L): Result {
        // 基本安全检查：只检查空字节和回车符
        if (command.contains('\u0000') || command.contains('\r')) {
            return Result(-1, "", "Command contains invalid characters")
        }

        return try {
            val proc = ProcessBuilder("su", "-c", command).start()

            val stdout = StreamCollector()
            val stderr = StreamCollector()

            val outThread = Thread({ stdout.collect(proc.inputStream) }, "RootShell-stdout")
            val errThread = Thread({ stderr.collect(proc.errorStream) }, "RootShell-stderr")
            outThread.isDaemon = true
            errThread.isDaemon = true
            outThread.start()
            errThread.start()

            val finished = waitForCompat(proc, timeoutMs)
            if (!finished) {
                runCatching { proc.destroy() }
                runCatching { waitForCompat(proc, 300L) }
                if (isAliveCompat(proc)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching { proc.destroyForcibly() }
                    } else {
                        runCatching { proc.destroy() }
                    }
                }
            }

            runCatching { proc.outputStream.close() }
            runCatching { proc.inputStream.close() }
            runCatching { proc.errorStream.close() }

            runCatching { outThread.join(800) }
            runCatching { errThread.join(800) }

            val code = if (finished) runCatching { proc.exitValue() }.getOrElse { -1 } else -1
            Result(
                exitCode = code,
                stdout = stdout.text,
                stderr = stderr.text,
                timedOut = !finished
            )
        } catch (t: Throwable) {
            Result(-1, "", t.message ?: t.toString())
        }
    }

    private fun waitForCompat(proc: Process, timeoutMs: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isAliveCompat(proc)) return true
            runCatching { Thread.sleep(50) }
        }
        return !isAliveCompat(proc)
    }

    private fun isAliveCompat(proc: Process): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            proc.isAlive
        } else {
            try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
    }

    private class StreamCollector {
        private val output = ByteArrayOutputStream()
        @Volatile
        private var truncated = false

        fun collect(input: InputStream) {
            val buffer = ByteArray(4096)
            while (true) {
                val n = runCatching { input.read(buffer) }.getOrElse { -1 }
                if (n <= 0) break

                val left = MAX_CAPTURE_BYTES - output.size()
                if (left <= 0) {
                    truncated = true
                    continue
                }

                val writable = minOf(left, n)
                runCatching { output.write(buffer, 0, writable) }
                if (writable < n) truncated = true
            }
        }

        val text: String
            get() {
                val base = runCatching { output.toString(Charsets.UTF_8.name()) }.getOrElse { "" }
                return if (truncated) "$base\n...(truncated)" else base
            }
    }
}
