package com.example.danmuapiapp.data.service

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 最小可用 Root 命令执行器。
 *
 * Root 管理器通常会在每次新建 `su -c ...` 进程时弹出/记录一次“已授予 Root 权限”。
 * 因此这里优先复用一个长连接 su shell，把后续小命令串行投递到同一个 Root 会话，
 * 避免切页面、后台刷新、状态探测时反复触发 Root 授权通知。
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

    @Synchronized
    fun exec(command: String, timeoutMs: Long = 15000L): Result {
        // 基本安全检查：只检查空字节和回车符。命令本身可能是多行 shell 脚本，保留 \n。
        if (command.contains('\u0000') || command.contains('\r')) {
            return Result(-1, "", "Command contains invalid characters")
        }

        val normalizedTimeout = timeoutMs.coerceAtLeast(1L)
        return persistentExec(command, normalizedTimeout)
    }

    @Synchronized
    fun closeSession() {
        closePersistentSessionLocked()
    }

    private var session: PersistentSession? = null

    private fun persistentExec(command: String, timeoutMs: Long): Result {
        val current = session?.takeIf { it.isAlive } ?: run {
            closePersistentSessionLocked()
            val created = PersistentSession.start().getOrElse {
                return Result(-1, "", it.message ?: it.toString())
            }
            session = created
            created
        }

        val result = current.exec(command, timeoutMs)
        if (result.timedOut || (!current.isAlive && result.exitCode == -1)) {
            closePersistentSessionLocked()
        }
        return result
    }

    private fun closePersistentSessionLocked() {
        session?.close()
        session = null
    }

    internal fun buildSessionCommand(command: String, marker: String): String {
        return """
            printf '%s\n' '__DANMU_ROOT_BEGIN_$marker'
            (
            $command
            )
            __DANMU_ROOT_CODE=${'$'}?
            printf '\n%s:%s\n' '__DANMU_ROOT_END_$marker' "${'$'}__DANMU_ROOT_CODE"
        """.trimIndent() + "\n"
    }

    private class PersistentSession private constructor(private val proc: Process) {
        private val lines = LinkedBlockingQueue<String>()
        private val output = ByteArrayOutputStream()
        private val readerThread: Thread

        @Volatile
        private var closed = false

        @Volatile
        private var readerFailure: String? = null

        init {
            readerThread = Thread({ readLoop(proc.inputStream) }, "RootShell-session-reader")
            readerThread.isDaemon = true
            readerThread.start()
        }

        val isAlive: Boolean
            get() = !closed && isAliveCompat(proc)

        fun exec(command: String, timeoutMs: Long): Result {
            if (!isAlive) {
                return Result(-1, "", "Root shell is not alive")
            }

            val marker = UUID.randomUUID().toString().replace("-", "")
            val begin = "__DANMU_ROOT_BEGIN_$marker"
            val endPrefix = "__DANMU_ROOT_END_$marker:"
            output.reset()
            var truncated = false
            var sawBegin = false

            try {
                proc.outputStream.write(buildSessionCommand(command, marker).toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
            } catch (t: Throwable) {
                close()
                return Result(-1, "", t.message ?: t.toString())
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) {
                    close()
                    return Result(
                        exitCode = -1,
                        stdout = currentText(truncated),
                        stderr = "Root command timed out",
                        timedOut = true
                    )
                }

                val line = try {
                    lines.poll(minOf(remaining, 120L), TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    close()
                    return Result(
                        exitCode = -1,
                        stdout = currentText(truncated),
                        stderr = "Interrupted while waiting for root command",
                        timedOut = true
                    )
                }

                if (line == null) {
                    if (!isAlive) {
                        close()
                        return Result(
                            exitCode = -1,
                            stdout = currentText(truncated),
                            stderr = readerFailure ?: "Root shell exited before command completed"
                        )
                    }
                    continue
                }

                if (!sawBegin) {
                    if (line == begin) sawBegin = true
                    continue
                }

                if (line.startsWith(endPrefix)) {
                    val code = line.removePrefix(endPrefix).trim().toIntOrNull() ?: -1
                    val captured = currentText(truncated)
                    return Result(
                        exitCode = code,
                        stdout = captured,
                        stderr = if (code == 0) "" else captured
                    )
                }

                val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
                val left = MAX_CAPTURE_BYTES - output.size()
                if (left <= 0) {
                    truncated = true
                } else {
                    val writable = minOf(left, bytes.size)
                    output.write(bytes, 0, writable)
                    if (writable < bytes.size) truncated = true
                }
            }
        }

        fun close() {
            if (closed) return
            closed = true
            runCatching {
                proc.outputStream.write("exit\n".toByteArray(Charsets.UTF_8))
                proc.outputStream.flush()
            }
            runCatching { proc.outputStream.close() }
            runCatching { proc.inputStream.close() }
            runCatching { proc.errorStream.close() }
            runCatching { waitForCompat(proc, 300L) }
            if (isAliveCompat(proc)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { proc.destroyForcibly() }
                } else {
                    runCatching { proc.destroy() }
                }
            }
            runCatching { readerThread.join(500L) }
        }

        private fun readLoop(input: InputStream) {
            try {
                input.bufferedReader(Charsets.UTF_8).useLines { sequence ->
                    sequence.forEach { line ->
                        if (closed) return@forEach
                        lines.offer(line)
                    }
                }
            } catch (t: Throwable) {
                // close() 会主动关闭 proc.inputStream；Android 上阻塞中的 readLine()
                // 可能因此抛 InterruptedIOException。这个是会话关闭的预期结果，
                // 不能让 reader 线程冒泡成 App uncaughtException 日志。
                if (!closed) {
                    readerFailure = t.message ?: t.toString()
                    closed = true
                    runCatching { proc.destroy() }
                }
            }
        }

        private fun currentText(truncated: Boolean): String {
            val base = runCatching { output.toString(Charsets.UTF_8.name()) }.getOrElse { "" }
            return if (truncated) "$base\n...(truncated)" else base
        }

        companion object {
            fun start(): kotlin.Result<PersistentSession> {
                return runCatching {
                    val proc = ProcessBuilder("su")
                        .redirectErrorStream(true)
                        .start()
                    PersistentSession(proc)
                }
            }
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
}
