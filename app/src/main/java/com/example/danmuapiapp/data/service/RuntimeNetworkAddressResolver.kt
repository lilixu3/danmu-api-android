package com.example.danmuapiapp.data.service

import android.content.Context
import android.net.ConnectivityManager
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

internal data class RuntimeNetworkAddresses(
    val ipv4: String = "0.0.0.0",
    val ipv6: String = ""
)

/** 一张网卡的候选地址（纯数据，方便单测）。 */
internal data class RuntimeIface(
    val name: String,
    val ipv4: String?,
    val ipv6: String?,
    val isUp: Boolean = true
)

/**
 * 局域网地址解析：手机开热点时优先返回**热点网卡的网关地址**，
 * 而不是蜂窝侧的 464XLAT/CLAT 合成地址或 tun 代理地址。
 */
internal object RuntimeNetworkAddressResolver {

    fun resolve(context: Context): RuntimeNetworkAddresses {
        val ifaces = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { iface ->
                val addresses = iface.inetAddresses?.toList().orEmpty()
                RuntimeIface(
                    name = iface.name.orEmpty(),
                    ipv4 = addresses.firstOrNull(::isUsableIpv4)?.hostAddress,
                    ipv6 = (addresses.firstOrNull(::isUsableIpv6) as? Inet6Address)
                        ?.hostAddress?.substringBefore('%'),
                    isUp = iface.isUp && !iface.isLoopback
                )
            }
        }.getOrDefault(emptyList())

        val activeName = runCatching {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return@runCatching null
            val network = manager.activeNetwork ?: return@runCatching null
            manager.getLinkProperties(network)?.interfaceName
        }.getOrNull()

        return pick(ifaces, activeName)
    }

    /**
     * 选择顺序：
     *   1) 明确的 AP/热点网卡（ap / softap / swlan 开头）；
     *   2) 蜂窝为活跃网络但存在 wlan 网卡（部分机型热点就叫 wlan0）→ 取 wlan；
     *   3) 活跃网络地址（保留原有行为）；
     *   4) 其余按网卡优先级兜底。
     */
    internal fun pick(ifaces: List<RuntimeIface>, activeName: String?): RuntimeNetworkAddresses {
        val candidates = ifaces
            .filter { it.isUp && !isTunnelLike(it.name) }
            .map { iface -> iface.copy(ipv4 = iface.ipv4?.takeUnless(::isClatLikeIpv4)) }
            .filter { it.ipv4 != null || !it.ipv6.isNullOrBlank() }
        if (candidates.isEmpty()) return RuntimeNetworkAddresses()

        val ap = candidates.filter { isApLike(it.name) }.maxByOrNull { interfacePreference(it.name) }
        val wlan = candidates.filter { isWlanLike(it.name) }.maxByOrNull { interfacePreference(it.name) }
        val active = candidates.firstOrNull { it.name == activeName }
        val chosen = ap
            ?: (if (active != null && isCellularLike(active.name) && wlan != null) wlan else active)
            ?: wlan
            ?: candidates.maxByOrNull { interfacePreference(it.name) }
            ?: return RuntimeNetworkAddresses()

        return RuntimeNetworkAddresses(
            ipv4 = chosen.ipv4 ?: "0.0.0.0",
            ipv6 = chosen.ipv6.orEmpty()
        )
    }

    fun buildHttpUrl(host: String, port: Int, token: String): String {
        val normalizedHost = host.trim().removePrefix("[").removeSuffix("]")
        if (normalizedHost.isBlank()) return ""
        val urlHost = if (normalizedHost.contains(':')) "[$normalizedHost]" else normalizedHost
        val tokenPath = RuntimeTokenNormalizer.normalizeInput(token)
            .takeIf { it.isNotEmpty() }
            ?.let { "/$it" }
            .orEmpty()
        return "http://$urlHost:$port$tokenPath"
    }

    internal fun isUsableIpv4(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val host = address.hostAddress.orEmpty()
        return !address.isAnyLocalAddress &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            host != "0.0.0.0"
    }

    internal fun isUsableIpv6(address: InetAddress): Boolean {
        return address is Inet6Address &&
            !address.isAnyLocalAddress &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            !address.isMulticastAddress
    }

    /** 464XLAT/CLAT 合成地址：只在本机内部有效，局域网设备无法访问。 */
    internal fun isClatLikeIpv4(host: String): Boolean {
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        val first = parts[0]
        val second = parts[1]
        return (first == 198 && (second == 18 || second == 19)) || // 198.18.0.0/15
            (first == 192 && second == 0) // 192.0.0.0/24（CLAT 常为 192.0.0.4）
    }

    private fun isTunnelLike(rawName: String): Boolean {
        val name = rawName.lowercase()
        return name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("wg") ||
            name.startsWith("utun") || name.startsWith("ipsec") || name.startsWith("clat") ||
            name.startsWith("v4-") || name.startsWith("p2p")
    }

    private fun isApLike(rawName: String): Boolean {
        val name = rawName.lowercase()
        return name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan")
    }

    private fun isWlanLike(rawName: String): Boolean {
        val name = rawName.lowercase()
        return name.startsWith("wlan") || name.startsWith("wifi")
    }

    private fun isCellularLike(rawName: String): Boolean {
        val name = rawName.lowercase()
        return name.startsWith("rmnet") || name.startsWith("ccmni") ||
            name.startsWith("pdp") || name.startsWith("wwan")
    }

    private fun interfacePreference(rawName: String): Int {
        val name = rawName.lowercase()
        return when {
            isApLike(name) -> 6
            isWlanLike(name) -> 5
            name.startsWith("eth") || name.startsWith("en") -> 4
            isCellularLike(name) -> 2
            else -> 1
        }
    }
}
