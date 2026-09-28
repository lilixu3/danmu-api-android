package com.example.danmuapiapp.data.tunnel

import org.json.JSONArray
import org.json.JSONObject

/**
 * 内网穿透（frpc）纯逻辑：配置模型、TOML 生成、粘贴配置解析/校验、
 * DNS 补全、公网地址推导与运行状态判定。
 *
 * 与 Flutter 版 tunnel_logic.dart 行为一致，便于两端配置互通。
 */

const val FRPC_KERNEL_VERSION = "0.71.0"
const val DEFAULT_DNS_SERVER = "223.5.5.5"
const val DEFAULT_API_TOKEN = "87654321"

enum class TunnelMode(val key: String, val label: String) {
    Form("form", "表单"),
    Paste("paste", "粘贴配置");

    companion object {
        fun fromKey(raw: String?): TunnelMode =
            if (raw?.trim()?.lowercase() == "paste") Paste else Form
    }
}

enum class TunnelProxyType(val key: String, val label: String) {
    Tcp("tcp", "TCP 端口"),
    Http("http", "HTTP 域名");

    companion object {
        fun fromKey(raw: String?): TunnelProxyType =
            if (raw?.trim()?.lowercase() == "http") Http else Tcp
    }
}

data class TunnelFormSettings(
    val serverAddr: String = "",
    val serverPort: Int = 7000,
    val authToken: String = "",
    val proxyType: TunnelProxyType = TunnelProxyType.Tcp,
    val remotePort: Int = 0,
    val customDomains: String = "",
    val subdomain: String = "",
    val proxyName: String = "danmu-api",
    val tlsEnabled: Boolean = true,
    val dnsServer: String = DEFAULT_DNS_SERVER,
    val autoFillDefaults: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("serverAddr", serverAddr)
        .put("serverPort", serverPort)
        .put("authToken", authToken)
        .put("proxyType", proxyType.key)
        .put("remotePort", remotePort)
        .put("customDomains", customDomains)
        .put("subdomain", subdomain)
        .put("proxyName", proxyName)
        .put("tlsEnabled", tlsEnabled)
        .put("dnsServer", dnsServer)
        .put("autoFillDefaults", autoFillDefaults)

    companion object {
        fun fromJson(raw: JSONObject?): TunnelFormSettings {
            if (raw == null) return TunnelFormSettings()
            return TunnelFormSettings(
                serverAddr = raw.optString("serverAddr", ""),
                serverPort = raw.optInt("serverPort", 7000),
                authToken = raw.optString("authToken", ""),
                proxyType = TunnelProxyType.fromKey(raw.optString("proxyType", "")),
                remotePort = raw.optInt("remotePort", 0),
                customDomains = raw.optString("customDomains", ""),
                subdomain = raw.optString("subdomain", ""),
                proxyName = raw.optString("proxyName", "danmu-api"),
                tlsEnabled = raw.optBoolean("tlsEnabled", true),
                dnsServer = raw.optString("dnsServer", DEFAULT_DNS_SERVER),
                autoFillDefaults = raw.optBoolean("autoFillDefaults", true)
            )
        }
    }
}

data class TunnelSettings(
    val enabled: Boolean = false,
    val autoStart: Boolean = false,
    val mode: TunnelMode = TunnelMode.Form,
    val publicAddress: String = "",
    val form: TunnelFormSettings = TunnelFormSettings(),
    val configText: String = "",
    val riskAcknowledged: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("autoStart", autoStart)
        .put("mode", mode.key)
        .put("publicAddress", publicAddress)
        .put("form", form.toJson())
        .put("configText", configText)
        .put("riskAcknowledged", riskAcknowledged)

    fun encode(): String = toJson().toString()

    companion object {
        fun decode(raw: String?): TunnelSettings {
            if (raw.isNullOrBlank()) return TunnelSettings()
            return try {
                val json = JSONObject(raw)
                TunnelSettings(
                    enabled = json.optBoolean("enabled", false),
                    autoStart = json.optBoolean("autoStart", false),
                    mode = TunnelMode.fromKey(json.optString("mode", "")),
                    publicAddress = json.optString("publicAddress", ""),
                    form = TunnelFormSettings.fromJson(json.optJSONObject("form")),
                    configText = json.optString("configText", ""),
                    riskAcknowledged = json.optBoolean("riskAcknowledged", false)
                )
            } catch (_: Exception) {
                TunnelSettings()
            }
        }
    }
}

data class FrpcProxySummary(
    val name: String,
    val type: String,
    val localIp: String,
    val localPort: Int?,
    val remotePort: Int?,
    val customDomains: List<String>
)

data class FrpcConfigSummary(
    val format: String,
    val serverAddr: String,
    val serverPort: Int?,
    val hasToken: Boolean,
    val hasDnsServer: Boolean,
    val hasLoginFailExit: Boolean,
    val proxies: List<FrpcProxySummary>
)

data class TunnelCheck(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val ok: Boolean get() = errors.isEmpty()
}

enum class TunnelLinkState { Unknown, Connecting, Connected, Error }

data class ServerInput(val host: String, val port: Int?)

// ---------------------------------------------------------------- 生成配置

private fun tomlString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun splitDomains(raw: String): List<String> =
    raw.split(Regex("[,，\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

fun parseServerInput(raw: String): ServerInput {
    val value = raw.trim()
    if (value.isEmpty()) return ServerInput("", null)
    val match = Regex("^\\[?([^\\]]+?)\\]?:(\\d{1,5})$").find(value)
    if (match != null) {
        return ServerInput(match.groupValues[1].trim(), match.groupValues[2].toIntOrNull())
    }
    return ServerInput(value, null)
}

/** 表单模式 → frpc.toml（官方 0.71 配置格式）。 */
fun buildFrpcToml(form: TunnelFormSettings, targetPort: Int): String {
    val name = form.proxyName.trim().ifEmpty { "danmu-api" }
    val builder = StringBuilder()
        .appendLine("serverAddr = ${tomlString(form.serverAddr.trim())}")
        .appendLine("serverPort = ${form.serverPort}")
        .appendLine("loginFailExit = false")
    if (form.authToken.trim().isNotEmpty()) {
        builder.appendLine("auth.token = ${tomlString(form.authToken.trim())}")
    }
    if (form.dnsServer.trim().isNotEmpty()) {
        builder.appendLine("dnsServer = ${tomlString(form.dnsServer.trim())}")
    }
    builder
        .appendLine("transport.tls.enable = ${form.tlsEnabled}")
        .appendLine()
        .appendLine("[[proxies]]")
        .appendLine("name = ${tomlString(name)}")
        .appendLine("type = ${tomlString(form.proxyType.key)}")
        .appendLine("localIP = \"127.0.0.1\"")
        .appendLine("localPort = $targetPort")
    if (form.proxyType == TunnelProxyType.Tcp) {
        builder.appendLine("remotePort = ${form.remotePort}")
    } else {
        val domains = splitDomains(form.customDomains)
        if (domains.isNotEmpty()) {
            builder.appendLine("customDomains = [${domains.joinToString(", ") { tomlString(it) }}]")
        }
        if (form.subdomain.trim().isNotEmpty()) {
            builder.appendLine("subdomain = ${tomlString(form.subdomain.trim())}")
        }
    }
    return builder.toString()
}

// ---------------------------------------------------------------- 解析配置

private fun cleanValue(raw: String): String {
    var value = raw.trim()
    val comment = value.indexOf(" #")
    if (comment > 0) value = value.substring(0, comment).trim()
    value = value.removeSurrounding("\"").removeSurrounding("'").trim()
    if (value.startsWith("[") && value.endsWith("]")) {
        value = value.substring(1, value.length - 1)
    }
    return value.trim()
}

private fun detectFormat(text: String): String {
    val trimmed = text.trimStart()
    if (trimmed.startsWith("{")) return "json"
    if (Regex("^\\s*\\[common\\]", RegexOption.MULTILINE).containsMatchIn(text)) return "ini"
    if (Regex("^\\s*serverAddr\\s*:", RegexOption.MULTILINE).containsMatchIn(text)) return "yaml"
    if (Regex("^\\s*serverAddr\\s*=", RegexOption.MULTILINE).containsMatchIn(text)) return "toml"
    if (Regex("^\\s*server_addr\\s*=", RegexOption.MULTILINE).containsMatchIn(text)) return "ini"
    return "unknown"
}

private fun collectValues(text: String, keys: List<String>): List<String> {
    val pattern = Regex(
        "^\\s*(?:${keys.joinToString("|")})\\s*[:=]\\s*(.+?)\\s*$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )
    return pattern.findAll(text).map { cleanValue(it.groupValues[1]) }.toList()
}

private fun collectNumbers(text: String, keys: List<String>): List<Int> =
    collectValues(text, keys).mapNotNull { it.replace(Regex("[^0-9]"), "").toIntOrNull() }

private fun pick(text: String, tomlKey: String, iniKey: String): String {
    val pattern = Regex(
        "^\\s*(?:$tomlKey|$iniKey)\\s*[:=]\\s*(.+?)\\s*$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )
    return cleanValue(pattern.find(text)?.groupValues?.get(1) ?: "")
}

/** 宽松解析：TOML / INI / YAML 按行键值扫描，JSON 走 JSONObject。 */
fun parseFrpcConfig(text: String): FrpcConfigSummary {
    val format = detectFormat(text)
    if (format == "json") return parseFrpcJson(text)

    val localPorts = collectNumbers(text, listOf("localPort", "local_port"))
    val remotePorts = collectNumbers(text, listOf("remotePort", "remote_port"))
    val localIps = collectValues(text, listOf("localIP", "local_ip"))
    val names = collectValues(text, listOf("name"))
    val types = collectValues(text, listOf("type"))
    val domains = collectValues(text, listOf("customDomains", "custom_domains", "subdomain"))

    val proxies = localPorts.mapIndexed { index, port ->
        FrpcProxySummary(
            name = names.getOrElse(index) { "" },
            type = types.getOrElse(index) { "tcp" },
            localIp = localIps.getOrElse(index) { "" },
            localPort = port,
            remotePort = remotePorts.getOrNull(index),
            customDomains = domains
        )
    }.toMutableList()
    if (localPorts.isEmpty() && domains.isNotEmpty()) {
        proxies += FrpcProxySummary(
            name = names.firstOrNull() ?: "",
            type = "http",
            localIp = "",
            localPort = null,
            remotePort = null,
            customDomains = domains
        )
    }

    return FrpcConfigSummary(
        format = format,
        serverAddr = pick(text, "serverAddr", "server_addr"),
        serverPort = pick(text, "serverPort", "server_port").toIntOrNull(),
        hasToken = pick(text, "auth\\.token", "auth_token").isNotEmpty() ||
            pick(text, "token", "token").isNotEmpty(),
        hasDnsServer = pick(text, "dnsServer", "dns_server").isNotEmpty(),
        hasLoginFailExit = pick(text, "loginFailExit", "login_fail_exit").isNotEmpty(),
        proxies = proxies
    )
}

private fun parseFrpcJson(text: String): FrpcConfigSummary {
    return try {
        val map = JSONObject(text)
        val auth = map.optJSONObject("auth")
        val proxies = mutableListOf<FrpcProxySummary>()
        val array = map.optJSONArray("proxies")
        if (array != null) {
            for (index in 0 until array.length()) {
                val proxy = array.optJSONObject(index) ?: continue
                val domains = mutableListOf<String>()
                val domainArray = proxy.optJSONArray("customDomains")
                if (domainArray != null) {
                    for (i in 0 until domainArray.length()) {
                        domains += domainArray.optString(i)
                    }
                }
                proxies += FrpcProxySummary(
                    name = proxy.optString("name", ""),
                    type = proxy.optString("type", "tcp"),
                    localIp = proxy.optString("localIP", proxy.optString("localIp", "")),
                    localPort = proxy.optInt("localPort", -1).takeIf { it >= 0 },
                    remotePort = proxy.optInt("remotePort", -1).takeIf { it >= 0 },
                    customDomains = domains
                )
            }
        }
        FrpcConfigSummary(
            format = "json",
            serverAddr = map.optString("serverAddr", map.optString("server_addr", "")),
            serverPort = map.optInt("serverPort", -1).takeIf { it >= 0 }
                ?: map.optString("server_port", "").toIntOrNull(),
            hasToken = (auth?.optString("token", "")?.isNotEmpty() == true) ||
                map.optString("token", "").isNotEmpty(),
            hasDnsServer = map.optString("dnsServer", map.optString("dns_server", "")).isNotEmpty(),
            hasLoginFailExit = map.has("loginFailExit") || map.has("login_fail_exit"),
            proxies = proxies
        )
    } catch (_: Exception) {
        FrpcConfigSummary("json", "", null, false, false, false, emptyList())
    }
}

// ---------------------------------------------------------------- 校验

fun isLoopbackHost(host: String): Boolean {
    val value = host.trim().lowercase().replace("[", "").replace("]", "")
    return value.isEmpty() || value == "127.0.0.1" || value == "localhost" || value == "::1"
}

fun isUnsafeApiToken(token: String): Boolean {
    val value = token.trim()
    return value.isEmpty() || value == DEFAULT_API_TOKEN
}

fun validateForm(form: TunnelFormSettings, targetPort: Int): List<String> {
    val errors = mutableListOf<String>()
    val server = parseServerInput(form.serverAddr)
    if (server.host.isEmpty()) errors += "请填写 frps 服务器地址"
    if (server.host.isNotEmpty() && !Regex("^[A-Za-z0-9._:-]+$").matches(server.host)) {
        errors += "服务器地址格式不正确（只填域名或 IP，不要带 http://）"
    }
    if (form.serverPort !in 1..65535) errors += "frps 端口需在 1-65535"
    if (targetPort !in 1..65535) errors += "本机 API 端口不合法"
    if (targetPort == 5321) errors += "禁止把正向代理端口 5321 暴露到公网"
    if (form.proxyType == TunnelProxyType.Tcp) {
        if (form.remotePort !in 1..65535) errors += "远程端口需在 1-65535"
    } else if (splitDomains(form.customDomains).isEmpty() && form.subdomain.trim().isEmpty()) {
        errors += "HTTP 隧道需要填写域名或子域名"
    }
    if (form.dnsServer.trim().isNotEmpty() && !Regex("^[0-9a-fA-F:.]+$").matches(form.dnsServer.trim())) {
        errors += "DNS 服务器格式不正确"
    }
    return errors
}

fun checkPastedConfig(text: String, targetPort: Int): TunnelCheck {
    if (text.isBlank()) return TunnelCheck(errors = listOf("请粘贴 frpc 配置"))
    val summary = parseFrpcConfig(text)
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    if (summary.serverAddr.isEmpty()) errors += "配置里没有找到 serverAddr"
    if (targetPort == 5321) errors += "禁止把正向代理端口 5321 暴露到公网"

    summary.proxies.forEach { proxy ->
        val label = proxy.name.ifEmpty { "未命名" }
        if (proxy.localPort != null && proxy.localPort != targetPort) {
            errors += "proxy \"$label\" 的 localPort=${proxy.localPort}，只能指向本机 API 端口 $targetPort"
        }
        if (!isLoopbackHost(proxy.localIp)) {
            errors += "proxy \"$label\" 的 localIP=${proxy.localIp}，只能转发本机回环地址"
        }
    }
    if (summary.proxies.isEmpty()) warnings += "没有识别到 proxies 条目，确认配置完整后再保存"
    if (!summary.hasDnsServer) warnings += "配置缺少 dnsServer，安卓上可能无法解析服务器域名"
    if (!summary.hasLoginFailExit) warnings += "配置缺少 loginFailExit=false，开机网络未就绪时 frpc 可能直接退出"
    return TunnelCheck(errors, warnings)
}

// ---------------------------------------------------------------- 补全与端口跟随

/** 在粘贴配置里补上缺失的 dnsServer / loginFailExit（幂等）。 */
fun injectFrpcDefaults(
    text: String,
    dnsServer: String = DEFAULT_DNS_SERVER,
    loginFailExit: Boolean = false
): String {
    if (text.isBlank()) return text
    val summary = parseFrpcConfig(text)
    val format = summary.format

    if (format == "json") {
        return try {
            val map = JSONObject(text)
            var changed = false
            if (!summary.hasDnsServer && dnsServer.trim().isNotEmpty()) {
                map.put("dnsServer", dnsServer.trim()); changed = true
            }
            if (!summary.hasLoginFailExit) {
                map.put("loginFailExit", loginFailExit); changed = true
            }
            if (changed) map.toString(2) else text
        } catch (_: Exception) {
            text
        }
    }

    val additions = mutableListOf<String>()
    if (!summary.hasDnsServer && dnsServer.trim().isNotEmpty()) {
        additions += when (format) {
            "ini" -> "dns_server = ${dnsServer.trim()}"
            "yaml" -> "dnsServer: ${dnsServer.trim()}"
            else -> "dnsServer = ${tomlString(dnsServer.trim())}"
        }
    }
    if (!summary.hasLoginFailExit) {
        additions += when (format) {
            "ini" -> "login_fail_exit = $loginFailExit"
            "yaml" -> "loginFailExit: $loginFailExit"
            else -> "loginFailExit = $loginFailExit"
        }
    }
    if (additions.isEmpty()) return text

    if (format == "ini") {
        val match = Regex("^\\s*\\[common\\]\\s*$", RegexOption.MULTILINE).find(text)
            ?: return text
        val insertAt = match.range.last + 1
        return text.substring(0, insertAt) + "\n" + additions.joinToString("\n") + text.substring(insertAt)
    }

    // TOML/YAML 的顶层键必须写在任何表/嵌套结构之前。
    val firstTable = Regex("^\\s*\\[", RegexOption.MULTILINE).find(text)
    val cut = firstTable?.range?.first ?: text.length
    return text.substring(0, cut) + additions.joinToString("\n") + "\n" + text.substring(cut)
}

/** 把粘贴配置里所有 localPort 改成 targetPort（用户改了 API 端口时自动跟随）。 */
fun syncPastedLocalPort(text: String, targetPort: Int): String {
    if (text.isBlank() || targetPort <= 0) return text
    if (parseFrpcConfig(text).format == "json") {
        return try {
            val map = JSONObject(text)
            val array = map.optJSONArray("proxies")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val proxy = array.optJSONObject(index) ?: continue
                    if (proxy.has("localPort")) proxy.put("localPort", targetPort)
                    else if (proxy.has("local_port")) proxy.put("local_port", targetPort)
                }
            }
            map.toString(2)
        } catch (_: Exception) {
            text
        }
    }
    return Regex(
        "^(\\s*(?:localPort|local_port)\\s*[:=]\\s*)\\d+",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    ).replace(text) { match -> "${match.groupValues[1]}$targetPort" }
}

// ---------------------------------------------------------------- 展示与状态

/** 推导公网入口（不含 token 路径）。 */
fun derivePublicAddress(settings: TunnelSettings): String {
    val override = settings.publicAddress.trim()
    if (override.isNotEmpty()) return override
    if (settings.mode == TunnelMode.Form) {
        val form = settings.form
        val host = parseServerInput(form.serverAddr).host
        if (host.isEmpty()) return ""
        if (form.proxyType == TunnelProxyType.Tcp) {
            return if (form.remotePort > 0) "$host:${form.remotePort}" else ""
        }
        val domains = splitDomains(form.customDomains)
        if (domains.isNotEmpty()) return domains.first()
        return form.subdomain.trim()
    }
    val summary = parseFrpcConfig(settings.configText)
    if (summary.serverAddr.isEmpty()) return ""
    summary.proxies.firstOrNull { it.type == "http" && it.customDomains.isNotEmpty() }
        ?.let { return it.customDomains.first() }
    summary.proxies.firstOrNull { (it.remotePort ?: 0) > 0 }
        ?.let { return "${summary.serverAddr}:${it.remotePort}" }
    return ""
}

/** 拼成播放器可用的完整地址（base + token 段）。 */
fun buildPublicApiUrl(base: String, token: String): String {
    var value = base.trim()
    if (value.isEmpty()) return ""
    if (!value.contains("://")) value = "http://$value"
    value = value.trimEnd('/')
    val normalizedToken = token.trim().trim('/')
    return if (normalizedToken.isEmpty()) value else "$value/$normalizedToken"
}

/** 版本比较：a > b 返回正数，相等 0。 */
fun compareVersions(a: String, b: String): Int {
    val left = a.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    val right = b.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(left.size, right.size)) {
        val diff = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
        if (diff != 0) return diff
    }
    return 0
}

/** 从 frp 官方 tar.gz 里取出 frpc 二进制（最小 tar 解析）。 */
fun extractFrpcFromTarGz(gz: ByteArray): ByteArray? {
    return try {
        java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(gz)).use { input ->
            val header = ByteArray(512)
            var result: ByteArray? = null
            while (result == null) {
                var read = 0
                while (read < 512) {
                    val count = input.read(header, read, 512 - read)
                    if (count < 0) return@use null
                    read += count
                }
                if (header.all { it == 0.toByte() }) return@use null
                val name = String(header, 0, 100, Charsets.UTF_8).trimEnd('\u0000', ' ')
                val sizeText = String(header, 124, 12, Charsets.UTF_8)
                    .trim().trimEnd('\u0000').trim()
                val size = sizeText.toLongOrNull(8) ?: 0L
                if (name.endsWith("/frpc") || name == "frpc") {
                    val data = ByteArray(size.toInt().coerceAtLeast(0))
                    var offset = 0
                    while (offset < data.size) {
                        val count = input.read(data, offset, data.size - offset)
                        if (count < 0) break
                        offset += count
                    }
                    result = data
                } else {
                    var skip = size + ((512 - (size % 512)) % 512)
                    while (skip > 0) {
                        val skipped = input.skip(skip)
                        if (skipped <= 0) break
                        skip -= skipped
                    }
                }
            }
            result
        }
    } catch (_: Exception) {
        null
    }
}

/** 从 frpc 日志尾部判断链路状态（后出现的日志优先）。 */
fun parseFrpcLogLinkState(logTail: String): TunnelLinkState {
    var state = TunnelLinkState.Unknown
    logTail.split('\n').forEach { line ->
        val lower = line.lowercase()
        // "connect to local service ... connection refused" 是运行期本机目标端口的问题，
        // 不代表 frpc 启动/链路失败，不能改链路状态。
        if (lower.contains("local service")) return@forEach
        when {
            lower.contains("start proxy success") -> state = TunnelLinkState.Connected
            lower.contains("login to server success") || lower.contains("try to connect to server") ->
                if (state != TunnelLinkState.Connected) state = TunnelLinkState.Connecting
            lower.contains("login to server failed") ||
                lower.contains("connect to server error") ||
                lower.contains("start error") ||
                lower.contains("i/o timeout") ||
                lower.contains("connection refused") -> state = TunnelLinkState.Error
        }
    }
    return state
}

/** 日志里最后一条错误，给 UI 展示。 */
fun lastFrpcError(logTail: String): String {
    logTail.split('\n').reversed().forEach { line ->
        val lower = line.lowercase()
        if (lower.contains("local service")) return@forEach
        if (lower.contains("login to server failed") ||
            lower.contains("connect to server error") ||
            lower.contains("start error") ||
            lower.contains("i/o timeout")
        ) {
            return line.trim()
        }
    }
    return ""
}
