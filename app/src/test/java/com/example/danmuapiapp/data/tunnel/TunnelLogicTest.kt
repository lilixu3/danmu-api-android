package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelLogicTest {

    private val form = TunnelFormSettings(
        serverAddr = "frp.example.com",
        serverPort = 7000,
        authToken = "tok",
        remotePort = 19321
    )

    @Test
    fun `build toml contains required fields`() {
        val toml = buildFrpcToml(form, targetPort = 19321)
        assertTrue(toml.contains("serverAddr = \"frp.example.com\""))
        assertTrue(toml.contains("serverPort = 7000"))
        assertTrue(toml.contains("auth.token = \"tok\""))
        assertTrue(toml.contains("dnsServer = \"$DEFAULT_DNS_SERVER\""))
        assertTrue(toml.contains("loginFailExit = false"))
        assertTrue(toml.contains("localPort = 19321"))
        assertTrue(toml.contains("remotePort = 19321"))
    }

    @Test
    fun `parse toml and validate local port`() {
        val text = """
            serverAddr = "frp.example.com"
            serverPort = 7000
            [[proxies]]
            name = "api"
            type = "tcp"
            localIP = "127.0.0.1"
            localPort = 5321
            remotePort = 19321
        """.trimIndent()
        val summary = parseFrpcConfig(text)
        assertEquals("frp.example.com", summary.serverAddr)
        assertEquals(7000, summary.serverPort)
        assertEquals(1, summary.proxies.size)
        val check = checkPastedConfig(text, targetPort = 9321)
        assertFalse(check.ok)
        assertTrue(check.errors.any { it.contains("localPort=5321") })
    }

    @Test
    fun `inject defaults is idempotent and stays before tables`() {
        val text = """
            serverAddr = "frp.example.com"

            [[proxies]]
            name = "api"
        """.trimIndent()
        val once = injectFrpcDefaults(text)
        assertTrue(once.contains("dnsServer = \"$DEFAULT_DNS_SERVER\""))
        assertTrue(once.contains("loginFailExit = false"))
        assertTrue(once.indexOf("dnsServer") < once.indexOf("[[proxies]]"))
        assertEquals(once, injectFrpcDefaults(once))
    }

    @Test
    fun `sync local port rewrites all proxies`() {
        val text = """
            serverAddr = "frp.example.com"
            [[proxies]]
            localPort = 9321
            remotePort = 19321
        """.trimIndent()
        val patched = syncPastedLocalPort(text, 19321)
        assertEquals(19321, parseFrpcConfig(patched).proxies.first().localPort)
        assertTrue(patched.contains("remotePort = 19321"))
    }

    @Test
    fun `derive public address and build url`() {
        val settings = TunnelSettings(form = form)
        assertEquals("frp.example.com:19321", derivePublicAddress(settings))
        assertEquals(
            "http://frp.example.com:19321/abc",
            buildPublicApiUrl(derivePublicAddress(settings), "abc")
        )
        val overridden = settings.copy(publicAddress = "https://danmu.example.com/")
        assertEquals(
            "https://danmu.example.com/abc",
            buildPublicApiUrl(derivePublicAddress(overridden), "abc")
        )
    }

    @Test
    fun `log link state picks the last marker`() {
        val log = """
            [I] try to connect to server...
            [I] login to server success, get run id [abc]
            [I] [api] start proxy success
        """.trimIndent()
        assertEquals(TunnelLinkState.Connected, parseFrpcLogLinkState(log))
        val failed = "login to server failed: i/o timeout"
        assertEquals(TunnelLinkState.Error, parseFrpcLogLinkState(failed))
        assertTrue(lastFrpcError(failed).contains("i/o timeout"))
    }

    @Test
    fun `settings json round trip`() {
        val settings = TunnelSettings(
            enabled = true,
            autoStart = true,
            mode = TunnelMode.Paste,
            publicAddress = "frp.example.com:19321",
            configText = "serverAddr = \"x\"",
            riskAcknowledged = true,
            form = form
        )
        val round = TunnelSettings.decode(settings.encode())
        assertTrue(round.enabled)
        assertTrue(round.autoStart)
        assertEquals(TunnelMode.Paste, round.mode)
        assertEquals("frp.example.com:19321", round.publicAddress)
        assertEquals("serverAddr = \"x\"", round.configText)
        assertEquals(7000, round.form.serverPort)
    }

    @Test
    fun `default token is flagged as unsafe`() {
        assertTrue(isUnsafeApiToken(""))
        assertTrue(isUnsafeApiToken(" $DEFAULT_API_TOKEN "))
        assertFalse(isUnsafeApiToken("my-secret"))
    }
}
