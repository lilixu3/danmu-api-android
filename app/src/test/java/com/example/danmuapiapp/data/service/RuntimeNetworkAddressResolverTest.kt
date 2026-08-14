package com.example.danmuapiapp.data.service

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeNetworkAddressResolverTest {
    @Test
    fun `IPv6 URL uses brackets and keeps token path`() {
        assertEquals(
            "http://[240e:1234::8]:9321/access-token",
            RuntimeNetworkAddressResolver.buildHttpUrl(
                host = "240e:1234::8",
                port = 9321,
                token = "access-token"
            )
        )
    }

    @Test
    fun `already bracketed IPv6 host is not double bracketed`() {
        assertEquals(
            "http://[fd12:3456::10]:8080",
            RuntimeNetworkAddressResolver.buildHttpUrl("[fd12:3456::10]", 8080, "")
        )
    }

    @Test
    fun `IPv4 URL remains compatible`() {
        assertEquals(
            "http://192.168.1.20:9321/token",
            RuntimeNetworkAddressResolver.buildHttpUrl("192.168.1.20", 9321, "token")
        )
    }

    @Test
    fun `only externally usable IPv6 addresses are displayed`() {
        assertTrue(
            RuntimeNetworkAddressResolver.isUsableIpv6(
                InetAddress.getByName("fd12:3456::10")
            )
        )
        assertFalse(
            RuntimeNetworkAddressResolver.isUsableIpv6(
                InetAddress.getByName("fe80::1")
            )
        )
        assertFalse(
            RuntimeNetworkAddressResolver.isUsableIpv6(
                InetAddress.getByName("::1")
            )
        )
    }

    @Test
    fun `link local IPv4 is not advertised`() {
        assertTrue(
            RuntimeNetworkAddressResolver.isUsableIpv4(
                InetAddress.getByName("192.168.1.20")
            )
        )
        assertFalse(
            RuntimeNetworkAddressResolver.isUsableIpv4(
                InetAddress.getByName("169.254.1.2")
            )
        )
    }
}
