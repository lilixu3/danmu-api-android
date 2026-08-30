package com.example.danmuapiapp.desktop.node

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Explicit user-action process termination for a verified Desktop-owned Node child. */
internal object WindowsProcessTerminator {
    fun terminateNodeTree(
        pid: Long,
        expectedNodeExe: File,
        expectedScriptDir: File,
    ): Result<Unit> {
        if (pid <= 0L) return Result.failure(IOException("后台 Node PID 无效：$pid"))
        if (pid == ProcessHandle.current().pid()) {
            return Result.failure(IOException("拒绝终止当前 Desktop 进程"))
        }
        val handle = ProcessHandle.of(pid).orElse(null)
            ?: return Result.failure(IOException("后台 Node 进程不存在：pid=$pid"))
        val info = handle.info()
        val command = info.command().orElse(null)
            ?: return Result.failure(IOException("无法验证后台 Node 可执行文件：pid=$pid"))
        val actualExe = runCatching { File(command).canonicalPath }.getOrElse {
            return Result.failure(IOException("无法解析后台 Node 可执行文件：$command", it))
        }
        val expectedExe = runCatching { expectedNodeExe.canonicalPath }.getOrElse {
            return Result.failure(IOException("无法解析 Desktop Node 可执行文件：${expectedNodeExe.absolutePath}", it))
        }
        if (!actualExe.equals(expectedExe, ignoreCase = true)) {
            return Result.failure(IOException("后台 PID=$pid 不是当前 Desktop 的 node.exe"))
        }
        val commandLine = info.commandLine().orElse(null)
            ?: return Result.failure(IOException("无法验证后台 Node 命令行：pid=$pid"))
        val expectedEntry = File(expectedScriptDir, "main.js").canonicalPath
        if (!commandLine.contains(expectedEntry, ignoreCase = true)) {
            return Result.failure(IOException("后台 PID=$pid 未运行当前 Desktop 运行目录的 main.js"))
        }

        val killer = try {
            ProcessBuilder("taskkill", "/PID", pid.toString(), "/T", "/F")
                .redirectErrorStream(true)
                .start()
        } catch (error: Throwable) {
            return Result.failure(IOException("无法终止后台 Node 进程树：${error.message}", error))
        }
        val finished = killer.waitFor(10, TimeUnit.SECONDS)
        if (!finished) {
            killer.destroyForcibly()
            return Result.failure(IOException("终止后台 Node 进程树超时：pid=$pid"))
        }
        if (killer.exitValue() != 0) {
            val output = runCatching { killer.inputStream.bufferedReader().readText().trim() }.getOrNull().orEmpty()
            return Result.failure(IOException("终止后台 Node 进程树失败：pid=$pid${output.takeIf { it.isNotBlank() }?.let { "，$it" } ?: ""}"))
        }
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (!handle.isAlive) return Result.success(Unit)
            Thread.sleep(100)
        }
        return Result.failure(IOException("后台 Node 进程终止命令已返回，但 PID=$pid 仍存活"))
    }
}
