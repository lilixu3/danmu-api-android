package com.example.danmuapiapp.data.network

import android.content.Context
import com.example.danmuapiapp.data.service.AppOutboundSettingsStore
import com.example.danmuapiapp.data.util.safeGetBoolean
import com.example.danmuapiapp.data.util.safeGetString
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import com.example.danmuapiapp.data.service.AppOutboundSettings
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
            factory = { selected -> GithubRouteSnapshot(selected.enhanced, selected.settings, fallback, forced == null) }
        )
        return builder.proxy(null).proxySelector(selector).proxyAuthenticator(selector)
    }
}

internal data class GithubRouteConfiguration(val option: String, val enhanced: Boolean, val settings: AppOutboundSettings) {
    val key: String get() = "$option|$enhanced|${settings.dohUrl}|${settings.connectTimeoutMs}|${settings.proxy.json()}"
}
internal abstract class GithubFrozenRoute : ProxySelector(), Authenticator {
    open fun call(client: OkHttpClient, request: okhttp3.Request): okhttp3.Call =
        client.newBuilder().proxySelector(this).proxyAuthenticator(this).build().newCall(request)
}

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
    private val enhanced: Boolean,
    private val config: AppOutboundSettings, private val fallback: ProxySelector?, private val allowAppProxy: Boolean
) : GithubFrozenRoute() {
    private val proxyRoute = if (allowAppProxy && config.proxy.enabled) config.proxy.rules["github"] ?: "inherit" else "inherit"
    private val useEnhanced = proxyRoute == "enhanced" || (proxyRoute == "inherit" && enhanced)
    private val resolver by lazy { GithubDohResolver(config) }
    override fun select(uri: URI): List<Proxy> {
        if ((useEnhanced || proxyRoute != "inherit") && GithubOutboundNetwork.isTarget(uri)) return listOf(Proxy.NO_PROXY)
        return fallback?.select(uri)?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
    }
    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        if (!useEnhanced || !GithubOutboundNetwork.isTarget(uri)) fallback?.connectFailed(uri, sa, ioe)
    }
    override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? = null

    override fun call(client: OkHttpClient, request: okhttp3.Request): okhttp3.Call {
        if (GithubOutboundNetwork.isTarget(request.url.toUri()) && proxyRoute.startsWith("node:")) {
            val node = config.proxy.nodes.firstOrNull { "node:${it.id}" == proxyRoute }
            if (node == null) return client.newBuilder().addInterceptor { throw IOException("GitHub 绑定的代理节点已失效") }.build().newCall(request)
            return client.withProxyNode(node).newCall(request)
        }
        if (!useEnhanced || !GithubOutboundNetwork.isTarget(request.url.toUri())) return super.call(client, request)
        val cancellation = GithubDnsCancellation()
        val delegate = client.newBuilder().proxySelector(this).proxyAuthenticator(this)
            .dns(GithubCallDns(resolver, cancellation)).build().newCall(request)
        return object : okhttp3.Call by delegate {
            override fun cancel() { cancellation.cancel(); delegate.cancel() }
            override fun clone(): okhttp3.Call = call(client, request)
        }
    }
}

/** All shared HTTP entry points freeze a route before OkHttp considers pooled connections. */
internal fun OkHttpClient.newOutboundCall(request: okhttp3.Request): okhttp3.Call {
    val selector = proxySelector as? GithubRouteSelector ?: return newCall(request)
    if (proxy != null) return newCall(request)
    val snapshot = selector.snapshot()
    return snapshot.call(this, request)
}
