package com.example.danmuapiapp.ui.screen.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit

object PushLanScanner {

    private val IPV4_REGEX = Regex(
        """^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$"""
    )

    data class ScanDevice(
        val ip: String,
        val mac: String? = null,
        val ifName: String? = null,
        val port9978Open: Boolean = false,
        val latencyMs: Int? = null,
        val verifiedPushApi: Boolean = false
    ) {
        val targetTemplate: String
            get() = "http://$ip:9978/action?do=refresh&type=danmaku&path="
    }

    private data class ArpEntry(
        val ip: String,
        val mac: String,
        val ifName: String
    )

    private data class PortProbe(
        val portOpen: Boolean,
        val latencyMs: Int? = null,
        val verifiedApi: Boolean = false
    )

    suspend fun scanLan(
        selfIp: String,
        httpClient: OkHttpClient
    ): List<ScanDevice> = coroutineScope {
        val prefix = selfIp.substringBeforeLast('.', "")
        if (prefix.isBlank()) return@coroutineScope emptyList()

        arpSweep(prefix = prefix, selfIp = selfIp)
        delay(620L)

        var arp = readArpTable(prefix).distinctBy { it.ip }
        val gateway = getDefaultGatewayIpv4()
        if (!gateway.isNullOrBlank() && gateway.startsWith("$prefix.") && gateway != selfIp) {
            if (arp.none { it.ip == gateway }) {
                arp = arp + ArpEntry(ip = gateway, mac = "", ifName = "gateway")
            }
        }

        if (arp.isEmpty()) {
            val discovered = tcpDiscoverActiveHosts(prefix = prefix, selfIp = selfIp)
            arp = discovered.map { ArpEntry(ip = it, mac = "", ifName = "") }
        }

        if (arp.none { it.ip == selfIp }) {
            arp = arp + ArpEntry(ip = selfIp, mac = "", ifName = "self")
        }

        if (arp.isEmpty()) {
            arp = listOf(ArpEntry(ip = selfIp, mac = "", ifName = "self"))
        }

        val sem = Semaphore(24)
        val jobs = arp.map { entry ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val probe = probe9978(entry.ip, httpClient)
                    ScanDevice(
                        ip = entry.ip,
                        mac = entry.mac.takeIf { it.isNotBlank() },
                        ifName = entry.ifName.takeIf { it.isNotBlank() },
                        port9978Open = probe.portOpen,
                        latencyMs = probe.latencyMs,
                        verifiedPushApi = probe.verifiedApi
                    )
                }
            }
        }

        val devices = jobs.awaitAll()
        val loopProbe = runCatching { probe9978("127.0.0.1", httpClient) }.getOrNull()
        val withLoopback = if (loopProbe != null && loopProbe.portOpen) {
            devices + ScanDevice(
                ip = "127.0.0.1",
                ifName = "loopback",
                port9978Open = true,
                latencyMs = loopProbe.latencyMs,
                verifiedPushApi = loopProbe.verifiedApi
            )
        } else {
            devices
        }

        withLoopback
            .distinctBy { it.ip }
            .sortedWith(
                compareByDescending<ScanDevice> { it.verifiedPushApi }
                    .thenByDescending { it.port9978Open }
                    .thenBy { it.latencyMs ?: Int.MAX_VALUE }
                    .thenBy { it.ip }
            )
    }

    fun resolveSelfLanIpv4(runtimeLanUrl: String): String? {
        val fromRuntime = runCatching {
            val uri = URI(runtimeLanUrl.trim())
            uri.host?.trim().orEmpty()
        }.getOrNull().orEmpty()

        if (fromRuntime.isNotBlank() && IPV4_REGEX.matches(fromRuntime) && !fromRuntime.startsWith("127.")) {
            return fromRuntime
        }
        return findLocalLanIpv4()
    }

    private fun findLocalLanIpv4(): String? {
        val interfaces = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        }.getOrDefault(emptyList())
        var fallback: String? = null

        interfaces.forEach { intf ->
            val valid = runCatching { intf.isUp && !intf.isLoopback }.getOrDefault(false)
            if (!valid) return@forEach

            val ifaceName = (intf.name ?: "").lowercase(Locale.ROOT)
            val preferredIface = ifaceName.startsWith("wlan") ||
                ifaceName.startsWith("eth") ||
                ifaceName.startsWith("en")

            val addresses = runCatching { Collections.list(intf.inetAddresses) }.getOrDefault(emptyList())
            addresses.forEach { addr ->
                val ipv4 = addr as? Inet4Address ?: return@forEach
                if (ipv4.isLoopbackAddress || !ipv4.isSiteLocalAddress) return@forEach
                val host = ipv4.hostAddress?.trim().orEmpty()
                if (!IPV4_REGEX.matches(host)) return@forEach
                if (preferredIface) return host
                if (fallback == null) fallback = host
            }
        }
        return fallback
    }

    private suspend fun tcpDiscoverActiveHosts(prefix: String, selfIp: String): List<String> = coroutineScope {
        val sem = Semaphore(64)
        val jobs = (1..254).mapNotNull { last ->
            val ip = "$prefix.$last"
            if (ip == selfIp) return@mapNotNull null
            async(Dispatchers.IO) {
                sem.withPermit {
                    if (isHostReachableByTcp(ip, intArrayOf(9978, 80, 443, 22))) ip else null
                }
            }
        }
        jobs.awaitAll().filterNotNull().distinct().sorted()
    }

    private fun isHostReachableByTcp(ip: String, ports: IntArray): Boolean {
        ports.forEach { port ->
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 260)
                }
                return true
            } catch (e: java.net.ConnectException) {
                val msg = (e.message ?: "").lowercase(Locale.ROOT)
                if (msg.contains("refused") || msg.contains("econnrefused")) {
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private fun probe9978(ip: String, httpClient: OkHttpClient): PortProbe {
        val latency = runCatching {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 9978), 520)
            }
            (System.currentTimeMillis() - start).toInt().coerceAtLeast(0)
        }.getOrElse { return PortProbe(portOpen = false) }

        val fastClient = httpClient.newBuilder()
            .connectTimeout(450, TimeUnit.MILLISECONDS)
            .readTimeout(650, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .build()
        val verified = runCatching {
            val request = Request.Builder()
                .url("http://$ip:9978/cache?do=get&key=danmu_api_probe")
                .get()
                .header("Accept", "*/*")
                .build()
            fastClient.newCall(request).execute().use { response ->
                response.code in 200..299
            }
        }.getOrDefault(false)
        return PortProbe(portOpen = true, latencyMs = latency, verifiedApi = verified)
    }

    private suspend fun arpSweep(prefix: String, selfIp: String) {
        withContext(Dispatchers.IO) {
            val payload = byteArrayOf(0)
            val socket = runCatching { DatagramSocket() }.getOrNull() ?: return@withContext
            socket.broadcast = false
            for (last in 1..254) {
                val ip = "$prefix.$last"
                if (ip == selfIp) continue
                runCatching {
                    val addr = InetAddress.getByName(ip)
                    val packet = DatagramPacket(payload, payload.size, addr, 9)
                    socket.send(packet)
                }
                if (last % 32 == 0) {
                    delay(6)
                }
            }
            runCatching { socket.close() }
        }
    }

    private fun readArpTable(prefix: String): List<ArpEntry> {
        return runCatching {
            val file = File("/proc/net/arp")
            if (!file.exists() || !file.canRead()) return@runCatching emptyList()

            val out = mutableListOf<ArpEntry>()
            file.forEachLine { raw ->
                val line = raw.trim()
                if (line.isBlank() || line.startsWith("IP")) return@forEachLine
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 6) return@forEachLine

                val ip = parts[0]
                val flags = parts[2]
                val mac = parts[3]
                val iface = parts[5]
                if (!ip.startsWith("$prefix.")) return@forEachLine
                if (flags == "0x0") return@forEachLine
                if (mac == "00:00:00:00:00:00") return@forEachLine
                if (!mac.contains(':')) return@forEachLine
                out += ArpEntry(ip = ip, mac = mac, ifName = iface)
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun getDefaultGatewayIpv4(): String? {
        return runCatching {
            val file = File("/proc/net/route")
            if (!file.exists() || !file.canRead()) return@runCatching null
            val lines = file.readLines()
            if (lines.size <= 1) return@runCatching null

            val candidates = lines.drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) return@mapNotNull null
                val iface = parts[0]
                val dest = parts[1]
                val gatewayHex = parts[2]
                if (dest != "00000000") return@mapNotNull null
                val ip = hexLeToIpv4(gatewayHex) ?: return@mapNotNull null
                iface to ip
            }
            val preferred = candidates.firstOrNull { (iface, _) ->
                val name = iface.lowercase(Locale.ROOT)
                name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")
            }
            (preferred ?: candidates.firstOrNull())?.second
        }.getOrNull()
    }

    private fun hexLeToIpv4(hex: String): String? {
        if (hex.length != 8) return null
        return runCatching {
            val b1 = hex.substring(6, 8).toInt(16)
            val b2 = hex.substring(4, 6).toInt(16)
            val b3 = hex.substring(2, 4).toInt(16)
            val b4 = hex.substring(0, 2).toInt(16)
            "$b1.$b2.$b3.$b4"
        }.getOrNull()
    }
}
