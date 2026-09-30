package com.example.danmuapiapp.data.tunnel

import java.io.File
import java.io.FileOutputStream

/** 临时文件验证完成后再停止旧进程，重命名替换，失败时恢复旧内核及版本。 */
internal fun installFrpcKernel(
    kernel: File,
    version: String,
    target: File,
    versionFile: File,
    wasRunning: Boolean,
    verify: (File) -> Unit,
    stop: () -> TunnelActionResult,
    start: () -> TunnelActionResult
): TunnelActionResult {
    require(kernel.isFile && kernel.length() > 0) { "下载内核为空" }
    val directory = requireNotNull(target.parentFile)
    check(directory.isDirectory || directory.mkdirs()) { "无法创建内核目录" }
    val staged = File.createTempFile("frpc-", ".pending", directory)
    val backup = File.createTempFile("frpc-", ".backup", directory)
    val oldVersion = versionFile.takeIf { it.isFile }?.readText()
    var backedUp = false
    var installed = false
    var stopped = false
    try {
        FileOutputStream(staged).use { output ->
            kernel.inputStream().use { it.copyTo(output) }
            output.fd.sync()
        }
        check(staged.setExecutable(true, false)) { "无法设置内核执行权限" }
        verify(staged)
        if (wasRunning) {
            val result = stop()
            check(result.ok) { result.message }
            stopped = true
        }
        if (target.exists()) {
            check(target.renameTo(backup)) { "无法备份旧内核" }
            backedUp = true
        }
        check(staged.renameTo(target)) { "无法替换内核" }
        installed = true
        check(TunnelStore.writeText(versionFile, version)) { "无法保存内核版本" }
        if (wasRunning) {
            val result = start()
            check(result.ok) { result.message }
        }
        backup.delete()
        return TunnelActionResult(true, "内核已更新到 $version")
    } catch (error: Exception) {
        val rollback = runCatching {
            if (installed && wasRunning) {
                val result = stop()
                check(result.ok) { result.message }
            }
            if (backedUp) {
                check(backup.renameTo(target)) { "旧内核保存在 ${backup.name}，恢复失败" }
            } else if (installed) {
                check(target.delete()) { "无法移除新内核" }
            }
            if (installed) {
                if (oldVersion == null) {
                    check(!versionFile.exists() || versionFile.delete()) { "无法恢复版本信息" }
                } else check(TunnelStore.writeText(versionFile, oldVersion)) { "无法恢复版本信息" }
            }
            if (stopped) {
                val result = start()
                check(result.ok) { "旧内核恢复后启动失败：${result.message}" }
            }
        }
        if (rollback.isSuccess) backup.delete()
        val detail = rollback.exceptionOrNull()?.message?.let { "；回滚失败：$it" }
            ?: if (installed || backedUp || stopped) "；已恢复更新前状态" else ""
        return TunnelActionResult(false, "更新失败：${error.message}$detail")
    } finally {
        staged.delete()
        if (!backedUp) backup.delete()
    }
}

/** Compatibility entry for small in-memory callers. */
internal fun installFrpcKernel(kernel: ByteArray, version: String, target: File, versionFile: File,
    wasRunning: Boolean, verify: (File) -> Unit, stop: () -> TunnelActionResult, start: () -> TunnelActionResult): TunnelActionResult {
    require(kernel.isNotEmpty())
    val directory = requireNotNull(target.parentFile)
    check(directory.isDirectory || directory.mkdirs())
    val source = File.createTempFile("frpc-source-", ".pending", directory)
    return try {
        source.writeBytes(kernel)
        installFrpcKernel(source, version, target, versionFile, wasRunning, verify, stop, start)
    } finally { source.delete() }
}
