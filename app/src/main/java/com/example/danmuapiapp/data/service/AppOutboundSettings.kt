package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.data.network.newOutboundCall

import android.content.Context
import android.util.AtomicFile
import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.example.danmuapiapp.NodeBridge
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import com.example.danmuapiapp.domain.model.RunMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/** App-owned configuration. Core env catalogs and core updates do not own this file. */
data class AppOutboundSettings(
    val enabled: Boolean = false,
    val sources: Set<String> = linkedSetOf("bahamut", "tmdb"),
    val httpVersion: String = "auto",
    val dohUrl: String = "",
    val connectTimeoutMs: Int = 3000,
    val proxy: AppProxySettings = AppProxySettings()
) {
    /** One explicit choice owns the entire source route. Disabled rules stay inactive. */
    fun withSourceRoute(source: String, route: String): AppOutboundSettings {
        require(source.isNotBlank() && source != "local") { "此来源不需要网络线路" }
        require(route in listOf("inherit", "enhanced", "direct") || proxy.nodes.any { route == "node:${it.id}" }) { "请选择有效线路" }
        require(route != "enhanced" || source in supportedSources || source == "github") { "此来源暂不支持增强直连" }
        val activeSources = if (enabled) sources else emptySet()
        val enhancedSources = if (source in supportedSources && route == "enhanced") activeSources + source else activeSources - source
        val activeRules = if (proxy.enabled) proxy.rules.mapValues { (key, value) ->
            if (!enabled && value == "enhanced" && key != "github") "inherit" else value
        } else emptyMap()
        return copy(
            enabled = enabled || (route == "enhanced" && source in supportedSources),
            sources = enhancedSources,
            proxy = proxy.copy(enabled = true,
                defaultRoute = if (proxy.enabled) proxy.defaultRoute else "inherit",
                rules = activeRules + (source to route))
        ).validate()
    }

    fun validate(): AppOutboundSettings {
        proxy.validate()
        require(sources.all { it in supportedSources }) { "不支持的增强直连来源" }
        require(httpVersion in listOf("auto", "h2", "h3")) { "协议必须为 auto、h2 或 h3" }
        require(connectTimeoutMs in 1..60000) { "连接超时须在 1–60000 毫秒之间" }
        if (dohUrl.isNotBlank()) {
            val url = URI(dohUrl.trim())
            require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.fragment == null) {
                "DoH 必须为不含凭据和片段的 HTTPS 地址"
            }
        }
        return this
    }

    fun encode(): String = JSONObject()
        .put("enabled", enabled).put("sources", JSONArray(sources.toList()))
        .put("httpVersion", httpVersion).put("dohUrl", dohUrl.trim())
        .put("connectTimeoutMs", connectTimeoutMs).put("proxy", proxy.json()).toString()

    companion object {
        val sourceLabels = linkedMapOf("bahamut" to "巴哈姆特", "tmdb" to "TMDB", "dandan" to "弹弹play",
            "animeko" to "Animeko", "douban" to "豆瓣")
        val supportedSources = sourceLabels.keys.toList()
        fun decode(text: String): AppOutboundSettings {
            val value = JSONObject(text)
            val sources = value.optJSONArray("sources")
            return AppOutboundSettings(
                enabled = value.optBoolean("enabled", false),
                sources = if (sources == null) linkedSetOf("bahamut", "tmdb") else
                    (0 until sources.length()).map { sources.getString(it) }.toSet(),
                httpVersion = value.optString("httpVersion", "auto"),
                dohUrl = value.optString("dohUrl", ""),
                connectTimeoutMs = value.optInt("connectTimeoutMs", 3000),
                proxy = AppProxySettings.decode(value.optJSONObject("proxy"))
            ).validate()
        }
    }
}

data class AppOutboundConnectivityResult(
    val source: String,
    val connected: Boolean,
    val durationMs: Long? = null,
    val reason: String? = null,
    val configurationRequired: Boolean = false,
    val unconfirmed: Boolean = false
)

object AppOutboundSettingsStore {
    private val diagnosticClient by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }
    private fun directory(context: Context) = File(context.createDeviceProtectedStorageContext().filesDir, "outbound")
    fun configFile(context: Context) = File(directory(context), "settings.json")
    @Synchronized fun read(context: Context): AppOutboundSettings = runCatching {
        val raw=configFile(context).readText()
        val settings=AppOutboundSettings.decode(raw)
        val proxy=JSONObject(raw).optJSONObject("proxy")
        val nodes=proxy?.optJSONArray("nodes")
        val oldDirect=proxy?.let { value -> value.optString("defaultRoute")=="direct" ||
            (value.optInt("routeVersion",1)<2 && value.optJSONObject("rules")?.let { rules -> rules.keys().asSequence().any {rules.optString(it)=="direct"} }==true)
        }==true
        val legacy=oldDirect || proxy?.has("subscriptions")==true || (0 until (nodes?.length() ?: 0)).any {
            val node=nodes!!.optJSONObject(it)
            node?.has("protocolOptions")==true || node?.has("subscriptionId")==true || node?.optString("type") !in AppProxyNode.supportedTypes
        }
        if(legacy) save(context,settings)
        settings
    }.getOrDefault(AppOutboundSettings())

    @Synchronized fun save(context: Context, settings: AppOutboundSettings) {
        settings.validate()
        val file = configFile(context)
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(settings.encode().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            file.setReadable(false, false); file.setReadable(true, true)
            file.setWritable(false, false); file.setWritable(true, true)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
        RootAutoStartModule.scheduleInstalledScriptMigration(context)
    }

    @Synchronized fun saveProxy(context: Context, proxy: AppProxySettings) {
        val current = read(context)
        val enhanced = proxy.rules.filterValues { it == "enhanced" }.keys.intersect(AppOutboundSettings.supportedSources.toSet())
        save(context, current.copy(proxy = proxy, enabled = current.enabled || (proxy.enabled && enhanced.isNotEmpty()), sources = current.sources + enhanced))
    }

    @Synchronized fun saveSourceRoute(context: Context, source: String, route: String): AppOutboundSettings {
        val next = read(context).withSourceRoute(source, route)
        save(context, next)
        return next
    }

    fun ensureConfig(context: Context): String {
        if (!configFile(context).exists()) save(context, AppOutboundSettings())
        return configFile(context).absolutePath
    }

    /** Both JNI and root app_process ultimately inherit these reserved paths. */
    fun installEnvironment(context: Context) {
        val file = configFile(context)
        if (!file.exists()) save(context, AppOutboundSettings())
        NodeBridge.setEnvironmentVariable("DANMU_APP_OUTBOUND_CONFIG", file.absolutePath, true)
    }

    suspend fun diagnose(context: Context, source: String): AppOutboundConnectivityResult {
        require(source in AppOutboundSettings.supportedSources)
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val access = RuntimeApiAccessResolver.resolve(context, prefs, 9321)
        val url = RuntimeApiUrls.local(access.port, access.tokenPaths.firstOrNull().orEmpty(), "__outbound") + "?source=$source"
        val request = Request.Builder().url(url).applyRuntimeApiAuth(access).post(ByteArray(0).toRequestBody()).build()
        return diagnosticClient.newOutboundCall(request).useCancellableResponse { response ->
            val raw = response.body.string()
            val result = runCatching { JSONObject(raw) }.getOrNull()
            AppOutboundConnectivityResult(
                source = source,
                connected = response.isSuccessful && result?.optBoolean("ok") == true,
                durationMs = result?.optLong("durationMs", -1L)?.takeIf { it >= 0L },
                reason = result?.optString("reason")?.takeIf { it.isNotBlank() },
                configurationRequired = result?.optBoolean("configurationRequired") == true
            )
        }
    }

    fun statusText(context: Context): String {
        if (!read(context).enabled) return "关闭"
        val file = File(directory(context), "status.json")
        val raw = runCatching { file.readText() }.getOrElse {
            if (RuntimeModePrefs.get(context) != RunMode.Root) return "等待服务启动"
            RootShell.exec("cat ${shellQuote(file.absolutePath)} 2>/dev/null", 2000).stdout
        }
        return runCatching {
            val status = JSONObject(raw)
            if (System.currentTimeMillis() - status.optLong("heartbeat") > 90000) return "服务未运行或状态已过期"
            val label = when (status.optString("status")) {
                "ready" -> "已就绪 · ${status.optString("httpVersion", "auto")}"
                "starting" -> "组件启动中"
                "failed" -> "组件失败"
                else -> "等待服务启动"
            }
            val reason = status.optString("reason")
            if (reason.isBlank()) label else "$label：$reason"
        }.getOrDefault("等待服务启动")
    }
}
