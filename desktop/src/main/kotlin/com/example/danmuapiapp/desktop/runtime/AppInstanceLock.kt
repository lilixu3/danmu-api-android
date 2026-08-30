package com.example.danmuapiapp.desktop.runtime

import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Commands that may be sent to the already-running desktop process. */
enum class InstanceCommand {
    SHOW_OVERVIEW,
    SHOW_SETTINGS,
}

/**
 * Process lock and loopback wake-up channel for the desktop application.
 *
 * The lock prevents a second UI/headless process from owning a tray icon or Node runtime. The
 * endpoint file only contains a random local-session token and a loopback port; it is never bound
 * to a LAN address. A duplicate launch sends a wake-up command to the owner instead of starting a
 * second Compose application.
 */
object AppInstanceLock {

    private const val ENDPOINT_FILE_NAME = "instance.endpoint"
    private const val CONNECT_TIMEOUT_MS = 350
    private const val SOCKET_TIMEOUT_MS = 3_000
    private const val RETRY_WINDOW_MS = 3_000L

    private var channel: java.io.RandomAccessFile? = null
    private var lock: java.nio.channels.FileLock? = null
    private var controlServer: ServerSocket? = null
    private var controlThread: Thread? = null
    private var controlToken: String? = null
    private var controlEndpointFile: File? = null
    private val controlRunning = AtomicBoolean(false)

    /** Try to hold the process lock; false means another instance owns it. */
    @Synchronized
    fun tryAcquire(lockFile: File): Boolean {
        lockFile.parentFile?.mkdirs()
        val raf = java.io.RandomAccessFile(lockFile, "rw")
        val fileLock = runCatching { raf.channel.tryLock() }.getOrNull()
        if (fileLock == null) {
            runCatching { raf.close() }
            return false
        }
        channel = raf
        lock = fileLock
        return true
    }

    /**
     * Start a loopback-only command server after acquiring the process lock.
     * Returns an error instead of silently disabling the single-instance wake-up path.
     */
    @Synchronized
    fun startControlServer(
        lockFile: File,
        onCommand: (InstanceCommand) -> Unit,
    ): String? {
        if (controlRunning.get()) return null
        val endpointFile = File(lockFile.parentFile ?: lockFile.absoluteFile.parentFile, ENDPOINT_FILE_NAME)
        val token = UUID.randomUUID().toString()
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            }
        } catch (error: Throwable) {
            return "无法创建本地唤醒通道：${error.message ?: error::class.java.simpleName}"
        }

        try {
            endpointFile.parentFile?.mkdirs()
            val content = buildString {
                append("port=").append(server.localPort).append('\n')
                append("token=").append(token).append('\n')
            }
            val temporary = File(endpointFile.parentFile, endpointFile.name + ".tmp")
            temporary.writeText(content, StandardCharsets.UTF_8)
            Files.move(
                temporary.toPath(),
                endpointFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (error: Throwable) {
            runCatching { server.close() }
            return "无法写入本地唤醒通道：${error.message ?: error::class.java.simpleName}"
        }

        controlServer = server
        controlToken = token
        controlEndpointFile = endpointFile
        controlRunning.set(true)
        controlThread = Thread({
            while (controlRunning.get()) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    if (controlRunning.get()) logControlError("本地唤醒通道接受连接失败")
                    break
                }
                handleConnection(socket, token, onCommand)
            }
        }, "danmu-desktop-instance-control").apply {
            isDaemon = true
            start()
        }
        return null
    }

    /** Send a wake-up command to the process that owns the lock. */
    fun sendCommand(command: InstanceCommand): Result<Unit> {
        val endpointFile = endpointFile()
        val deadline = System.currentTimeMillis() + RETRY_WINDOW_MS
        var lastError: Throwable = IOException("本地唤醒端点不存在：${endpointFile.absolutePath}")
        while (System.currentTimeMillis() < deadline) {
            try {
                val endpoint = readEndpoint(endpointFile)
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), endpoint.port), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = SOCKET_TIMEOUT_MS
                    val writer = PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)
                    writer.println(endpoint.token + '\t' + command.name)
                    writer.flush()
                    val response = socket.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine()
                    if (response == "OK") return Result.success(Unit)
                    throw IOException(response ?: "本地唤醒通道未返回结果")
                }
            } catch (error: Throwable) {
                lastError = error
                Thread.sleep(100)
            }
        }
        return Result.failure(IOException("无法唤醒已运行的弹幕API实例：${lastError.message}", lastError))
    }

    /** Stop the command server and release the process lock. */
    @Synchronized
    fun release() {
        controlRunning.set(false)
        runCatching { controlServer?.close() }
        controlThread?.interrupt()
        controlServer = null
        controlThread = null
        controlEndpointFile?.let { endpoint ->
            runCatching {
                val token = controlToken
                if (token == null || endpoint.readText(StandardCharsets.UTF_8).contains("token=$token")) {
                    endpoint.delete()
                }
            }
        }
        controlEndpointFile = null
        controlToken = null
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }

    private fun endpointFile(): File {
        val appdata = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming")
        return File(appdata, "DanmuApi/$ENDPOINT_FILE_NAME")
    }

    private data class Endpoint(val port: Int, val token: String)

    private fun readEndpoint(file: File): Endpoint {
        if (!file.isFile) throw IOException("本地唤醒端点不存在：${file.absolutePath}")
        val values = file.readLines(StandardCharsets.UTF_8)
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        val port = values["port"]?.toIntOrNull()?.takeIf { it in 1..65_535 }
            ?: throw IOException("本地唤醒端点端口无效")
        val token = values["token"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("本地唤醒端点令牌缺失")
        return Endpoint(port, token)
    }

    private fun handleConnection(
        socket: Socket,
        expectedToken: String,
        onCommand: (InstanceCommand) -> Unit,
    ) {
        socket.use { connection ->
            connection.soTimeout = SOCKET_TIMEOUT_MS
            try {
                val request = connection.getInputStream().bufferedReader(StandardCharsets.UTF_8).readLine()
                    ?: throw IOException("本地唤醒请求为空")
                val parts = request.split('\t', limit = 2)
                if (parts.size != 2 || parts[0] != expectedToken) {
                    throw IOException("本地唤醒令牌无效")
                }
                val command = InstanceCommand.entries.firstOrNull { it.name == parts[1] }
                    ?: throw IOException("未知本地唤醒命令：${parts[1]}")
                onCommand(command)
                val writer = PrintWriter(connection.getOutputStream(), true, StandardCharsets.UTF_8)
                writer.println("OK")
                writer.flush()
            } catch (error: Throwable) {
                runCatching {
                    val writer = PrintWriter(connection.getOutputStream(), true, StandardCharsets.UTF_8)
                    writer.println("ERROR ${error.message ?: error::class.java.simpleName}")
                    writer.flush()
                }.onFailure { writeError ->
                    logControlError("本地唤醒响应写入失败：${writeError.message ?: writeError::class.java.simpleName}")
                }
                logControlError(error.message ?: error.toString())
            }
        }
    }

    private fun logControlError(message: String) {
        runCatching {
            val appdata = System.getenv("LOCALAPPDATA")
                ?: (System.getProperty("user.home") + "\\AppData\\Local")
            val log = File(appdata, "DanmuApi/logs/tray.log")
            log.parentFile?.mkdirs()
            log.appendText("${java.time.LocalDateTime.now()}  本地唤醒通道：$message${System.lineSeparator()}")
        }
    }
}
