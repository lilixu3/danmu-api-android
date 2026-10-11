package com.example.danmuapiapp.data.tunnel

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

class TunnelDiagnosticsTest {
    private fun server(pid: Long, coreCode: Int = 200, hits: AtomicInteger = AtomicInteger(),
        onPage: () -> Unit = {}): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val health = exchange.requestURI.path == "/__health"
            val page = !health && !exchange.requestURI.path.endsWith("/api/config")
            if (!health) hits.incrementAndGet()
            if (page) onPage()
            val code = if (health || page) 200 else coreCode
            val body = when {
                health -> """{"ok":true,"pid":$pid,"runtimeIdentity":"fixture"}"""
                page -> "<html><body>" + "x".repeat(500000) + "</body></html>"
                else -> """{"success":true}"""
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server
    }

    @Test fun `matching PIDs without runtime identity do not prove the public endpoint is ours`() {
        for (missing in listOf("", "null")) {
            val local = TunnelProbe("local", code = 200, pid = 42424, runtimeIdentity = missing)
            val public = TunnelProbe("public", code = 200, pid = 42424, runtimeIdentity = missing)
            assertFalse(sameTunnelRuntime(local, public))
        }
        val local = TunnelProbe("local", code = 200, pid = 42424, runtimeIdentity = "instance-a")
        val public = TunnelProbe("public", code = 200, pid = 42424, runtimeIdentity = "instance-b")
        assertFalse(sameTunnelRuntime(local, public))
    }

    @Test fun `diagnosis compares local and public entry plus core without exposing the token`() = runBlocking {
        val local = server(42424)
        val public = server(42424)
        try {
            val report = TunnelDiagnostics(OkHttpClient()).check(local.address.port, "sensitive-token", "http://127.0.0.1:${public.address.port}")
            assertTrue(report.probes.all { it.ok })
            assertEquals(7, report.probes.size)
            assertTrue(report.probes.last().bytesRead > 64 * 1024)
            assertTrue(report.summary.contains("均正常"))
            assertFalse(report.text().contains("sensitive-token"))
        } finally { local.stop(0); public.stop(0) }
    }

    @Test fun `public page is measured before local page and rechecked afterwards`() = runBlocking {
        val order = CopyOnWriteArrayList<String>()
        val local = server(42424, onPage = { order.add("local") })
        val public = server(42424, onPage = { order.add("public") })
        try {
            val report = TunnelDiagnostics(OkHttpClient()).check(local.address.port, "token", "http://127.0.0.1:${public.address.port}")
            assertTrue(report.probes.all { it.ok })
            assertEquals(listOf("public", "local", "public"), order.toList())
        } finally { local.stop(0); public.stop(0) }
    }

    @Test fun `recovery indication distinguishes slow or timed out transfers from HTTP failures`() {
        val after = TunnelProbe("after", code = 200, elapsedMs = 400)
        assertTrue(publicPageImproved(TunnelProbe("before", elapsedMs = 5000, error = "请求超时"), after))
        assertTrue(publicPageImproved(TunnelProbe("before", code = 200, elapsedMs = 3200), after))
        assertFalse(publicPageImproved(TunnelProbe("before", code = 401), after))
        assertFalse(publicPageImproved(TunnelProbe("before", code = 200, elapsedMs = 500), after))
        assertFalse(publicPageImproved(TunnelProbe("before", code = 200, elapsedMs = 3200), after.copy(error = "请求超时")))
    }

    @Test fun `incomplete public response retains HTTP code byte progress and header timing`() = runBlocking {
        val local = server(42424)
        val public = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        public.createContext("/") { exchange ->
            val body = when (exchange.requestURI.path) {
                "/__health" -> """{"ok":true,"pid":42424,"runtimeIdentity":"fixture"}"""
                "/token/api/config" -> """{"success":true}"""
                else -> "<html>" + "x".repeat(8192)
            }.toByteArray()
            val isPage = exchange.requestURI.path == "/token"
            exchange.sendResponseHeaders(200, if (isPage) 500000L else body.size.toLong())
            try {
                exchange.responseBody.write(body)
                exchange.responseBody.flush()
            } finally { exchange.close() }
        }
        public.start()
        try {
            val report = TunnelDiagnostics(OkHttpClient()).check(local.address.port, "token", "http://127.0.0.1:${public.address.port}")
            val page = report.probes.first { it.label.startsWith("公网页面") }
            assertFalse(page.ok)
            assertEquals(200, page.code)
            assertTrue(page.bytesRead >= 8192)
            assertEquals(500000L, page.expectedBytes)
            assertTrue(page.headerElapsedMs >= 0)
            assertTrue(page.display().contains("HTTP 200"))
            assertTrue(page.display().contains("正文"))
            assertFalse(report.text().contains("token"))
        } finally { local.stop(0); public.stop(0) }
    }

    @Test fun `foreign public runtime is detected before sending API credentials`() = runBlocking {
        val hits = AtomicInteger()
        val local = server(42424)
        val public = server(31313, hits = hits)
        try {
            val report = TunnelDiagnostics(OkHttpClient()).check(local.address.port, "sensitive-token", "http://127.0.0.1:${public.address.port}")
            assertTrue(report.summary.contains("另一服务实例"))
            assertEquals(0, hits.get())
            assertEquals(2, report.probes.size)
        } finally { local.stop(0); public.stop(0) }
    }

    @Test fun `public core failure is distinguished from local core failure`() = runBlocking {
        val local = server(42424)
        val public = server(42424, coreCode = 504)
        try {
            val report = TunnelDiagnostics(OkHttpClient()).check(local.address.port, "token", "127.0.0.1:${public.address.port}")
            assertTrue(report.summary.contains("本机核心正常"))
            assertTrue(report.summary.contains("公网访问核心异常"))
            assertEquals(504, report.probes.single { it.label == "公网核心" }.code)
        } finally { local.stop(0); public.stop(0) }
    }
}
