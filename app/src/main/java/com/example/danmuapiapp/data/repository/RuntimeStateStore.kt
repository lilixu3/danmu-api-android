package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.data.service.RuntimeNetworkAddressResolver
import com.example.danmuapiapp.data.service.RuntimeNetworkAddresses
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.VideoShellInjectionConfigPublisher
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.TokenDefaults
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * 运行时状态存储：`runtime` 偏好读写、初始状态装载、运行时长锚点与访问地址拼装。
 *
 * Repository 只提供"进程是否存活"和"健康接口上报的运行时长"两个探测回调，
 * 其余读写都收敛在这里，避免偏好键与 URL 拼装散落在业务代码里。
 */
internal class RuntimeStateStore(
    private val context: Context,
    private val isNormalProcessRunning: () -> Boolean,
    private val healthUptimeSeconds: (Int) -> Long?
) {

    private companion object {
        const val KEY_RUNTIME_STARTED_AT_MS = "runtime_started_at_ms"
        const val KEY_PORT = "port"
        const val KEY_TOKEN = "token"
        const val KEY_VARIANT = "variant"
        const val DEFAULT_PORT = 9321
        const val PREFS_RUNTIME = "runtime"
        const val PREFS_LEGACY_VARIANT = "danmu_api_variant"
    }

    private val prefs = context.getSharedPreferences(PREFS_RUNTIME, Context.MODE_PRIVATE)

    val state: MutableStateFlow<RuntimeState> = MutableStateFlow(loadInitialState())

    fun loadInitialState(): RuntimeState {
        val mode = RuntimeModePrefs.get(context)
        val defaultTokenEnvFile = normalRuntimeEnvFileForBootstrap(mode)
        val envValues = readRuntimeEnvValues(defaultTokenEnvFile)
        val portFromEnv = envValues["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 }
        val port = if (prefs.contains(KEY_PORT)) {
            prefs.getInt(KEY_PORT, DEFAULT_PORT)
        } else {
            portFromEnv ?: DEFAULT_PORT
        }
        val token = TokenDefaults.resolveTokenFromPrefs(prefs, context, defaultTokenEnvFile)
        val listenModeFromPrefs = RuntimeListenMode.fromKey(
            if (prefs.contains(RuntimeListenMode.PREFERENCE_KEY)) {
                prefs.getString(RuntimeListenMode.PREFERENCE_KEY, null)
            } else {
                null
            }
        )
        val listenModeFromEnv = RuntimeListenMode.fromBindHost(
            envValues[RuntimeListenMode.ENV_KEY]
        )
        val listenMode = listenModeFromPrefs
            ?: listenModeFromEnv
            ?: RuntimeListenMode.Ipv4Only
        if (listenModeFromPrefs == null && listenModeFromEnv != null) {
            prefs.edit {
                putString(RuntimeListenMode.PREFERENCE_KEY, listenModeFromEnv.key)
            }
        }
        val runtimeVariant = normalizeVariantKey(
            if (prefs.contains(KEY_VARIANT)) prefs.getString(KEY_VARIANT, null) else null
        )
        val legacyVariant = normalizeVariantKey(
            context.getSharedPreferences(PREFS_LEGACY_VARIANT, Context.MODE_PRIVATE)
                .getString(KEY_VARIANT, null)
        )
        val envVariant = normalizeVariantKey(envValues["DANMU_API_VARIANT"])
        val selectedVariantKey = runtimeVariant ?: legacyVariant ?: envVariant ?: ApiVariant.Stable.key
        val variant = ApiVariant.entries.find { it.key == selectedVariantKey } ?: ApiVariant.Stable

        if (runtimeVariant == null || legacyVariant == null || runtimeVariant != selectedVariantKey || legacyVariant != selectedVariantKey) {
            persistSelectedVariant(variant, commit = false)
        }

        // Constructor runs on the app main thread. Use local process markers here and
        // leave HTTP/Root ownership reconciliation to the existing IO coroutine.
        val normalRuntimeAlive = mode == RunMode.Normal && isNormalProcessRunning()
        val rootPid = if (mode == RunMode.Root) RootRuntimeController.getPid(context) else null
        val running = if (mode == RunMode.Root) {
            RootRuntimeController.hasPersistedRuntimeHint(context)
        } else {
            normalRuntimeAlive
        }
        var startedAt = readRuntimeStartedAt()

        if (running) {
            if (startedAt <= 0L) {
                startedAt = when (mode) {
                    RunMode.Root -> {
                        RootRuntimeController.getProcessStartedAtMs(context)
                            ?: RootRuntimeController.getPidFileLastModified(context)
                            ?: System.currentTimeMillis()
                    }

                    RunMode.Normal -> recoverNormalRuntimeStartedAtFromHealth(port)
                        ?: System.currentTimeMillis()
                }
                saveRuntimeStartedAt(startedAt)
            }
        }

        val uptimeSeconds = if (running && startedAt > 0L) {
            uptimeSecondsFrom(startedAt)
        } else {
            0L
        }

        val networkAddresses = resolveAddresses()
        return RuntimeState(
            status = if (running) ServiceStatus.Running else ServiceStatus.Stopped,
            port = port,
            token = token,
            variant = variant,
            runMode = mode,
            listenMode = listenMode,
            pid = if (running && mode == RunMode.Root) rootPid else null,
            uptimeSeconds = uptimeSeconds,
            localUrl = buildLocalUrl(port, token),
            lanUrl = buildLanUrl(networkAddresses.ipv4, port, token),
            lanIpv6Url = buildLanIpv6Url(networkAddresses.ipv6, port, token, listenMode)
        )
    }

    fun resolveAddresses(): RuntimeNetworkAddresses = RuntimeNetworkAddressResolver.resolve(context)

    /** 用当前端口/Token/监听模式重算三份访问地址。 */
    fun withResolvedUrls(state: RuntimeState, addresses: RuntimeNetworkAddresses): RuntimeState {
        return state.copy(
            localUrl = buildLocalUrl(state.port, state.token),
            lanUrl = buildLanUrl(addresses.ipv4, state.port, state.token),
            lanIpv6Url = buildLanIpv6Url(addresses.ipv6, state.port, state.token, state.listenMode)
        )
    }

    private fun normalRuntimeEnvFileForBootstrap(mode: RunMode): File? {
        if (mode != RunMode.Normal) return null
        return runCatching {
            val projectDir = RuntimePaths.normalProjectDir(context)
            val entryFile = File(projectDir, "main.js")
            if (!projectDir.exists() || !projectDir.isDirectory || !entryFile.exists()) {
                return@runCatching null
            }
            File(projectDir, "config/.env")
        }.getOrNull()
    }

    private fun readRuntimeEnvValues(envFile: File?): Map<String, String> {
        if (envFile == null || !envFile.exists() || !envFile.isFile) return emptyMap()
        return runCatching {
            DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyMap())
    }

    fun persistSelectedVariant(variant: ApiVariant, commit: Boolean) {
        prefs.edit(commit = commit) { putString(KEY_VARIANT, variant.key) }
        context.getSharedPreferences(PREFS_LEGACY_VARIANT, Context.MODE_PRIVATE).edit(commit = commit) {
            putString(KEY_VARIANT, variant.key)
        }
    }

    fun normalizeVariantKey(raw: String?): String? {
        val value = raw?.trim()?.lowercase().orEmpty()
        return when (value) {
            "stable" -> "stable"
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> null
        }
    }

    fun persistServiceConfigChanges(
        port: Int,
        token: String,
        applyPort: Boolean,
        applyToken: Boolean,
        listenMode: RuntimeListenMode,
        applyListenMode: Boolean
    ) {
        prefs.edit(commit = true) {
            if (applyPort) {
                putInt(KEY_PORT, port)
            }
            if (applyToken) {
                if (token.isBlank()) {
                    remove(KEY_TOKEN)
                } else {
                    putString(KEY_TOKEN, token)
                }
            }
            if (applyListenMode) {
                putString(RuntimeListenMode.PREFERENCE_KEY, listenMode.key)
            }
        }

        val networkAddresses = resolveAddresses()
        state.update { current ->
            val nextPort = if (applyPort) port else current.port
            val nextToken = if (applyToken) {
                token.ifBlank { TokenDefaults.resolveCoreDefaultToken(context) }
            } else {
                current.token
            }
            val nextListenMode = if (applyListenMode) listenMode else current.listenMode
            withResolvedUrls(
                current.copy(
                    port = nextPort,
                    token = nextToken,
                    listenMode = nextListenMode
                ),
                networkAddresses
            )
        }
        VideoShellInjectionConfigPublisher.publishAsync(context)
    }

    /** 接管已运行的 Root 实例时，把从 Root 环境读到的端口/Token/监听模式写回偏好。 */
    fun persistRecoveredRootConfig(port: Int, token: String, listenMode: RuntimeListenMode) {
        prefs.edit {
            putInt(KEY_PORT, port)
            putString(KEY_TOKEN, token)
            putString(RuntimeListenMode.PREFERENCE_KEY, listenMode.key)
        }
    }

    fun ensureRuntimeStartedAt(mode: RunMode, forceNew: Boolean, port: Int): Long {
        val current = readRuntimeStartedAt()
        val fromNormalHealth = if (!forceNew) {
            recoverNormalRuntimeStartedAtFromHealth(port)
        } else {
            null
        }
        val fromRootProcess = if (mode == RunMode.Root) {
            RootRuntimeController.getProcessStartedAtMs(context)
        } else {
            null
        }
        val fromRootPidFile = if (mode == RunMode.Root) {
            RootRuntimeController.getPidFileLastModified(context)
        } else {
            null
        }

        val startedAt = selectRuntimeStartedAtAnchor(
            mode = mode,
            current = current,
            forceNew = forceNew,
            normalHealthStartedAt = fromNormalHealth,
            rootProcessStartedAt = fromRootProcess,
            rootPidFileModifiedAt = fromRootPidFile,
            nowMs = System.currentTimeMillis()
        )
        saveRuntimeStartedAt(startedAt)
        return startedAt
    }

    private fun recoverNormalRuntimeStartedAtFromHealth(port: Int): Long? {
        return calculateStartedAtFromUptime(
            nowMs = System.currentTimeMillis(),
            uptimeSec = healthUptimeSeconds(port)
        )
    }

    fun readRuntimeStartedAt(): Long {
        return prefs.getLong(KEY_RUNTIME_STARTED_AT_MS, 0L)
    }

    fun saveRuntimeStartedAt(startedAt: Long) {
        prefs.edit { putLong(KEY_RUNTIME_STARTED_AT_MS, startedAt) }
    }

    fun clearRuntimeStartedAt() {
        prefs.edit { remove(KEY_RUNTIME_STARTED_AT_MS) }
    }

    fun uptimeSecondsFrom(startedAtMs: Long): Long {
        return ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)) / 1000L
    }

    fun buildLocalUrl(port: Int, token: String): String {
        return RuntimeNetworkAddressResolver.buildHttpUrl("127.0.0.1", port, token)
    }

    fun buildLanUrl(ip: String, port: Int, token: String): String {
        return RuntimeNetworkAddressResolver.buildHttpUrl(ip, port, token)
    }

    fun buildLanIpv6Url(
        ip: String,
        port: Int,
        token: String,
        listenMode: RuntimeListenMode
    ): String {
        if (listenMode != RuntimeListenMode.DualStack) return ""
        return RuntimeNetworkAddressResolver.buildHttpUrl(ip, port, token)
    }
}
