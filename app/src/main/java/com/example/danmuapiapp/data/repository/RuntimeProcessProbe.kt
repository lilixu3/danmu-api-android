package com.example.danmuapiapp.data.repository

import android.app.ActivityManager
import android.content.Context
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RuntimeIdentityStore
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.PortProbe
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL

/**
 * 运行时端口、进程与归属探测。
 *
 * 只读取系统与运行时的真实状态，不修改状态流：端口探活与等待、Node 进程/前台服务判定、
 * 健康接口归属校验、退出等待。状态协调与生命周期控制都基于这里的结论做决策。
 */
internal class RuntimeProcessProbe(private val context: Context) {

    private companion object {
        const val PORT_PROBE_ATTEMPTS = 3
        const val PORT_PROBE_TIMEOUT_MS = 450
        const val PORT_PROBE_RETRY_DELAY_MS = 300L
        const val PORT_OPEN_TIMEOUT_MS = 450
        const val HEALTH_CONNECT_TIMEOUT_MS = 450
        const val HEALTH_READ_TIMEOUT_MS = 700
        const val WAIT_POLL_INTERVAL_MS = 180L
    }

    fun isPortOpen(port: Int): Boolean {
        return PortProbe.isOpen(port = port, timeoutMs = PORT_OPEN_TIMEOUT_MS)
    }

    suspend fun waitForPort(port: Int, wantOpen: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val open = isPortOpen(port)
            if (open == wantOpen) return true
            delay(160)
        }
        return false
    }

    /**
     * 带重试的端口探测：任何一次成功即视为存活，全部失败才返回 closed，
     * 并带上最后一次的错误类型，便于区分"连接被拒"和"被冻结导致的超时"。
     */
    suspend fun probePort(
        state: RuntimeState,
        attempts: Int = PORT_PROBE_ATTEMPTS,
        timeoutMs: Int = PORT_PROBE_TIMEOUT_MS,
        retryDelayMs: Long = PORT_PROBE_RETRY_DELAY_MS
    ): PortProbe.Result {
        if (state.runMode == RunMode.Root) {
            val open = RootRuntimeController.isRunning(context, state.port)
            return PortProbe.Result(
                open = open,
                attempts = 1,
                elapsedMs = 0L,
                error = if (open) null else "Root 运行时未响应"
            )
        }
        return PortProbe.probe(
            port = state.port,
            attempts = attempts,
            timeoutMs = timeoutMs,
            retryDelayMs = retryDelayMs
        )
    }

    fun isNormalRuntimeStopped(port: Int): Boolean {
        return !isNormalProcessRunning() && !isPortOpen(port)
    }

    fun isNormalRestartStopCompleted(oldPort: Int): Boolean {
        return isNormalRestartTeardownComplete(
            serviceRunning = isNormalServiceRunning(),
            portOpen = isPortOpen(oldPort)
        )
    }

    suspend fun waitForRestartStopCompletion(
        mode: RunMode,
        oldPort: Int,
        timeoutMs: Long
    ): Boolean {
        if (mode != RunMode.Normal) {
            return waitForRuntimeStopped(mode, oldPort, timeoutMs)
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isNormalRestartStopCompleted(oldPort)) {
                return true
            }
            delay(WAIT_POLL_INTERVAL_MS)
        }
        return isNormalRestartStopCompleted(oldPort)
    }

    suspend fun waitForRuntimeStopped(mode: RunMode, oldPort: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isRuntimeStopped(mode, oldPort)) return true
            delay(WAIT_POLL_INTERVAL_MS)
        }
        return isRuntimeStopped(mode, oldPort)
    }

    private fun isRuntimeStopped(mode: RunMode, oldPort: Int): Boolean {
        return when (mode) {
            RunMode.Normal -> isNormalRuntimeStopped(oldPort)
            RunMode.Root -> !RootRuntimeController.isRunning(context, oldPort)
        }
    }

    suspend fun waitForNormalProcessReclaimed(expectedPid: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (NodeService.recoveryCandidatePid(context) != expectedPid) return true
            delay(WAIT_POLL_INTERVAL_MS)
        }
        return NodeService.recoveryCandidatePid(context) != expectedPid
    }

    @Suppress("DEPRECATION")
    fun isNormalServiceRunning(): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            am.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == NodeService::class.java.name }
        }.getOrElse { false }
    }

    fun isNormalProcessRunning(): Boolean {
        return NodeService.isProcessRunning(context)
    }

    fun isNormalRuntimeReachable(port: Int): Boolean {
        return isPortOpen(port) && isRuntimeOwnedByApp(port, RunMode.Normal)
    }

    fun isRuntimePortActive(state: RuntimeState): Boolean {
        return when (state.runMode) {
            RunMode.Normal -> isPortOpen(state.port)
            RunMode.Root -> RootRuntimeController.isRunning(context, state.port)
        }
    }

    fun isRootRuntimeOwnedByApp(port: Int): Boolean {
        return RootRuntimeController.isRuntimeOwnedByAppPassive(context, port)
    }

    fun isRuntimeOwnedByApp(port: Int, mode: RunMode): Boolean {
        return isRuntimeOwnershipOwned(readRuntimeOwnership(port, mode))
    }

    fun readRuntimeOwnership(port: Int, mode: RunMode): RuntimeOwnership {
        if (port !in 1..65535) return RuntimeOwnership.Foreign
        val expectedIdentity = RuntimeIdentityStore.ensureInstanceId(context).trim()
        val expectedHome = RuntimePaths.projectDir(context, mode).absolutePath
        val body = readRuntimeHealthBody(port) ?: return RuntimeOwnership.Unknown
        return determineRuntimeOwnershipFromHealth(
            body = body,
            expectedIdentity = expectedIdentity,
            expectedHome = expectedHome
        )
    }

    fun readRuntimeHealthBody(port: Int): String? {
        if (port !in 1..65535) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RuntimeApiUrls.local(port, path = "__health")).openConnection() as HttpURLConnection).apply {
                connectTimeout = HEALTH_CONNECT_TIMEOUT_MS
                readTimeout = HEALTH_READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun readHealthUptimeSeconds(port: Int): Long? {
        val body = readRuntimeHealthBody(port) ?: return null
        return parseHealthUptimeSeconds(body)
    }
}
