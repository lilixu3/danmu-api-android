package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeNetworkAddressResolverTest {

    private fun iface(
        name: String,
        ipv4: String? = null,
        ipv6: String? = null,
        isUp: Boolean = true
    ) = RuntimeIface(name = name, ipv4 = ipv4, ipv6 = ipv6, isUp = isUp)

    @Test
    fun `热点开启时优先热点网关而不是蜂窝 CLAT`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(
                iface("v4-rmnet_data0", "198.18.0.5"),
                iface("rmnet_data0", "10.0.0.8"),
                iface("ap0", "192.168.43.1"),
                iface("lo", "127.0.0.1")
            ),
            activeName = "rmnet_data0"
        )
        assertEquals("192.168.43.1", result.ipv4)
    }

    @Test
    fun `部分机型热点就叫 wlan0 时也能选中`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(
                iface("rmnet_data0", "10.0.0.8"),
                iface("wlan0", "192.168.232.1")
            ),
            activeName = "rmnet_data0"
        )
        assertEquals("192.168.232.1", result.ipv4)
    }

    @Test
    fun `没有热点时保持活跃网络地址`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(iface("wlan0", "192.168.1.5")),
            activeName = "wlan0"
        )
        assertEquals("192.168.1.5", result.ipv4)
    }

    @Test
    fun `纯蜂窝时仍显示蜂窝地址`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(iface("rmnet_data0", "10.1.2.3")),
            activeName = "rmnet_data0"
        )
        assertEquals("10.1.2.3", result.ipv4)
    }

    @Test
    fun `tun 代理网卡被忽略`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(
                iface("tun0", "10.0.0.1"),
                iface("rmnet_data0", "10.1.2.3")
            ),
            activeName = "rmnet_data0"
        )
        assertEquals("10.1.2.3", result.ipv4)
    }

    @Test
    fun `只有 CLAT 地址时不给用户假地址`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(iface("rmnet_data0", "198.18.0.5")),
            activeName = "rmnet_data0"
        )
        assertEquals("0.0.0.0", result.ipv4)
    }

    @Test
    fun `CLAT 地址识别`() {
        assertTrue(RuntimeNetworkAddressResolver.isClatLikeIpv4("198.18.0.5"))
        assertTrue(RuntimeNetworkAddressResolver.isClatLikeIpv4("198.19.255.1"))
        assertTrue(RuntimeNetworkAddressResolver.isClatLikeIpv4("192.0.0.4"))
        assertFalse(RuntimeNetworkAddressResolver.isClatLikeIpv4("192.168.43.1"))
        assertFalse(RuntimeNetworkAddressResolver.isClatLikeIpv4("10.0.0.8"))
    }

    @Test
    fun `down 的网卡不参与选择`() {
        val result = RuntimeNetworkAddressResolver.pick(
            listOf(
                iface("ap0", "192.168.43.1", isUp = false),
                iface("rmnet_data0", "10.1.2.3")
            ),
            activeName = "rmnet_data0"
        )
        assertEquals("10.1.2.3", result.ipv4)
    }
}
