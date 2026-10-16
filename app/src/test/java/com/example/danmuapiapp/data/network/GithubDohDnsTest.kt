package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.service.AppOutboundSettings
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

class GithubDohDnsTest {
    private fun response(query: ByteArray, ttl: Int = 60): ByteArray {
        val kind = ((query[query.size - 4].toInt() and 255) shl 8) or (query[query.size - 3].toInt() and 255)
        val header = query.clone().apply { this[2] = 0x81.toByte(); this[3] = 0x80.toByte(); this[7] = if (kind == 1) 1 else 0 }
        return ByteArrayOutputStream().also { buffer ->
            buffer.write(header)
            if (kind == 1) DataOutputStream(buffer).apply {
                writeShort(0xc00c); writeShort(1); writeShort(1); writeInt(ttl); writeShort(4); write(byteArrayOf(8, 8, 8, 8)); flush()
            }
        }.toByteArray()
    }
    @Test fun `decoder rejects mismatched truncated and local DNS answers`() {
        val query = GithubDnsWire.query("api.github.com", 1, 123)
        val answer = response(query)
        assertEquals("8.8.8.8", GithubDnsWire.parse(answer, query).addresses.single().hostAddress)
        assertEquals(60L, GithubDnsWire.parse(answer, query).ttl)
        assertThrows(java.io.IOException::class.java) { GithubDnsWire.parse(answer.copyOf(answer.size - 1), query) }
        assertThrows(java.io.IOException::class.java) { GithubDnsWire.parse(answer.clone().apply { this[1] = 1 }, query) }
        for (ip in listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "100.64.0.1", "::1", "fc00::1", "2001:db8::1")) {
            assertFalse(ip, GithubDnsWire.publicAddress(InetAddress.getByName(ip)))
        }
    }
    @Test fun `native DoH caches by TTL and cancellation fails without system DNS fallback`() {
        val count = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/dns-query") { exchange ->
            val query = exchange.requestBody.readBytes(); val bytes = response(query); count.incrementAndGet()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val resolver = GithubDohResolver(AppOutboundSettings(dohUrl = "http://127.0.0.1:${server.address.port}/dns-query", connectTimeoutMs = 2000))
            assertEquals("8.8.8.8", resolver.lookup("api.github.com", GithubDnsCancellation()).single().hostAddress)
            val before = count.get()
            assertEquals("8.8.8.8", resolver.lookup("api.github.com", GithubDnsCancellation()).single().hostAddress)
            assertEquals(before, count.get())
            val cancelled = GithubDnsCancellation().apply { cancel() }
            assertThrows(java.io.IOException::class.java) { resolver.lookup("github.com", cancelled) }
            assertEquals(before, count.get())
        } finally { server.stop(0) }
    }
}
