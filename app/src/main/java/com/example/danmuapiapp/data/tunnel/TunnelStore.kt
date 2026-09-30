package com.example.danmuapiapp.data.tunnel

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 内网穿透（frpc）的落盘布局与状态读写。
 *
 * 目录固定在应用私有设备保护 filesDir/frp/（旧 CE 配置自动迁移），两种运行模式共用：
 *   frpc.conf / settings.json / status.json / frpc.log / autostart / PID 文件
 */
object TunnelStore {

    /** 穿透状态变化广播：:node 进程（普通模式）与 App 进程（Root 模式）都会发，
     *  UI 侧注册接收后被动刷新，不需要轮询。 */
    const val ACTION_TUNNEL_STATUS = "com.example.danmuapiapp.action.TUNNEL_STATUS"

    private const val LOG_MAX_BYTES = 1024L * 1024L
    private const val LOG_KEEP_BYTES = 256 * 1024

    fun dir(context: Context): File {
        val directory = File(context.createDeviceProtectedStorageContext().filesDir, "frp")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建穿透目录" }
        val unlocked = context.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked == true
        if (unlocked) migrateStorage(File(context.applicationContext.filesDir, "frp"), directory)
        return directory
    }

    /** Copy once under a cross-process lock; retain legacy paths for live-process adoption. */
    @Synchronized internal fun migrateStorage(legacy: File, directory: File) {
        if (legacy.absolutePath == directory.absolutePath || !legacy.isDirectory || File(directory, ".storage-v2").isFile) return
        FileOutputStream(File(directory, ".migration.lock"), true).channel.use { channel ->
            channel.lock().use {
                if (File(directory, ".storage-v2").isFile) return
                val names = setOf("frpc.conf", "frpc.toml", "settings.json", "status.json", "autostart",
                    "frpc.pid", "frpc-root.pid", "frpc-root.pid.start", "frpc-root.pid.version", "frpc.log", "kernel/libfrpc.so", "kernel/version.txt")
                names.map { File(legacy, it) }.filter { it.isFile }.forEach { source ->
                    val target = File(directory, source.relativeTo(legacy).path)
                    if (!target.exists()) {
                        target.parentFile?.mkdirs()
                        val pending = File.createTempFile("migrate-", ".pending", target.parentFile)
                        try {
                            FileOutputStream(pending).use { output ->
                                if (source.name == "frpc.log") output.write(readText(source, LOG_KEEP_BYTES).toByteArray(Charsets.UTF_8))
                                else source.inputStream().use { it.copyTo(output) }
                                output.fd.sync()
                            }
                            if (source.canExecute()) pending.setExecutable(true, true)
                            check(pending.renameTo(target)) { "穿透配置迁移失败" }
                        } finally { pending.delete() }
                    }
                }
                check(writeText(File(directory, ".storage-v2"), "2")) { "穿透迁移标记写入失败" }
            }
        }
    }

    // .conf 不强制格式，frpc 会按内容识别 TOML / INI / JSON / YAML。
    fun configFile(context: Context): File = migrateConfigFile(dir(context))

    internal fun migrateConfigFile(dir: File): File {
        val target = File(dir, "frpc.conf")
        val legacy = File(dir, "frpc.toml")
        if (!target.exists() && legacy.isFile) {
            check(writeText(target, legacy.readText())) { "穿透配置迁移失败" }
        }
        return target
    }
    fun settingsFile(context: Context) = File(dir(context), "settings.json")
    fun statusFile(context: Context) = File(dir(context), "status.json")
    fun logFile(context: Context) = File(dir(context), "frpc.log")
    fun autostartFlag(context: Context) = File(dir(context), "autostart")
    fun rootPidFile(context: Context) = File(dir(context), "frpc-root.pid")
    fun normalPidFile(context: Context) = File(dir(context), "frpc.pid")

    /** 随 App 包内置的内核（nativeLibraryDir，普通模式唯一可执行的位置）。 */
    fun kernelFile(context: Context): File {
        val libDir = context.applicationInfo.nativeLibraryDir
        return if (libDir.isNullOrBlank()) File("") else File(libDir, "libfrpc.so")
    }

    /** 在线更新下载的 Root 模式内核（App 私有目录，只有 root 能 exec）。 */
    fun customKernelFile(context: Context): File = File(dir(context), "kernel/libfrpc.so")

    fun customKernelVersionFile(context: Context): File = File(dir(context), "kernel/version.txt")

    fun kernelReady(context: Context, rootMode: Boolean = false): Boolean {
        if (rootMode) {
            val custom = customKernelFile(context)
            if (custom.isFile && custom.canExecute() && custom.length() > 0) return true
        }
        val kernel = kernelFile(context)
        return kernel.isFile && kernel.canExecute() && kernel.length() > 0
    }

    /** Root 模式优先用在线更新的内核，退回包内内核。 */
    fun execKernelFile(context: Context): File {
        val custom = customKernelFile(context)
        if (custom.isFile && custom.canExecute() && custom.length() > 0) return custom
        return kernelFile(context)
    }

    /** 当前生效的内核版本：在线更新过就以更新版本为准。 */
    fun kernelVersion(context: Context, rootMode: Boolean = false): String {
        val custom = customKernelFile(context)
        if (rootMode && custom.isFile && custom.canExecute() && custom.length() > 0) {
            val version = readText(customKernelVersionFile(context), 64).trim()
            if (version.isNotEmpty()) return version
        }
        return FRPC_KERNEL_VERSION
    }

    /** 读文本；超过 maxBytes 只保留尾部（日志/状态都是"后写的更重要"）。 */
    fun readText(file: File, maxBytes: Int = 256 * 1024): String {
        if (!file.isFile) return ""
        return try {
            val length = file.length()
            val skip = if (length > maxBytes) length - maxBytes else 0L
            file.inputStream().use { input ->
                var skipped = 0L
                while (skipped < skip) {
                    val step = input.skip(skip - skipped)
                    if (step <= 0) break
                    skipped += step
                }
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() < maxBytes) {
                    val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** 先写临时文件再改名：进程被杀也不会留下半个 JSON。 */
    fun writeText(file: File, text: String): Boolean {
        file.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        var tmp: File? = null
        return try {
            tmp = File.createTempFile(file.name + ".", ".tmp", file.parentFile)
            FileOutputStream(tmp).use { output ->
                output.write((text).toByteArray(Charsets.UTF_8))
                output.flush()
            }
            tmp.renameTo(file)
        } catch (_: Exception) {
            false
        } finally {
            tmp?.delete()
        }
    }

    // ------------------------------------------------------------ settings

    fun readSettings(context: Context): TunnelSettings =
        TunnelSettings.decode(readText(settingsFile(context), 64 * 1024))

    /**
     * 写入设置并同步开机自启标记：只有"总开关 + 随服务启动"同时打开才留标记，
     * Root 开机脚本只认这个标记，避免开关变化后还要重装模块。
     */
    fun writeSettings(context: Context, settings: TunnelSettings): Boolean {
        val json = settings.toJson().apply {
            put("updatedAt", System.currentTimeMillis())
        }
        val ok = writeText(settingsFile(context), json.toString())
        val flag = autostartFlag(context)
        if (settings.enabled && settings.autoStart) {
            return ok && writeText(flag, "1")
        } else if (flag.exists()) {
            return ok && flag.delete()
        }
        return ok
    }

    // ------------------------------------------------------------ status

    fun readStatus(context: Context): JSONObject = try {
        val text = readText(statusFile(context), 64 * 1024)
        if (text.isBlank()) JSONObject() else JSONObject(text)
    } catch (_: Exception) {
        JSONObject()
    }

    fun writeStatus(
        context: Context,
        state: String,
        mode: String,
        pid: Long,
        since: Long,
        restarts: Int,
        lastError: String
    ) {
        val json = JSONObject()
            .put("state", state)
            .put("mode", mode)
            .put("pid", pid)
            .put("since", since)
            .put("restarts", restarts)
            .put("lastError", lastError)
            .put("heartbeat", System.currentTimeMillis())
            .put("kernel", FRPC_KERNEL_VERSION)
        writeText(statusFile(context), json.toString())
    }

    /** 状态变化后通知 App 进程（仅同 UID 可收）。 */
    fun broadcastStatus(context: Context) {
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_TUNNEL_STATUS).setPackage(context.packageName)
            )
        }
    }

    // ------------------------------------------------------------ log

    fun rotateLogIfNeeded(context: Context) {
        val log = logFile(context)
        if (!log.isFile || log.length() <= LOG_MAX_BYTES) return
        replaceLogContents(log, readText(log, LOG_KEEP_BYTES))
    }

    fun readLogTail(context: Context, maxLines: Int = 200): String {
        val text = readText(logFile(context), LOG_KEEP_BYTES)
        if (text.isBlank()) return ""
        val lines = text.split('\n')
        if (lines.size <= maxLines) return text
        return lines.takeLast(maxLines).joinToString("\n")
    }

    fun clearLog(context: Context): Boolean = replaceLogContents(logFile(context), "")

    /** 保留 inode，运行中的 O_APPEND 写入端无需重新打开日志。 */
    internal fun replaceLogContents(file: File, text: String): Boolean = runCatching {
        FileOutputStream(file, false).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }.isSuccess
}
