package com.example.danmuapiapp.ui.compat

import android.os.Build
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.data.service.TvConfigSyncPayload
import com.example.danmuapiapp.data.service.TvConfigSyncResponse
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CompatTvConfigSyncServer(
    private val envConfigRepository: EnvConfigRepository,
    private val runtimeRepository: RuntimeRepository,
    private val settingsRepository: SettingsRepository,
    private val coreRepository: CoreRepository
) {

    data class UiState(
        val isReady: Boolean = false,
        val host: String = "",
        val port: Int = 0,
        val inviteUrl: String = "",
        val statusText: String = "正在准备手机同步",
        val lastSyncSummary: String = ""
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val token = UUID.randomUUID().toString().replace("-", "").take(12)
    private val deviceName = buildDeviceName()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var pendingRestartJob: Job? = null

    fun start(initialHost: String) {
        updateHost(initialHost)
        if (acceptJob != null) return

        acceptJob = scope.launch {
            try {
                ServerSocket(0).use { socket ->
                    serverSocket = socket
                    updateState {
                        val invite = buildInviteUrl(it.host, socket.localPort)
                        it.copy(
                            isReady = true,
                            port = socket.localPort,
                            inviteUrl = invite,
                            statusText = if (it.host.isBlank()) {
                                "请让电视和手机接入同一局域网"
                            } else {
                                "打开手机端“备份与恢复”，点击扫码同步"
                            }
                        )
                    }

                    while (scope.isActive) {
                        val client = socket.accept()
                        scope.launch { handleClient(client) }
                    }
                }
            } catch (_: SocketException) {
                // Activity 销毁时会主动关闭 socket，这里无需额外处理。
            } catch (t: Throwable) {
                updateState {
                    it.copy(
                        isReady = false,
                        statusText = "同步服务启动失败：${t.message ?: "未知错误"}"
                    )
                }
            } finally {
                serverSocket = null
                acceptJob = null
            }
        }
    }

    fun updateHost(host: String) {
        val normalized = host.trim()
        scope.launch {
            updateState {
                val invite = buildInviteUrl(normalized, it.port)
                it.copy(
                    host = normalized,
                    inviteUrl = invite,
                    statusText = if (it.lastSyncSummary.isNotBlank()) {
                        it.statusText
                    } else if (normalized.isBlank()) {
                        "请让电视和手机接入同一局域网"
                    } else {
                        "打开手机端“备份与恢复”，点击扫码同步"
                    }
                )
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        pendingRestartJob?.cancel()
        pendingRestartJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        scope.cancel()
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 7_000
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val requestLine = readLine(input)?.trim().orEmpty()
                if (requestLine.isBlank()) {
                    writeResponse(output, 400, TvConfigSyncResponse(false, "请求为空"))
                    return
                }

                val parts = requestLine.split(' ')
                if (parts.size < 2) {
                    writeResponse(output, 400, TvConfigSyncResponse(false, "请求格式错误"))
                    return
                }

                val method = parts[0].uppercase(Locale.ROOT)
                val rawPath = parts[1]
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isBlank()) break
                    val index = line.indexOf(':')
                    if (index <= 0) continue
                    val name = line.substring(0, index).trim().lowercase(Locale.ROOT)
                    val value = line.substring(index + 1).trim()
                    headers[name] = value
                }

                val uri = URI("http://localhost$rawPath")
                if (method == "GET" && uri.path == "/sync/apply") {
                    writeResponse(output, 200, TvConfigSyncResponse(true, _uiState.value.statusText))
                    return
                }

                if (method != "POST" || uri.path != "/sync/apply") {
                    writeResponse(output, 404, TvConfigSyncResponse(false, "未找到同步接口"))
                    return
                }

                val queryToken = parseQuery(uri.rawQuery)["token"].orEmpty().trim()
                if (queryToken != token) {
                    writeResponse(output, 403, TvConfigSyncResponse(false, "同步码已失效或不匹配"))
                    return
                }

                val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val bodyBytes = readExact(input, contentLength)
                if (bodyBytes == null) {
                    writeResponse(output, 400, TvConfigSyncResponse(false, "同步数据不完整"))
                    return
                }

                val payload = runCatching {
                    TvConfigSyncCodec.decodePayload(bodyBytes.toString(Charsets.UTF_8))
                }.getOrElse {
                    writeResponse(output, 400, TvConfigSyncResponse(false, "同步数据无法解析"))
                    return
                }

                val message = applyPayload(payload)
                writeResponse(output, 200, TvConfigSyncResponse(true, message))
            }.onFailure { error ->
                runCatching {
                    val output = BufferedOutputStream(socket.getOutputStream())
                    writeResponse(output, 500, TvConfigSyncResponse(false, error.message ?: "同步失败"))
                }
            }
        }
    }

    private suspend fun applyPayload(payload: TvConfigSyncPayload): String {
        require(payload.envContent.isNotBlank()) { "同步内容为空" }

        envConfigRepository.saveRawContent(payload.envContent)

        val runtimeSnapshot = runtimeRepository.runtimeState.value
        val requestedVariant = ApiVariant.entries.firstOrNull {
            it.key.equals(payload.runtime.variantKey, ignoreCase = true)
        }
        val port = payload.runtime.port.takeIf { it in 1..65535 } ?: runtimeSnapshot.port
        val token = payload.runtime.token

        settingsRepository.setGithubProxy(payload.settings.githubProxy)
        settingsRepository.setGithubToken(payload.settings.githubToken)
        if (payload.version >= 2) {
            settingsRepository.setVariantDisplayName(ApiVariant.Stable, payload.settings.stableRepoDisplayName)
            settingsRepository.setVariantDisplayName(ApiVariant.Dev, payload.settings.devRepoDisplayName)
            settingsRepository.saveCustomCoreConfig(
                displayName = payload.settings.customRepoDisplayName,
                repoInput = payload.settings.customRepo,
                branchInput = payload.settings.customRepoBranch
            )
        } else {
            settingsRepository.setCustomRepo(payload.settings.customRepo)
            settingsRepository.setCustomRepoDisplayName(payload.settings.customRepoDisplayName)
        }

        val resolvedVariant = resolveVariantAfterSync(requestedVariant, runtimeSnapshot.variant)
        val portChanged = port != runtimeSnapshot.port
        val tokenChanged = token.trim() != runtimeSnapshot.token.trim()
        val variantChanged = resolvedVariant != runtimeSnapshot.variant
        if (variantChanged) {
            runtimeRepository.updateVariant(resolvedVariant)
        }

        coreRepository.refreshCoreInfo()

        val currentStatus = runtimeRepository.runtimeState.value.status
        val shouldRestart = currentStatus == ServiceStatus.Running || currentStatus == ServiceStatus.Starting
        var restartNote = ""
        if (portChanged || tokenChanged) {
            cancelPendingRestart()
            runtimeRepository.applyServiceConfig(
                port = port,
                token = token,
                restartIfRunning = shouldRestart
            )
            if (shouldRestart) {
                restartNote = "服务正在重启"
            }
        } else if (variantChanged) {
            when (currentStatus) {
                ServiceStatus.Running -> {
                    cancelPendingRestart()
                    runtimeRepository.restartService()
                    restartNote = "服务正在重启"
                }

                ServiceStatus.Starting -> {
                    scheduleRestartAfterStartup()
                    restartNote = "服务将在启动完成后自动重启"
                }

                else -> Unit
            }
        }

        val sourceName = payload.sourceDeviceName.trim().ifBlank { "手机端" }
        val variantNote = buildVariantSyncNote(requestedVariant, resolvedVariant, runtimeSnapshot.variant)
        val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val message = buildString {
            append("已接收 ")
            append(sourceName)
            append(" 配置")
            if (variantNote.isNotBlank()) {
                append("，")
                append(variantNote)
            }
            if (restartNote.isNotBlank()) {
                append("，")
                append(restartNote)
            }
        }
        runtimeRepository.addLog(LogLevel.Info, message)
        updateState {
            it.copy(
                statusText = if (variantNote.isBlank()) "上次同步成功" else "上次同步成功：$variantNote",
                lastSyncSummary = "$timeText 来自 $sourceName"
            )
        }
        return message
    }

    private fun cancelPendingRestart() {
        pendingRestartJob?.cancel()
        pendingRestartJob = null
    }

    private fun scheduleRestartAfterStartup() {
        cancelPendingRestart()
        val job = scope.launch {
            repeat(40) {
                when (runtimeRepository.runtimeState.value.status) {
                    ServiceStatus.Starting,
                    ServiceStatus.Stopping -> delay(500L)

                    ServiceStatus.Running -> {
                        runtimeRepository.restartService()
                        return@launch
                    }

                    ServiceStatus.Stopped,
                    ServiceStatus.Error -> return@launch
                }
            }
        }
        pendingRestartJob = job
        job.invokeOnCompletion {
            if (pendingRestartJob === job) {
                pendingRestartJob = null
            }
        }
    }

    private suspend fun updateState(transform: (UiState) -> UiState) {
        stateMutex.withLock {
            _uiState.value = transform(_uiState.value)
        }
    }

    private fun resolveVariantAfterSync(requestedVariant: ApiVariant?, currentVariant: ApiVariant): ApiVariant {
        if (requestedVariant == null) {
            return if (coreRepository.isCoreReady(currentVariant)) {
                currentVariant
            } else {
                ApiVariant.entries.firstOrNull { coreRepository.isCoreReady(it) } ?: currentVariant
            }
        }
        if (coreRepository.isCoreReady(requestedVariant)) return requestedVariant
        if (coreRepository.isCoreReady(currentVariant)) return currentVariant
        return ApiVariant.entries.firstOrNull { coreRepository.isCoreReady(it) } ?: currentVariant
    }

    private fun buildVariantSyncNote(
        requestedVariant: ApiVariant?,
        resolvedVariant: ApiVariant,
        previousVariant: ApiVariant
    ): String {
        val displayNames = settingsRepository.coreDisplayNames.value
        fun label(variant: ApiVariant): String = displayNames.resolve(variant)
        if (requestedVariant == null) return ""
        if (requestedVariant == resolvedVariant) {
            return if (resolvedVariant == previousVariant) "" else "已切换到 ${label(resolvedVariant)}"
        }
        return when {
            resolvedVariant == previousVariant -> "${label(requestedVariant)} 不可用，已保留当前核心 ${label(resolvedVariant)}"
            else -> "${label(requestedVariant)} 不可用，已改用 ${label(resolvedVariant)}"
        }
    }


    private fun buildInviteUrl(host: String, port: Int): String {
        if (host.isBlank() || port !in 1..65535) return ""
        return TvConfigSyncCodec.buildInviteUrl(
            host = host,
            port = port,
            token = token,
            deviceName = deviceName
        )
    }

    private fun buildDeviceName(): String {
        val parts = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.orEmpty().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return parts.joinToString(" ").ifBlank { "DanmuApi TV" }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) {
                return if (buffer.size() > 0) buffer.toString(Charsets.UTF_8.name()) else null
            }
            if (value == '\n'.code) {
                break
            }
            if (value != '\r'.code) {
                buffer.write(value)
            }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun readExact(input: BufferedInputStream, length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(buffer, offset, length - offset)
            if (count <= 0) return null
            offset += count
        }
        return buffer
    }

    private fun writeResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        response: TvConfigSyncResponse
    ) {
        val body = TvConfigSyncCodec.encodeResponse(response).toByteArray(Charsets.UTF_8)
        val statusLine = when (statusCode) {
            200 -> "HTTP/1.1 200 OK"
            400 -> "HTTP/1.1 400 Bad Request"
            403 -> "HTTP/1.1 403 Forbidden"
            404 -> "HTTP/1.1 404 Not Found"
            else -> "HTTP/1.1 500 Internal Server Error"
        }
        val header = buildString {
            append(statusLine)
            append("\r\nContent-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ")
            append(body.size)
            append("\r\nConnection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        output.write(header)
        output.write(body)
        output.flush()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { segment ->
            if (segment.isBlank()) return@forEach
            val index = segment.indexOf('=')
            val key = if (index >= 0) segment.substring(0, index) else segment
            val value = if (index >= 0) segment.substring(index + 1) else ""
            val normalizedKey = URLDecoder.decode(key, Charsets.UTF_8.name())
            if (normalizedKey.isBlank()) return@forEach
            out[normalizedKey] = URLDecoder.decode(value, Charsets.UTF_8.name())
        }
        return out
    }
}
