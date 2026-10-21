package com.example.danmuapiapp.data.service

import android.content.Context
import android.os.Looper
import com.example.danmuapiapp.data.repository.RuntimeOwnership
import com.example.danmuapiapp.data.repository.determineRuntimeOwnershipFromHealth
import com.example.danmuapiapp.data.repository.isRootPassiveLivenessLikely
import com.example.danmuapiapp.data.repository.isRuntimeOwnershipAcceptedForRoot
import com.example.danmuapiapp.data.util.PortProbe
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import java.net.HttpURLConnection
import java.net.URL

/**
 * Root 运行时探活与健康检查。
 *
 * 包含端口/健康端点探测、进程发现、被动存活判断与控制标记读写。
 */
internal object RootRuntimeHealth {
    internal fun isRunningFast(port: Int): Boolean {
        return isPortOpen("127.0.0.1", port, 220)
    }

    internal fun isRuntimeOwnedByAppPassive(context: Context, port: Int): Boolean {
        return isRuntimeOwnershipAcceptedForRoot(RootRuntimeHealth.readRuntimeOwnership(context, port))
    }

    internal fun readRuntimeOwnership(context: Context, port: Int): RuntimeOwnership {
        if (port !in 1..65535) return RuntimeOwnership.Foreign
        val expectedIdentity = RuntimeIdentityStore.ensureInstanceId(context).trim()
        val expectedHome = RuntimePaths.rootProjectDir(context).absolutePath
        val body = RootRuntimeHealth.readRuntimeHealthBody(port) ?: return RuntimeOwnership.Unknown
        return determineRuntimeOwnershipFromHealth(
            body = body,
            expectedIdentity = expectedIdentity,
            expectedHome = expectedHome
        )
    }

    private fun readRuntimeHealthBody(port: Int): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("http://127.0.0.1:$port/__health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 450
                readTimeout = 700
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(65537)
                var count = 0
                while (count < chars.size) {
                    val read = reader.read(chars, count, chars.size - count)
                    if (read < 0) break
                    count += read
                }
                if (count > 65536) null else String(chars, 0, count)
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun discoverProcesses(context: Context): List<RootRuntimeRecovery.Process>? {
        val result = RootShell.exec(
            RootRuntimeRecovery.buildScanShell(RootRuntimeLocation.rootProjectDir(context), RootRuntimeLocation.mainClassName), timeoutMs = 4500L
        )
        return if (result.ok) RootRuntimeRecovery.parseProcesses(result.stdout) else null
    }

    /** Requires an explicit Root operation; never used by periodic passive checks. */
    internal fun recoverRuntime(context: Context, preferredPort: Int): RootRuntimeController.RecoveredRuntime? {
        if (Looper.getMainLooper().thread === Thread.currentThread()) return null
        val processes = RootRuntimeHealth.discoverProcesses(context)?.takeIf { it.isNotEmpty() } ?: return null
        val envResult = RootShell.exec(
            "head -c 262144 ${shellQuote("${RootRuntimeLocation.rootProjectDir(context)}/config/.env")}", timeoutMs = 2500L
        )
        val env = if (envResult.ok) com.example.danmuapiapp.data.util.DotEnvCodec.parse(envResult.stdout) else emptyMap()
        val port = env["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: preferredPort
        val health = RootRuntimeHealth.readRuntimeHealthBody(port)
        val healthPid = health?.let { Regex("\"pid\"\\s*:\\s*(\\d+)").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        val process = processes.firstOrNull { it.pid == healthPid }
            ?: processes.maxByOrNull { it.startTicks } ?: return null
        val clockTicks = runCatching { android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK) }.getOrNull()
        val startedAt = clockTicks?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime() + process.startTicks * 1000L / it
        }?.takeIf { it > 0 }
        rememberRecoveredMarkers(context, process.pid, startedAt)
        return RootRuntimeController.RecoveredRuntime(process.pid, port, startedAt, env, health != null)
    }

    /** A same-home health reply can recover the mode without opening a Root shell. */
    internal fun recoverRuntimePassive(context: Context, port: Int): RootRuntimeController.RecoveredRuntime? {
        val body = RootRuntimeHealth.readRuntimeHealthBody(port) ?: return null
        val ownership = determineRuntimeOwnershipFromHealth(body, RuntimeIdentityStore.ensureInstanceId(context), RootRuntimeLocation.rootProjectDir(context))
        if (!isRuntimeOwnershipAcceptedForRoot(ownership)) return null
        // Exact identity alone must not adopt a normal-mode runtime as Root.
        val rootHome = RootRuntimeLocation.rootProjectDir(context).trimEnd('/')
        val sameHome = listOf("resolvedHome", "envHome", "cwd").any { key ->
            com.example.danmuapiapp.data.repository.extractHealthString(body, key)?.trimEnd('/') == rootHome
        }
        if (!sameHome) return null
        val pid = Regex("\"pid\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 1 } ?: return null
        val uptime = Regex("\"uptimeSec\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val startedAt = uptime?.takeIf { it >= 0 && it < System.currentTimeMillis() / 1000L }?.let { System.currentTimeMillis() - it * 1000L }
        rememberRecoveredMarkers(context, pid, startedAt)
        return RootRuntimeController.RecoveredRuntime(pid, port, startedAt, emptyMap(), apiReady = true)
    }

    private fun rememberRecoveredMarkers(context: Context, pid: Int, startedAt: Long?) {
        runCatching { RootRuntimeLocation.pidFile(context).writeText("$pid\n", Charsets.UTF_8) }
        if (startedAt != null) runCatching { RootRuntimeLocation.startedAtFile(context).writeText("$startedAt\n", Charsets.UTF_8) }
    }

    internal fun isRunning(context: Context, port: Int): Boolean {
        if (RootRuntimeHealth.recoverRuntimePassive(context, port) != null) return true

        if (Looper.getMainLooper().thread === Thread.currentThread()) return false
        val recovered = RootRuntimeHealth.recoverRuntime(context, port)
        if (recovered != null) return true
        // Query failure remains unknown. A successful empty scan is authoritative.
        return if (RootRuntimeHealth.discoverProcesses(context) == null) RootRuntimeHealth.isProbablyRunning(context, port) && RootRuntimeHealth.readPid(context) != null else false
    }

    /**
     * Passive liveness hint used by UI/state reconciliation paths.
     *
     * This deliberately avoids RootShell/su so foreground resume, QS tile listening,
     * and periodic status reconciliation do not trigger Root manager authorization
     * notifications. Active start/stop paths still use [isRunning] when they need an
     * exact answer.
     */
    internal fun isProbablyRunning(context: Context, port: Int): Boolean {
        val portOpen = RootRuntimeHealth.isRunningFast(port)
        if (portOpen) return true
        return isRootPassiveLivenessLikely(
            portOpen = false,
            pidPresent = RootRuntimeHealth.readPid(context) != null,
            startedAtMs = RootRuntimeHealth.getProcessStartedAtMs(context),
            nowMs = System.currentTimeMillis()
        )
    }

    /** Cheap startup hint that never opens a socket or a Root shell. */
    internal fun hasPersistedRuntimeHint(context: Context): Boolean {
        return RootRuntimeHealth.readPid(context) != null && (
            RootRuntimeHealth.getProcessStartedAtMs(context) != null || RootRuntimeLocation.pidFile(context).lastModified() > 0L
        )
    }

    internal fun getPid(context: Context): Int? = RootRuntimeHealth.readPid(context)

    internal fun getProcessStartedAtMs(context: Context): Long? {
        val f = RootRuntimeLocation.startedAtFile(context)
        if (!f.exists()) return null
        val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()?.trim() ?: return null
        return raw.toLongOrNull()?.takeIf { it > 0L }
    }

    internal fun getPidFileLastModified(context: Context): Long? {
        val f = RootRuntimeLocation.pidFile(context)
        if (!f.exists()) return null
        val ts = f.lastModified()
        return ts.takeIf { it > 0L }
    }

    private fun readPid(context: Context): Int? {
        val f = RootRuntimeLocation.pidFile(context)
        if (!f.exists()) return null
        val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()?.trim() ?: return null
        return raw.toIntOrNull()
    }

    private fun isPidAlive(pid: Int): Boolean {
        val script = """
            PID=${shellQuote(pid.toString())}
            [ -d /proc/${'$'}PID ]
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 1200L).ok
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return PortProbe.isOpen(port = port, host = host, timeoutMs = timeoutMs)
    }


    internal data class StartupWaitResult(
        val ready: Boolean,
        val failureDetail: String? = null
    )

    internal fun waitForReadyOrFailure(context: Context, port: Int, timeoutMs: Long): StartupWaitResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (RootRuntimeHealth.isRunningFast(port)) {
                return StartupWaitResult(ready = true)
            }
            val startupFailure = StartupFailureStore.readRoot(context)
            if (startupFailure != null) {
                return StartupWaitResult(
                    ready = false,
                    failureDetail = startupFailure.userMessage()
                )
            }
            val pid = RootRuntimeHealth.readPid(context)
            if (pid != null && !isPidAlive(pid)) {
                return StartupWaitResult(ready = false)
            }
            runCatching { Thread.sleep(180) }
        }
        val startupFailure = StartupFailureStore.readRoot(context)
        return StartupWaitResult(
            ready = false,
            failureDetail = startupFailure?.userMessage()
        )
    }
}
