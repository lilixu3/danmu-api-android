package com.example.danmuapiapp.data.util

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMappingRefreshTest {
    @Test
    fun refreshUsesSavedRuntimeAddressAndAdminRouteForEachTable() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val saved = "https://example.test/table.txt"
        val vars = JSONObject().put(RemoteMapping.Title.key, saved).put(RemoteMapping.Season.key, saved)
        val posts = mutableListOf<String>()
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val (status, result) = when {
                !path.startsWith("/admin/api/") -> 401 to JSONObject()
                path.endsWith("/config") && exchange.requestMethod == "GET" ->
                    200 to JSONObject().put("originalEnvVars", vars)
                exchange.requestMethod == "POST" -> {
                    posts.add(path)
                    200 to JSONObject().put("success", true).put("count", 7)
                }
                else -> 404 to JSONObject()
            }
            val bytes = result.toString().toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = OkHttpClient()
            val port = server.address.port
            assertTrue(runCatching { RemoteMapping.Title.refresh(client, port, "user", saved) }.isFailure)
            assertTrue(runCatching { RemoteMapping.Title.refresh(client, port, "admin", "$saved.changed") }.isFailure)
            assertTrue(posts.isEmpty())
            for (mapping in RemoteMapping.entries) assertEquals(7, mapping.refresh(client, port, "admin", saved))
            assertEquals(listOf("/admin/api/title-mapping/refresh", "/admin/api/auto-match-mapping/refresh"), posts)
        } finally {
            server.stop(0)
        }
    }
}
