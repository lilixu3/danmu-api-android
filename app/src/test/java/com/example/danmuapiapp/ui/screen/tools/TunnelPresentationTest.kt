package com.example.danmuapiapp.ui.screen.tools

import com.example.danmuapiapp.data.tunnel.*
import org.junit.Assert.*
import org.junit.Test

class TunnelPresentationTest {
    @Test fun `a running process is not proof of a connected tunnel`() {
        for (link in listOf(TunnelLinkState.Unknown, TunnelLinkState.Connecting, TunnelLinkState.Error)) {
            assertNotEquals(TunnelTone.Active, tunnelStatus(TunnelUiState(running = true, linkState = link)).tone)
        }
        assertEquals(TunnelTone.Active, tunnelStatus(TunnelUiState(running = true, linkState = TunnelLinkState.Connected)).tone)
    }

    @Test fun `stale connected log cannot make a stopped tunnel look connected`() {
        assertNotEquals(TunnelTone.Active, tunnelStatus(TunnelUiState(running = false, linkState = TunnelLinkState.Connected)).tone)
        assertEquals(TunnelTone.Pending, tunnelStatus(TunnelUiState(state = "retrying", linkState = TunnelLinkState.Connected)).tone)
    }

    @Test fun `draft round trip retains all server and import settings`() {
        val settings = TunnelSettings(enabled = true, autoStart = true, mode = TunnelMode.Paste,
            publicAddress = "https://danmu.example.com", configText = "serverAddr = \"frp.example.com\"",
            riskAcknowledged = true,
            form = TunnelFormSettings(serverAddr = "example.com", serverPort = 7001, authToken = "example-token",
                proxyType = TunnelProxyType.Http, remotePort = 19000, customDomains = "danmu.example.com",
                subdomain = "danmu", proxyName = "phone", tlsEnabled = false, dnsServer = "1.1.1.1", autoFillDefaults = false))
        assertEquals(settings, TunnelDraft.from(settings).settings())
    }

    @Test fun `switching to form keeps imported text for a later switch back`() {
        val imported = TunnelDraft(mode = TunnelMode.Paste, pasteText = "serverAddr = \"frp.example.com\"")
        val savedForm = imported.copy(mode = TunnelMode.Form, serverAddr = "other.example.com", remotePort = "19321").settings()
        assertEquals(imported.pasteText, TunnelDraft.from(savedForm).copy(mode = TunnelMode.Paste).pasteText)
    }

    @Test fun `incomplete ports stay editable and cannot be saved`() {
        val draft = TunnelDraft(serverAddr = "frp.example.com", serverPort = "", remotePort = "99999")
        assertEquals("", draft.serverPort)
        assertFalse(draft.check(9321).ok)
        assertTrue(draft.copy(serverPort = "7000", remotePort = "19321").check(9321).ok)
    }

    @Test fun `import keeps source text while generated config follows service port`() {
        val text = """
            serverAddr = "frp.example.com"
            serverPort = 7000
            [[proxies]]
            name = "danmu"
            type = "tcp"
            localIP = "127.0.0.1"
            localPort = 9321
            remotePort = 19321
        """.trimIndent()
        val draft = TunnelDraft(mode = TunnelMode.Paste, pasteText = text)
        val effective = draft.effectiveConfig(12345)
        assertEquals(12345, parseFrpcConfig(effective).proxies.first().localPort)
        assertTrue(effective.contains("dnsServer"))
        assertEquals(text, draft.settings().configText)
        assertTrue(draft.check(12345).ok)
    }

    @Test fun `form draft validates domain alternatives and reserved local port`() {
        val draft = TunnelDraft(serverAddr = "frp.example.com", proxyType = TunnelProxyType.Http)
        assertFalse(draft.check(9321).ok)
        assertTrue(draft.copy(subdomain = "danmu").check(9321).ok)
        assertFalse(draft.copy(subdomain = "danmu").check(5321).ok)
    }
}
