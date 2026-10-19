package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.parser.EnvVarConfigLoader
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.RunMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

data class AppProxyNode(
    val id: String = UUID.randomUUID().toString(), val name: String = "",
    val type: String = "http", val host: String = "", val port: Int = 8080,
    val username: String = "", val password: String = "",
    val tls: Boolean = false, val tlsServerName: String = "", val skipCertVerify: Boolean = false
) {
    val usesTls: Boolean get() = type == "https" || (type == "socks5" && tls)
    fun validate(): AppProxyNode {
        require(type in supportedTypes) { "仅支持 HTTP、HTTPS、SOCKS5 节点" }
        require(id.isNotBlank() && name.isNotBlank() && host.isNotBlank() && !host.contains(Regex("[\\s/@?#]"))) { "请填写有效的名称和服务器地址" }
        require(port in 1..65535) { "端口须为 1–65535" }
        require(listOf(username,password).all {it.toByteArray().size<=255 && !it.contains('\r') && !it.contains('\n')}) { "代理凭据过长或含换行符" }
        require(username.isNotEmpty() || password.isEmpty()) { "填写密码时也需要用户名" }
        require(tlsServerName.isEmpty() || !tlsServerName.contains(Regex("[\\s/@?#:]"))) { "TLS 服务器名称无效" }
        require(usesTls || (tlsServerName.isEmpty() && !skipCertVerify && !tls)) { "TLS 参数仅用于 HTTPS 或启用 TLS 的 SOCKS5 节点" }
        return this
    }
    fun json() = JSONObject().put("id",id).put("name",name).put("type",type).put("host",host).put("port",port)
        .put("username",username).put("password",password).put("tls",tls).put("tlsServerName",tlsServerName).put("skipCertVerify",skipCertVerify)
    companion object {
        val supportedTypes = setOf("http","https","socks5")
        fun decode(o: JSONObject): AppProxyNode {
            val options=o.optJSONObject("protocolOptions") ?: JSONObject()
            require(options.keys().asSequence().all {it in setOf("tls","sni","servername","skip-cert-verify","udp","tfo")}) {"旧节点含不支持的传输参数"}
            return AppProxyNode(o.optString("id",UUID.randomUUID().toString()),o.optString("name"),o.optString("type","http"),
                o.optString("host"),o.optInt("port",8080),o.optString("username"),o.optString("password"),
                o.optBoolean("tls",o.optString("type")=="socks5" && options.optBoolean("tls")),
                o.optString("tlsServerName",options.optString("sni",options.optString("servername"))),
                o.optBoolean("skipCertVerify",options.optBoolean("skip-cert-verify"))).validate()
        }
        fun fromUri(text: String): AppProxyNode {
            val uri=URI(text.trim())
            val type=when(uri.scheme?.lowercase()) {"socks","socks5h"->"socks5";else->uri.scheme?.lowercase().orEmpty()}
            require(type in supportedTypes) {"仅支持 HTTP、HTTPS、SOCKS5 节点链接"}
            require(uri.rawPath.orEmpty() in setOf("","/") && uri.rawQuery.isNullOrEmpty()) {"请输入节点地址，不支持订阅或网页地址"}
            fun decode(value: String)=java.net.URLDecoder.decode(value.replace("+","%2B"),"UTF-8")
            val auth=uri.rawUserInfo.orEmpty().split(':',limit=2)
            return AppProxyNode(name=uri.rawFragment?.let(::decode)?.ifBlank {null} ?: uri.host.orEmpty(),type=type,
                host=uri.host.orEmpty().removeSurrounding("[","]"),port=if(uri.port==-1)when(type){"https"->443;"socks5"->1080;else->80}else uri.port,
                username=decode(auth.firstOrNull().orEmpty()),password=decode(auth.getOrNull(1).orEmpty())).validate()
        }
    }
}
data class AppProxySettings(val enabled: Boolean = false, val defaultRoute: String = "inherit", val nodes: List<AppProxyNode> = emptyList(),
    val rules: Map<String,String> = emptyMap()) {
    fun validate(): AppProxySettings {
        require(nodes.size<=1000 && nodes.map {it.id}.distinct().size==nodes.size) {"节点过多或标识重复"}
        nodes.forEach {it.validate()}
        require(defaultRoute !in listOf("enhanced","direct")) {"增强直连和设备网络请按来源单独选择"}
        require(rules.values.all {it in listOf("inherit","enhanced","direct") || it.startsWith("node:")}) {"线路规则无效"}
        return this
    }
    fun json(): JSONObject = JSONObject().put("enabled",enabled).put("routeVersion",2).put("defaultRoute",defaultRoute)
        .put("nodes",JSONArray(nodes.map {it.json()})).put("rules",JSONObject(rules))
    fun label(route: String): String = when(route) {
        "inherit"->"沿用核心配置";"enhanced"->"增强直连";"direct"->"设备当前网络"
        else->nodes.firstOrNull {"node:${it.id}"==route}?.name ?: "节点已失效，请重新选择"
    }
    companion object {
        fun decode(o: JSONObject?): AppProxySettings {
            if(o==null)return AppProxySettings()
            val list=o.optJSONArray("nodes") ?: JSONArray()
            require(list.length()<=1000) {"节点过多"}
            // Unsupported old nodes must not invalidate unrelated routes or enhanced settings.
            // Keep their rule IDs so a removed node fails closed until explicitly replaced.
            val nodes=(0 until list.length()).mapNotNull {runCatching {AppProxyNode.decode(list.getJSONObject(it))}.getOrNull()}.distinctBy {it.id}
            val rules=o.optJSONObject("rules") ?: JSONObject()
            val supportsDeviceRoute=o.optInt("routeVersion",1)>=2
            // Before routeVersion 2, "direct" meant restoring the core route.
            val defaultRoute=if(o.optString("defaultRoute","inherit")=="direct") "inherit" else o.optString("defaultRoute","inherit")
            return AppProxySettings(o.optBoolean("enabled"),defaultRoute,nodes,
                rules.keys().asSequence().associateWith {key ->
                    rules.getString(key).let {if(!supportsDeviceRoute && it=="direct") "inherit" else it}
                }).validate()
        }
    }
}
object ProxyNodeLinks {
    fun parse(text: String): List<AppProxyNode> {
        require(text.toByteArray().size<=256*1024) {"节点链接过长"}
        val lines=text.lineSequence().map(String::trim).filter {it.isNotBlank()}.toList()
        require(lines.isNotEmpty() && lines.size<=1000) {"请输入 1–1000 个节点链接"}
        return lines.mapIndexed {index,line->
            try {AppProxyNode.fromUri(line)} catch (_: Exception) {throw IllegalArgumentException("第 ${index+1} 行无效，仅支持 HTTP、HTTPS、SOCKS5 节点链接")}
        }
    }
}
data class ProxySourceCatalog(val sources: List<String>, val enabled: Set<String>, val legacyProxied: Set<String> = emptySet()) {
    fun visibleSources(showAll: Boolean = false): List<String> =
        if (showAll) (sources + enabled).distinct() else enabled.toList()
}

/** Explicit App nodes win; inherit restores core routing, including its proxy settings. */
fun AppOutboundSettings.sourceRoute(source: String, catalog: ProxySourceCatalog, githubRoute: String = "inherit"): String {
    if (source == "local") return "local"
    val selected = if (proxy.enabled) {
        proxy.rules[source] ?: if (source == "github" || source in AppProxySourceCatalog.coreProxySources) "inherit" else proxy.defaultRoute
    } else "inherit"
    if (selected != "inherit") return if (selected == "enhanced" && !enabled && source != "github") "inherit" else selected
    if (source == "github") return if (githubRoute == "direct") "inherit" else githubRoute
    if (source in catalog.legacyProxied) return "legacy"
    // Preserve old enhanced settings only when the source has no explicit App choice.
    return if (proxy.enabled && proxy.rules.containsKey(source)) "inherit"
        else if (enabled && source in sources) "enhanced" else "inherit"
}
object AppProxySourceCatalog {
    // Sources whose core requests call makeProxyUrl, including the shared TMDB/Aiyifan utilities.
    val coreProxySources = setOf("bahamut", "tmdb", "bilibili", "animeko", "aiyifan")
    fun inheritLabel(source: String): String = when {
        source in coreProxySources -> "沿用核心配置"
        source == "github" -> "沿用下载设置"
        else -> "使用设备当前网络"
    }
    fun legacyProxied(sources: List<String>, raw: String): Set<String> {
        val entries = raw.split(',').map(String::trim).filter(String::isNotBlank)
        return sources.filter { source -> source in coreProxySources && entries.any { entry ->
            entry.startsWith("@") || entry.startsWith("http://", ignoreCase = true) || entry.startsWith("https://", ignoreCase = true) ||
                entry.startsWith("$source@")
        } }.toSet()
    }
    val labels = mapOf("360" to "360 影视", "vod" to "VOD", "tmdb" to "TMDB", "douban" to "豆瓣", "bahamut" to "巴哈姆特", "dandan" to "弹弹play", "animeko" to "Animeko",
        "aiyifan" to "爱壹帆", "tencent" to "腾讯视频", "youku" to "优酷", "iqiyi" to "爱奇艺", "imgo" to "芒果 TV", "bilibili" to "哔哩哔哩", "migu" to "咪咕", "renren" to "人人视频", "hanjutv" to "韩剧 TV",
        "sohu" to "搜狐", "leshi" to "乐视", "xigua" to "西瓜视频", "maiduidui" to "埋堆堆", "hongguo" to "红果", "custom" to "自定义源", "other" to "第三方源", "local" to "本地弹幕", "github" to "GitHub")
    fun label(key: String) = labels[key] ?: key
    fun load(context: Context): ProxySourceCatalog {
        val catalog=EnvVarConfigLoader.loadCatalog(context)
        val all=catalog.firstOrNull { it.key == "SOURCE_ORDER" }?.options.orEmpty()
        val mode = RuntimeModePrefs.get(context)
        // The no-mode projectDir overload always points at the normal workspace.
        // Match configuration management so Root mode never reads its stale copy.
        val file=File(NodeProjectManager.projectDir(context, mode),"config/.env")
        val raw=if(mode==RunMode.Root) RootShell.exec("cat ${shellQuote(file.absolutePath)}",3000).let { if(!it.ok) error("无法读取 Root 配置") else it.stdout }
            else if(file.exists()) file.readText() else ""
        val env=EnvVarConfigLoader.loadDefaultValues(context) + DotEnvCodec.parse(raw)
        val active=(env["SOURCE_ORDER"] ?: "douban,360,renren,hanjutv").split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val sources = (all+active).distinct()
        val legacy = legacyProxied(sources, env["PROXY_URL"].orEmpty())
        return ProxySourceCatalog(sources,active,legacy)
    }
}
