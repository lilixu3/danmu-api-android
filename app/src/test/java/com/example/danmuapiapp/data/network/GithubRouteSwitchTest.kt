package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.service.AppOutboundSettings
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.SocketAddress
import java.io.IOException

class GithubRouteSwitchTest {
    private fun route(proxy: Proxy) = object : GithubFrozenRoute() {
        override fun select(uri: URI) = listOf(proxy)
        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {}
        override fun authenticate(route: Route?, response: Response): Request? = null
    }
    @Test fun `settings change changes connection identity and unchanged settings reuse it`() {
        var config = GithubRouteConfiguration("original", false, AppOutboundSettings())
        val selector = GithubRouteSelector({ config }) { route(Proxy.NO_PROXY) }
        val original = selector.snapshot()
        assertSame(original, selector.snapshot())
        config = config.copy(option = "github_enhanced", enhanced = true)
        val enhanced = selector.snapshot()
        assertNotSame(original, enhanced)
        config = config.copy(settings = config.settings.copy(dohUrl = "https://dns.example/query"))
        assertNotSame(enhanced, selector.snapshot())
        config = config.copy(option = "original", enhanced = false, settings = AppOutboundSettings())
        assertSame(original, selector.snapshot())
    }
    @Test fun `route switch cannot borrow an old pooled connection and in-flight call keeps its route`() {
        fun server(label: String) = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val data = label.toByteArray(); exchange.sendResponseHeaders(200, data.size.toLong())
                exchange.responseBody.use { it.write(data) }
            }; start()
        }
        val a = server("A"); val b = server("B")
        var selection = "A"
        val selector = GithubRouteSelector({ GithubRouteConfiguration(selection, false, AppOutboundSettings()) }) { selected ->
            route(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", (if (selected.option == "A") a else b).address.port)))
        }
        val client = OkHttpClient.Builder().proxySelector(selector).proxyAuthenticator(selector).build()
        val request = Request.Builder().url("http://github.test/resource").build()
        try {
            assertEquals("A", client.newOutboundCall(request).execute().use { it.body.string() })
            val pendingOldRoute = client.newOutboundCall(request)
            selection = "B"
            assertEquals("B", client.newOutboundCall(request).execute().use { it.body.string() })
            assertEquals("A", pendingOldRoute.execute().use { it.body.string() })
        } finally { client.connectionPool.evictAll(); a.stop(0); b.stop(0) }
    }
}
