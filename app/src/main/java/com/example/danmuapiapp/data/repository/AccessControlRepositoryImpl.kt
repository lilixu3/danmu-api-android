package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.TokenDefaults
import com.example.danmuapiapp.domain.model.DeviceAccessConfig
import com.example.danmuapiapp.domain.model.DeviceAccessDevice
import com.example.danmuapiapp.domain.model.DeviceAccessMode
import com.example.danmuapiapp.domain.model.DeviceAccessSnapshot
import com.example.danmuapiapp.domain.repository.AccessControlRepository
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessControlRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository
) : AccessControlRepository {

    companion object {
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
        private const val ACCESS_ENDPOINT_UNSUPPORTED_MESSAGE =
            "当前运行中的服务未加载设备控制接口，请重启服务后重试"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val IPV4_REGEX = Regex(
            """^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$"""
        )
        private val IPV6_REGEX = Regex(
            """^(([0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|([0-9a-f]{1,4}:){1,7}:|:([0-9a-f]{1,4}:){1,7}|([0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}|([0-9a-f]{1,4}:){1,5}(:[0-9a-f]{1,4}){1,2}|([0-9a-f]{1,4}:){1,4}(:[0-9a-f]{1,4}){1,3}|([0-9a-f]{1,4}:){1,3}(:[0-9a-f]{1,4}){1,4}|([0-9a-f]{1,4}:){1,2}(:[0-9a-f]{1,4}){1,5}|[0-9a-f]{1,4}:((:[0-9a-f]{1,4}){1,6})|:((:[0-9a-f]{1,4}){1,7}|:))$"""
        )
    }

    private data class HistoryDevice(
        val ip: String,
        var totalRequests: Int = 0,
        var lastSeenAtMs: Long = 0L,
        var lastMethod: String = "GET",
        var lastPath: String = ""
    )

    private data class RuntimeAddress(
        val port: Int,
        val token: String,
        val tokenPath: String,
        val adminToken: String,
        val adminTokenPath: String
    )

    private data class RawHttpResult(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String
    )

    override suspend fun fetchSnapshot(): Result<DeviceAccessSnapshot> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val runtime = resolveRuntimeAddress()
                val control = fetchControlSnapshot(runtime)
                val history = runCatching {
                    fetchHistoryDevices(runtime)
                }.getOrDefault(emptyMap())
                val lanNeighbors = runCatching { readLanNeighborIps() }.getOrDefault(emptySet())
                mergeHistory(control, history, lanNeighbors)
            }
        }
    }

    override suspend fun saveConfig(
        config: DeviceAccessConfig,
        clearDevices: Boolean,
        clearRules: Boolean
    ): Result<DeviceAccessSnapshot> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val runtime = resolveRuntimeAddress()
                val payload = JSONObject().apply {
                    put("mode", config.mode.key)
                    put("whitelist", JSONArray(config.whitelist))
                    put("blacklist", JSONArray(config.blacklist))
                    if (clearDevices) put("clearDevices", true)
                    if (clearRules) put("clearRules", true)
                }
                val root = executeControlJson(
                    runtime = runtime,
                    method = "POST",
                    body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
                )
                val control = parseControlSnapshot(root)
                val history = runCatching {
                    fetchHistoryDevices(runtime)
                }.getOrDefault(emptyMap())
                val lanNeighbors = runCatching { readLanNeighborIps() }.getOrDefault(emptySet())
                mergeHistory(control, history, lanNeighbors)
            }
        }
    }

    private fun resolveRuntimeAddress(): RuntimeAddress {
        val prefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
        val port = prefs.getInt("port", DEFAULT_PORT).coerceIn(1, 65535)
        val token = TokenDefaults.resolveTokenFromPrefs(prefs, context)
        val tokenPath = if (token.isBlank()) "" else "/$token"
        val adminToken = adminSessionRepository.currentAdminTokenOrNull().trim()
        val adminTokenPath = if (adminToken.isBlank()) "" else "/$adminToken"
        return RuntimeAddress(
            port = port,
            token = token,
            tokenPath = tokenPath,
            adminToken = adminToken,
            adminTokenPath = adminTokenPath
        )
    }

    private fun controlUrl(port: Int): String = "http://127.0.0.1:$port/__access-control"

    private fun controlUrlWithToken(port: Int, tokenPath: String): String {
        return "http://127.0.0.1:$port$tokenPath/__access-control"
    }

    private fun controlUrls(runtime: RuntimeAddress): List<String> {
        val urls = linkedSetOf<String>()
        urls += controlUrl(runtime.port)
        if (runtime.adminTokenPath.isNotBlank()) {
            urls += controlUrlWithToken(runtime.port, runtime.adminTokenPath)
        }
        if (runtime.tokenPath.isNotBlank()) {
            urls += controlUrlWithToken(runtime.port, runtime.tokenPath)
        }
        return urls.toList()
    }

    private fun recordsUrl(port: Int, tokenPath: String): String {
        return "http://127.0.0.1:$port$tokenPath/api/reqrecords"
    }

    private fun recordTokenPaths(runtime: RuntimeAddress): List<String> {
        val out = linkedSetOf<String>()
        if (runtime.adminTokenPath.isNotBlank()) out += runtime.adminTokenPath
        if (runtime.tokenPath.isNotBlank()) out += runtime.tokenPath
        if (out.isEmpty()) out += ""
        return out.toList()
    }

    private fun fetchControlSnapshot(runtime: RuntimeAddress): DeviceAccessSnapshot {
        val root = executeControlJson(
            runtime = runtime,
            method = "GET",
            body = null
        )
        return parseControlSnapshot(root)
    }

    private fun fetchHistoryDevices(runtime: RuntimeAddress): Map<String, HistoryDevice> {
        var fallbackRows = 0
        var fallbackMap: Map<String, HistoryDevice> = emptyMap()

        recordTokenPaths(runtime).forEach { tokenPath ->
            val request = Request.Builder()
                .url(recordsUrl(runtime.port, tokenPath))
                .get()
                .build()

            val root = runCatching { executeJson(request) }.getOrNull() ?: return@forEach
            val (rows, map) = parseHistoryRecords(root)
            if (map.isNotEmpty()) return map

            if (rows > fallbackRows) {
                fallbackRows = rows
                fallbackMap = map
            }
        }
        return fallbackMap
    }

    private fun parseHistoryRecords(root: JSONObject): Pair<Int, Map<String, HistoryDevice>> {
        val arr = root.optJSONArray("records") ?: JSONArray()
        if (arr.length() <= 0) return 0 to emptyMap()

        val out = linkedMapOf<String, HistoryDevice>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val ip = normalizeIp(obj.optString("clientIp", ""))
            if (ip.isBlank()) continue

            val item = out.getOrPut(ip) { HistoryDevice(ip = ip) }
            item.totalRequests += 1
            val timestampMs = parseTimestamp(obj.optString("timestamp", ""))
            if (timestampMs >= item.lastSeenAtMs) {
                item.lastSeenAtMs = timestampMs
                item.lastMethod = obj.optString("method", "GET").uppercase().ifBlank { "GET" }
                item.lastPath = decodeUtf8(obj.optString("interface", ""))
            }
        }
        return arr.length() to out
    }

    private fun executeControlJson(
        runtime: RuntimeAddress,
        method: String,
        body: okhttp3.RequestBody?
    ): JSONObject {
        val failures = mutableListOf<Pair<Int, String>>()
        val urls = controlUrls(runtime)
        urls.forEach { url ->
            val requestBuilder = Request.Builder().url(url)
            if (runtime.adminToken.isNotBlank()) {
                requestBuilder.header("x-admin-token", runtime.adminToken)
                requestBuilder.header("Authorization", "Bearer ${runtime.adminToken}")
            }
            if (method == "GET") {
                requestBuilder.get()
            } else {
                requestBuilder.method(method, body)
            }
            val result = executeRaw(requestBuilder.build())
            if (result.isSuccessful) {
                if (result.body.isBlank()) return JSONObject()
                return runCatching { JSONObject(result.body) }
                    .getOrElse { error("服务返回了无效 JSON") }
            }
            val message = extractErrorMessage(result.body)
                .ifBlank { "请求失败：HTTP ${result.code}" }
            failures += result.code to message
        }

        if (failures.isEmpty()) {
            error("请求失败：未知错误")
        }
        val allUnsupported = failures.all { (code, _) -> code == 401 || code == 403 || code == 404 }
        if (allUnsupported) {
            error(ACCESS_ENDPOINT_UNSUPPORTED_MESSAGE)
        }
        error(failures.last().second)
    }

    private fun executeRaw(request: Request): RawHttpResult {
        httpClient.newCall(request).execute().use { response ->
            return RawHttpResult(
                code = response.code,
                isSuccessful = response.isSuccessful,
                body = response.body.string()
            )
        }
    }

    private fun extractErrorMessage(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val obj = JSONObject(raw)
            obj.optString("errorMessage")
                .ifBlank { obj.optString("message") }
                .trim()
        }.getOrDefault("")
    }

    private fun executeJson(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                val message = extractErrorMessage(raw)
                error(message.takeIf { it.isNotBlank() } ?: "请求失败：HTTP ${response.code}")
            }
            if (raw.isBlank()) return JSONObject()
            return runCatching { JSONObject(raw) }
                .getOrElse { error("服务返回了无效 JSON") }
        }
    }

    private fun parseControlSnapshot(root: JSONObject): DeviceAccessSnapshot {
        if (root.has("success") && !root.optBoolean("success", false)) {
            val msg = root.optString("errorMessage").trim().ifBlank { "读取访问控制失败" }
            error(msg)
        }

        val configObj = root.optJSONObject("config") ?: JSONObject()
        val mode = DeviceAccessMode.fromKey(configObj.optString("mode", "off"))
        val whitelist = parseIpArray(configObj.optJSONArray("whitelist"))
        val blacklist = parseIpArray(configObj.optJSONArray("blacklist"))
        val config = DeviceAccessConfig(
            mode = mode,
            whitelist = whitelist,
            blacklist = blacklist,
            updatedAtMs = configObj.optLong("updatedAtMs", 0L).coerceAtLeast(0L)
        )

        val devices = parseDeviceList(root.optJSONArray("devices"), config)
        val statsObj = root.optJSONObject("stats")

        val trackedDevices = statsObj?.optInt("trackedDevices", devices.size) ?: devices.size
        val whitelistCount = statsObj?.optInt("whitelistCount", whitelist.size) ?: whitelist.size
        val blacklistCount = statsObj?.optInt("blacklistCount", blacklist.size) ?: blacklist.size
        val totalAllowedRequests = statsObj?.optLong("totalAllowedRequests", 0L) ?: 0L
        val totalBlockedRequests = statsObj?.optLong("totalBlockedRequests", 0L) ?: 0L

        return DeviceAccessSnapshot(
            config = config,
            devices = devices,
            trackedDevices = trackedDevices.coerceAtLeast(devices.size),
            whitelistCount = whitelistCount.coerceAtLeast(config.whitelist.size),
            blacklistCount = blacklistCount.coerceAtLeast(config.blacklist.size),
            totalAllowedRequests = totalAllowedRequests.coerceAtLeast(0L),
            totalBlockedRequests = totalBlockedRequests.coerceAtLeast(0L)
        )
    }

    private fun parseDeviceList(arr: JSONArray?, config: DeviceAccessConfig): List<DeviceAccessDevice> {
        if (arr == null || arr.length() <= 0) return emptyList()

        val out = ArrayList<DeviceAccessDevice>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val ip = normalizeIp(obj.optString("ip", ""))
            if (ip.isBlank()) continue

            val inWhitelist = obj.optBoolean("inWhitelist", config.whitelist.contains(ip))
            val inBlacklist = obj.optBoolean("inBlacklist", config.blacklist.contains(ip))
            val effectiveBlocked = obj.optBoolean(
                "effectiveBlocked",
                calculateEffectiveBlocked(config, ip, inWhitelist, inBlacklist)
            )

            out += DeviceAccessDevice(
                ip = ip,
                firstSeenAtMs = obj.optLong("firstSeenAtMs", 0L).coerceAtLeast(0L),
                lastSeenAtMs = obj.optLong("lastSeenAtMs", 0L).coerceAtLeast(0L),
                totalRequests = obj.optInt("totalRequests", 0).coerceAtLeast(0),
                allowedRequests = obj.optInt("allowedRequests", 0).coerceAtLeast(0),
                blockedRequests = obj.optInt("blockedRequests", 0).coerceAtLeast(0),
                lastMethod = obj.optString("lastMethod", "GET").uppercase().ifBlank { "GET" },
                lastPath = decodeUtf8(obj.optString("lastPath", "")),
                lastReason = obj.optString("lastReason", "").trim(),
                lastUserAgent = obj.optString("lastUserAgent", "").trim(),
                inWhitelist = inWhitelist,
                inBlacklist = inBlacklist,
                effectiveBlocked = effectiveBlocked
            )
        }
        return out.sortedWith(
            compareByDescending<DeviceAccessDevice> { it.lastSeenAtMs }
                .thenBy { it.ip }
        )
    }

    private fun mergeHistory(
        snapshot: DeviceAccessSnapshot,
        historyMap: Map<String, HistoryDevice>,
        lanNeighbors: Set<String>
    ): DeviceAccessSnapshot {
        val config = snapshot.config
        val merged = linkedMapOf<String, DeviceAccessDevice>()
        snapshot.devices.forEach { device ->
            val history = historyMap[device.ip]
            if (history == null) {
                merged[device.ip] = device.copy(
                    inWhitelist = config.whitelist.contains(device.ip),
                    inBlacklist = config.blacklist.contains(device.ip),
                    effectiveBlocked = calculateEffectiveBlocked(
                        config = config,
                        ip = device.ip,
                        inWhitelist = config.whitelist.contains(device.ip),
                        inBlacklist = config.blacklist.contains(device.ip)
                    )
                )
                return@forEach
            }
            val inWhitelist = config.whitelist.contains(device.ip)
            val inBlacklist = config.blacklist.contains(device.ip)
            val historySeen = history.lastSeenAtMs.coerceAtLeast(0L)
            merged[device.ip] = device.copy(
                firstSeenAtMs = listOf(device.firstSeenAtMs, historySeen)
                    .filter { it > 0L }
                    .minOrNull() ?: 0L,
                lastSeenAtMs = maxOf(device.lastSeenAtMs, historySeen),
                totalRequests = maxOf(device.totalRequests, history.totalRequests),
                allowedRequests = maxOf(device.allowedRequests, history.totalRequests),
                lastMethod = if (device.lastSeenAtMs >= historySeen) {
                    device.lastMethod
                } else {
                    history.lastMethod.ifBlank { device.lastMethod }
                },
                lastPath = if (device.lastSeenAtMs >= historySeen) {
                    device.lastPath
                } else {
                    history.lastPath.ifBlank { device.lastPath }
                },
                inWhitelist = inWhitelist,
                inBlacklist = inBlacklist,
                effectiveBlocked = calculateEffectiveBlocked(
                    config = config,
                    ip = device.ip,
                    inWhitelist = inWhitelist,
                    inBlacklist = inBlacklist
                )
            )
        }

        historyMap.forEach { (ip, history) ->
            if (merged.containsKey(ip)) return@forEach
            val inWhitelist = config.whitelist.contains(ip)
            val inBlacklist = config.blacklist.contains(ip)
            val seenAt = history.lastSeenAtMs.coerceAtLeast(0L)
            merged[ip] = DeviceAccessDevice(
                ip = ip,
                firstSeenAtMs = seenAt,
                lastSeenAtMs = seenAt,
                totalRequests = history.totalRequests.coerceAtLeast(0),
                allowedRequests = history.totalRequests.coerceAtLeast(0),
                blockedRequests = 0,
                lastMethod = history.lastMethod.ifBlank { "GET" },
                lastPath = history.lastPath,
                inWhitelist = inWhitelist,
                inBlacklist = inBlacklist,
                effectiveBlocked = calculateEffectiveBlocked(
                    config = config,
                    ip = ip,
                    inWhitelist = inWhitelist,
                    inBlacklist = inBlacklist
                )
            )
        }

        config.whitelist.forEach { ip ->
            if (merged.containsKey(ip)) return@forEach
            merged[ip] = DeviceAccessDevice(
                ip = ip,
                firstSeenAtMs = 0L,
                lastSeenAtMs = 0L,
                totalRequests = 0,
                allowedRequests = 0,
                blockedRequests = 0,
                lastMethod = "GET",
                lastPath = "白名单规则",
                inWhitelist = true,
                inBlacklist = false,
                effectiveBlocked = calculateEffectiveBlocked(
                    config = config,
                    ip = ip,
                    inWhitelist = true,
                    inBlacklist = false
                )
            )
        }

        config.blacklist.forEach { ip ->
            if (merged.containsKey(ip)) return@forEach
            merged[ip] = DeviceAccessDevice(
                ip = ip,
                firstSeenAtMs = 0L,
                lastSeenAtMs = 0L,
                totalRequests = 0,
                allowedRequests = 0,
                blockedRequests = 0,
                lastMethod = "GET",
                lastPath = "黑名单规则",
                inWhitelist = false,
                inBlacklist = true,
                effectiveBlocked = calculateEffectiveBlocked(
                    config = config,
                    ip = ip,
                    inWhitelist = false,
                    inBlacklist = true
                )
            )
        }

        lanNeighbors.forEach { ip ->
            if (merged.containsKey(ip)) return@forEach
            val inWhitelist = config.whitelist.contains(ip)
            val inBlacklist = config.blacklist.contains(ip)
            merged[ip] = DeviceAccessDevice(
                ip = ip,
                firstSeenAtMs = 0L,
                lastSeenAtMs = 0L,
                totalRequests = 0,
                allowedRequests = 0,
                blockedRequests = 0,
                lastMethod = "GET",
                lastPath = "局域网邻居设备",
                inWhitelist = inWhitelist,
                inBlacklist = inBlacklist,
                effectiveBlocked = calculateEffectiveBlocked(
                    config = config,
                    ip = ip,
                    inWhitelist = inWhitelist,
                    inBlacklist = inBlacklist
                )
            )
        }

        val mergedDevices = merged.values.sortedWith(
            compareByDescending<DeviceAccessDevice> { it.lastSeenAtMs }
                .thenBy { it.ip }
        )
        return snapshot.copy(
            devices = mergedDevices,
            trackedDevices = maxOf(snapshot.trackedDevices, mergedDevices.size),
            whitelistCount = maxOf(snapshot.whitelistCount, config.whitelist.size),
            blacklistCount = maxOf(snapshot.blacklistCount, config.blacklist.size)
        )
    }

    private fun readLanNeighborIps(): Set<String> {
        val arpFile = java.io.File("/proc/net/arp")
        if (!arpFile.exists() || !arpFile.isFile) return emptySet()
        val out = linkedSetOf<String>()
        val lines = runCatching { arpFile.readLines(Charsets.UTF_8) }.getOrElse { return emptySet() }
        lines.drop(1).forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val parts = line.split(Regex("\\s+"))
            if (parts.isEmpty()) return@forEach
            val ip = normalizeIp(parts[0])
            if (ip.isNotBlank() && !isLoopback(ip)) {
                out += ip
            }
        }
        return out
    }

    private fun parseIpArray(arr: JSONArray?): List<String> {
        if (arr == null || arr.length() <= 0) return emptyList()
        val out = linkedSetOf<String>()
        for (i in 0 until arr.length()) {
            val ip = normalizeIp(arr.optString(i, ""))
            if (ip.isNotBlank()) out += ip
        }
        return out.toList().sorted()
    }

    private fun normalizeIp(raw: String?): String {
        var value = raw?.trim()?.lowercase().orEmpty()
        if (value.isBlank()) return ""
        if (value.contains(',')) {
            value = value.substringBefore(',').trim()
        }
        if (value.startsWith('[') && value.contains(']')) {
            value = value.substringAfter('[').substringBefore(']')
        }
        if (value.contains('%')) {
            value = value.substringBefore('%').trim()
        }
        if (value.startsWith("::ffff:")) {
            val mapped = value.removePrefix("::ffff:")
            if (isIp(mapped)) return mapped
        }
        if (Regex("""^\d{1,3}(\.\d{1,3}){3}:\d+$""").matches(value)) {
            value = value.substringBefore(':')
        }
        return if (isIp(value)) value else ""
    }

    private fun isIp(value: String): Boolean {
        if (value.isBlank()) return false
        return IPV4_REGEX.matches(value) || IPV6_REGEX.matches(value)
    }

    private fun calculateEffectiveBlocked(
        config: DeviceAccessConfig,
        ip: String,
        inWhitelist: Boolean,
        inBlacklist: Boolean
    ): Boolean {
        if (isLoopback(ip)) return false
        return when (config.mode) {
            DeviceAccessMode.Off -> false
            DeviceAccessMode.Blacklist -> inBlacklist
            DeviceAccessMode.Whitelist -> !inWhitelist
        }
    }

    private fun isLoopback(ip: String): Boolean {
        if (ip.isBlank()) return false
        return ip == "::1" || ip == "0:0:0:0:0:0:0:1" || ip.startsWith("127.")
    }

    private fun parseTimestamp(raw: String): Long {
        if (raw.isBlank()) return 0L
        return runCatching {
            Instant.parse(raw).toEpochMilli()
        }.getOrElse {
            runCatching {
                val local = LocalDateTime.parse(
                    raw,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
                local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
    }

    private fun decodeUtf8(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrDefault(raw)
    }
}
