package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.RunMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class RuntimeDirectoryStatus(val exists: Boolean, val readable: Boolean, val writable: Boolean, val freeMb: Long)

@Singleton
class RuntimeStorageGateway @Inject constructor(@ApplicationContext private val context: Context) {
    fun isFile(target: RuntimeDataContext, relativePath: String): Boolean {
        val file = resolve(target, relativePath)
        if (target.mode == RunMode.Normal) return file.isFile
        val result = RootShell.exec("[ -e ${shellQuote(file.absolutePath)} ] || exit 44\n[ -f ${shellQuote(file.absolutePath)} ]", 6000L)
        if (result.exitCode == 44) return false
        check(result.ok) { "无法检查 Root 运行目录中的文件" }
        return true
    }
    fun resolve(target: RuntimeDataContext, relativePath: String): File {
        require(!File(relativePath).isAbsolute && relativePath.split('/', '\\').none { it == ".." })
        return File(target.projectDir, relativePath)
    }

    fun readText(target: RuntimeDataContext, relativePath: String, limit: Int = 128 * 1024): String? {
        require(limit in 1..128 * 1024)
        val file = resolve(target, relativePath)
        if (target.mode == RunMode.Normal) {
            if (!file.exists()) return null
            check(file.isFile && file.canRead()) { "无法读取当前运行目录中的文件" }
            file.inputStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= limit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                check(bytes.size <= limit) { "运行时元数据超过大小限制" }
                return bytes.toString(Charsets.UTF_8)
            }
        }
        val script = "FILE=${shellQuote(file.absolutePath)}\n" +
            "[ -e \"\$FILE\" ] || exit 44\n[ -f \"\$FILE\" ] && [ -r \"\$FILE\" ] || exit 13\n" +
            "[ \"\$(wc -c < \"\$FILE\")\" -le $limit ] || exit 45\ncat \"\$FILE\""
        val result = RootShell.exec(script, timeoutMs = 6000L)
        if (result.exitCode == 44) return null
        if (!result.ok) throw IOException("Root 运行目录读取失败（${result.exitCode}），未使用普通目录替代")
        return result.stdout
    }

    fun writeText(target: RuntimeDataContext, relativePath: String, content: String?) {
        val file = resolve(target, relativePath)
        if (content == null) {
            if (target.mode == RunMode.Root) {
                check(RootShell.exec("rm -f -- ${shellQuote(file.absolutePath)}", 8000L).ok) { "无法删除 Root 运行目录中的文件" }
            } else if (file.exists() && !file.delete()) throw IOException("无法删除运行目录中的文件")
            return
        }
        if (target.mode == RunMode.Normal) {
            val parent = file.parentFile ?: throw IOException("运行目录无效")
            check(parent.isDirectory || parent.mkdirs()) { "无法创建运行目录" }
            val temporary = File.createTempFile(".runtime-write-", ".tmp", parent)
            try {
                temporary.writeText(content, Charsets.UTF_8)
                check(temporary.renameTo(file)) { "无法原子替换运行目录中的文件" }
            } finally { temporary.delete() }
            return
        }
        val source = File.createTempFile("runtime-write-", ".tmp", context.cacheDir)
        val temporaryPath = "${file.absolutePath}.${java.util.UUID.randomUUID()}.tmp"
        try {
            source.writeText(content, Charsets.UTF_8)
            val script = "SRC=${shellQuote(source.absolutePath)}\nDST=${shellQuote(file.absolutePath)}\nTMP=${shellQuote(temporaryPath)}\n" +
                "trap 'rm -f -- \"\$TMP\"' EXIT\n" +
                "mkdir -p \"\$(dirname \"\$DST\")\" && cat \"\$SRC\" > \"\$TMP\" && chmod 0600 \"\$TMP\" && mv -f \"\$TMP\" \"\$DST\""
            check(RootShell.exec(script, 8000L).ok) { "Root 运行目录写入失败，未改写普通目录" }
        } finally { source.delete() }
    }

    fun directoryStatus(target: RuntimeDataContext): RuntimeDirectoryStatus {
        val directory = target.projectDir
        if (target.mode == RunMode.Normal) return RuntimeDirectoryStatus(directory.isDirectory,
            directory.canRead(), directory.canWrite(), (directory.parentFile?.usableSpace ?: 0L) / (1024 * 1024))
        val script = "DIR=${shellQuote(directory.absolutePath)}\n" +
            "[ -d \"\$DIR\" ] || exit 44\n" +
            "[ -r \"\$DIR\" ] && echo readable\n[ -w \"\$DIR\" ] && echo writable\n" +
            "df -Pk \"\$DIR\" | tail -n 1 | awk '{print \$4}'"
        val result = RootShell.exec(script, timeoutMs = 6000L)
        if (result.exitCode == 44) return RuntimeDirectoryStatus(false, false, false, 0)
        if (!result.ok) throw IOException("无法检查 Root 工作目录，未使用普通目录替代")
        val lines = result.stdout.lineSequence().map { it.trim() }.toList()
        return RuntimeDirectoryStatus(true, "readable" in lines, "writable" in lines,
            (lines.lastOrNull()?.toLongOrNull() ?: throw IOException("无法读取 Root 剩余空间")) / 1024)
    }
}
