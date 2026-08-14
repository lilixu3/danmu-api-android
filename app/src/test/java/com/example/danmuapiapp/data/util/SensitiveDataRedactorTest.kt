package com.example.danmuapiapp.data.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataRedactorTest {
    @Test
    fun redactsKeyedGithubAndUrlPathSecrets() {
        val raw = "TOKEN=87654321 Authorization: Bearer abcdef ghp_1234567890abcdefghijkl http://127.0.0.1:9321/87654321"
        val redacted = SensitiveDataRedactor.redact(raw)
        assertFalse(redacted.contains("87654321"))
        assertFalse(redacted.contains("ghp_1234567890abcdefghijkl"))
        assertFalse(redacted.contains("Bearer abcdef"))
        assertTrue(redacted.contains("****"))
    }

    @Test
    fun diagnosticModeRedactsIpv4AndIpv6() {
        val redacted = SensitiveDataRedactor.redact(
            "LAN 192.168.1.20 IPv6 [2408:8215:4914::1]",
            redactNetworkAddresses = true
        )
        assertFalse(redacted.contains("192.168.1.20"))
        assertFalse(redacted.contains("2408:8215"))
        assertTrue(redacted.contains("<IP>"))
    }
}

