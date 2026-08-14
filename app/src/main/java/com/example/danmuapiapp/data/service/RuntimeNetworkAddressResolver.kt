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

internal object RuntimeNetworkAddressResolver {
    fun resolve(context: Context): RuntimeNetworkAddresses {
        val activeAddresses = runCatching {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return@runCatching emptyList()
            val activeNetwork = connectivityManager.activeNetwork
                ?: return@runCatching emptyList()
            connectivityManager.getLinkProperties(activeNetwork)
                ?.linkAddresses
                ?.map { it.address }
                .orEmpty()
        }.getOrDefault(emptyList())

        var ipv4 = activeAddresses.firstUsableIpv4()
        var ipv6 = activeAddresses.firstUsableIpv6()
        if (ipv4 != null && ipv6 != null) {
            return RuntimeNetworkAddresses(ipv4 = ipv4, ipv6 = ipv6)
        }

        val fallback = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { interfacePreference(it.name) }
                .flatMap { it.inetAddresses?.toList().orEmpty().asSequence() }
                .toList()
        }.getOrDefault(emptyList())

        if (ipv4 == null) ipv4 = fallback.firstUsableIpv4()
        if (ipv6 == null) ipv6 = fallback.firstUsableIpv6()
        return RuntimeNetworkAddresses(
            ipv4 = ipv4 ?: "0.0.0.0",
            ipv6 = ipv6.orEmpty()
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

    private fun List<InetAddress>.firstUsableIpv4(): String? =
        firstOrNull(::isUsableIpv4)?.hostAddress

    private fun List<InetAddress>.firstUsableIpv6(): String? =
        firstOrNull(::isUsableIpv6)?.hostAddress?.substringBefore('%')

    private fun interfacePreference(rawName: String?): Int {
        val name = rawName.orEmpty().lowercase()
        return when {
            name.startsWith("wlan") || name.startsWith("wifi") -> 4
            name.startsWith("eth") || name.startsWith("en") -> 3
            name.startsWith("rmnet") || name.startsWith("ccmni") -> 2
            else -> 1
        }
    }
}
