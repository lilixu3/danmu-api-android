package com.example.danmuapiapp.data.util

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.delay

/**
 * 统一的 TCP 存活探测。
 *
 * 之前 NodeService、看护/心跳服务和运行时仓库各自复制了一份同样的实现，
 * 这里收敛成一处，超时与重试语义保持一致：
 * - [isOpen] 单次探测，供后台线程（服务、心跳）使用；
 * - [probe] 带重试与错误详情，供协程（运行时仓库）使用。
 */
internal object PortProbe {

    const val LOOPBACK = "127.0.0.1"
    const val DEFAULT_TIMEOUT_MS = 220

    data class Result(
        val open: Boolean,
        val attempts: Int,
        val elapsedMs: Long,
        val error: String?
    )

    fun isOpen(
        port: Int,
        host: String = LOOPBACK,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS
    ): Boolean {
        if (port !in 1..65535) return false
        return attempt(host, port, timeoutMs).first
    }

    suspend fun probe(
        port: Int,
        host: String = LOOPBACK,
        attempts: Int = 3,
        timeoutMs: Int = 450,
        retryDelayMs: Long = 300L
    ): Result {
        if (port !in 1..65535) return Result(false, 0, 0L, "端口无效：$port")
        val startedAt = System.currentTimeMillis()
        var lastError: String? = null
        var used = 0
        repeat(attempts.coerceAtLeast(1)) { index ->
            used = index + 1
            val (open, error) = attempt(host, port, timeoutMs)
            if (open) {
                return Result(true, used, System.currentTimeMillis() - startedAt, null)
            }
            lastError = error
            if (index < attempts - 1) {
                delay(retryDelayMs)
            }
        }
        return Result(
            open = false,
            attempts = used,
            elapsedMs = System.currentTimeMillis() - startedAt,
            error = lastError
        )
    }

    private fun attempt(host: String, port: Int, timeoutMs: Int): Pair<Boolean, String?> {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true to null
        } catch (error: Exception) {
            false to "${error.javaClass.simpleName}: ${error.message ?: "无消息"}"
        } finally {
            runCatching { socket?.close() }
        }
    }
}
