package com.example.danmuapiapp.data.network

import android.content.Context
import com.example.danmuapiapp.data.service.AppOutboundSettingsStore
import com.example.danmuapiapp.data.util.safeGetBoolean
import com.example.danmuapiapp.data.util.safeGetString
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.SecureRandom
import com.example.danmuapiapp.data.service.AppOutboundSettings
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** A separate GitHub route: no core installation, Node server, or root is required. */
internal object GithubOutboundNetwork {
    const val OPTION_ID = "github_enhanced"
    val hosts = setOf(
        "github.com", "api.github.com", "raw.githubusercontent.com", "codeload.github.com",
        "release-assets.githubusercontent.com", "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    fun isTarget(uri: URI): Boolean = uri.scheme == "https" && uri.host in hosts &&
        uri.port in listOf(-1, 443) && uri.userInfo == null

    fun isSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences("github_proxy_prefs", Context.MODE_PRIVATE)
        val id = prefs.safeGetString("selected_proxy")
        return id == OPTION_ID && prefs.safeGetBoolean("has_user_selected_proxy", true)
    }

    fun createClient(context: Context): OkHttpClient = configure(context, OkHttpClient.Builder())
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
        .followRedirects(true).build()

    /** Also used by speed tests; the tested route must not inherit the selected one. */
    fun forOption(context: Context, client: OkHttpClient, enhanced: Boolean): OkHttpClient =
        configure(context, client.newBuilder(), enhanced).build()

    private fun configure(context: Context, builder: OkHttpClient.Builder, forced: Boolean? = null): OkHttpClient.Builder {
        val appContext = context.applicationContext
        val fallback = ProxySelector.getDefault()
        val selector = GithubRouteSelector(
            configuration = {
                val prefs = appContext.getSharedPreferences("github_proxy_prefs", Context.MODE_PRIVATE)
                GithubRouteConfiguration(prefs.safeGetString("selected_proxy"), forced ?: isSelected(appContext), AppOutboundSettingsStore.read(appContext))
            },
            factory = { selected -> GithubRouteSnapshot(appContext, selected.enhanced, selected.settings, fallback) }
        )
        return builder.proxy(null).proxySelector(selector).proxyAuthenticator(selector)
    }
}

internal data class GithubRouteConfiguration(val option: String, val enhanced: Boolean, val settings: AppOutboundSettings) {
    val key: String get() = "$option|$enhanced|${settings.dohUrl}|${settings.connectTimeoutMs}"
}
internal abstract class GithubFrozenRoute : ProxySelector(), Authenticator

/** Stable per-generation address identity. Existing streams keep their own route. */
internal class GithubRouteSelector(
    private val configuration: () -> GithubRouteConfiguration,
    private val factory: (GithubRouteConfiguration) -> GithubFrozenRoute
) : ProxySelector(), Authenticator {
    private val snapshots = LinkedHashMap<String, GithubFrozenRoute>()
    @Synchronized fun snapshot(): GithubFrozenRoute {
        val selected = configuration()
        return snapshots.getOrPut(selected.key) {
            if (snapshots.size >= 32) snapshots.remove(snapshots.keys.first())
            factory(selected)
        }
    }
    override fun select(uri: URI): List<Proxy> = snapshot().select(uri)
    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = snapshot().connectFailed(uri, sa, ioe)
    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? = snapshot().authenticate(route, response)
}

private class GithubRouteSnapshot(
    private val context: Context, private val enhanced: Boolean,
    private val config: AppOutboundSettings, private val fallback: ProxySelector?
) : GithubFrozenRoute() {
    override fun select(uri: URI): List<Proxy> {
        if (enhanced && GithubOutboundNetwork.isTarget(uri)) return listOf(GithubOutboundHelper.endpoint(context, config).proxy)
        return fallback?.select(uri)?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
    }
    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        if (!enhanced || !GithubOutboundNetwork.isTarget(uri)) fallback?.connectFailed(uri, sa, ioe)
    }
    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
        if (!enhanced || !GithubOutboundNetwork.isTarget(response.request.url.toUri()) || route?.proxy?.type() != Proxy.Type.HTTP || response.code != 407 || response.request.header("Proxy-Authorization") != null) return null
        val endpoint = GithubOutboundHelper.byAddress(route.proxy.address()) ?: return null
        return response.request.newBuilder().header("Proxy-Authorization", "Bearer ${endpoint.token}").build()
    }
}

/** All shared HTTP entry points freeze a route before OkHttp considers pooled connections. */
internal fun OkHttpClient.newOutboundCall(request: okhttp3.Request): okhttp3.Call {
    val selector = proxySelector as? GithubRouteSelector ?: return newCall(request)
    if (proxy != null) return newCall(request)
    val snapshot = selector.snapshot()
    return newBuilder().proxySelector(snapshot).proxyAuthenticator(snapshot).build().newCall(request)
}

internal data class GithubHelperEndpoint(val process: Process, val proxy: Proxy, val token: String) {
    var retired: Boolean = false
}

/** One app-owned helper, shared by update/download/PR clients, lazy and idle-exiting. */
internal object GithubOutboundHelper {
    @Volatile private var running: GithubHelperEndpoint? = null
    private val generations = LinkedHashMap<String, GithubHelperEndpoint>()
    @Synchronized fun byAddress(address: SocketAddress): GithubHelperEndpoint? = generations.values.firstOrNull { it.proxy.address() == address && alive(it.process) }
    fun currentEndpoint(): GithubHelperEndpoint? = running?.takeIf { alive(it.process) }
    fun currentAddress(): SocketAddress? = currentEndpoint()?.proxy?.address()
    private fun alive(process: Process): Boolean = try { process.exitValue(); false } catch (_: IllegalThreadStateException) { true }

    @Synchronized fun endpoint(context: Context, config: AppOutboundSettings = AppOutboundSettingsStore.read(context)): GithubHelperEndpoint {
        // Keep old generations until their tunnels finish/idle-exit. Never interrupt downloads on a settings change.
        val iterator = generations.iterator()
        while (iterator.hasNext()) {
            val previous = iterator.next().value
            if (!alive(previous.process)) {
                runCatching { previous.process.outputStream.close() }
                runCatching { previous.process.inputStream.close() }
                runCatching { previous.process.errorStream.close() }
                iterator.remove()
            }
        }
        val key = "${config.dohUrl}|${config.connectTimeoutMs}"
        generations[key]?.takeUnless { it.retired }?.let { running = it; return it }
        if (generations.size >= 8) throw IOException("旧网络连接仍在完成，请稍后重试")
        val binary = AppOutboundSettingsStore.helperFile(context)
        if (!binary.isFile || !binary.canExecute()) throw IOException("APK 缺少可用的增强直连组件")
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        val process = ProcessBuilder(
            binary.absolutePath, "--github-proxy", "--http-version", "h2",
            "--connect-timeout-ms", config.connectTimeoutMs.toString(), "--doh-url", config.dohUrl
        ).apply { environment()["DANMU_OUTBOUND_TOKEN"] = token }.start()
        val ready = CompletableFuture<String>()
        Thread({
            try {
                process.inputStream.bufferedReader().use { reader ->
                    ready.complete(reader.readLine() ?: throw IOException("网络组件未就绪"))
                    while (reader.readLine() != null) { /* Drain; no endpoint/token logging. */ }
                }
            } catch (error: Exception) { ready.completeExceptionally(error) }
        }, "github-outbound-ready").apply { isDaemon = true; start() }
        Thread({ runCatching { process.errorStream.use { input ->
            val buffer = ByteArray(1024)
            while (input.read(buffer) != -1) { /* Do not log resolver paths or secrets. */ }
        } } }, "github-outbound-stderr").apply { isDaemon = true; start() }
        try {
            val value = JSONObject(ready.get(2000, TimeUnit.MILLISECONDS))
            val address = URI(value.getString("url"))
            if (!value.optBoolean("ready") || value.optInt("appProtocol") != 1 ||
                address.scheme != "http" || address.host != "127.0.0.1" || address.port !in 1..65535 ||
                address.userInfo != null || !alive(process)) throw IOException("网络组件启动响应无效")
            return GithubHelperEndpoint(process, Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", address.port)), token)
                .also { endpoint ->
                    generations.values.filter { !it.retired }.forEach { previous ->
                        // Close only stdin: the helper drains active CONNECT tunnels and then exits.
                        runCatching { previous.process.outputStream.close() }
                        previous.retired = true
                    }
                    running = endpoint
                    // Multiple generations of the same settings can coexist while a download drains.
                    generations[key]?.let { previous -> generations["$key|retired|${previous.proxy.address()}"] = previous }
                    generations[key] = endpoint
                }
        } catch (error: Exception) {
            runCatching { process.outputStream.close() }
            process.destroy()
            throw IOException("增强直连组件启动失败", error)
        }
    }
}
