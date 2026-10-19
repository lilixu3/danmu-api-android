package com.example.danmuapiapp.data.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.danmuapiapp.data.network.GithubConnectivityProbe
import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SourceProbeResult(val source: String,val status: String,val connected: Boolean,val durationMs: Long?,val route: String,
    val routeType: String,val region: String,val regionNote: String,val reason: String,val external: String,val testedAt: Long = System.currentTimeMillis(),
    val regionEvidence: String = "unknown",val regionHost: String = "",val probeHost: String = "",val httpStatus: Int? = null,
    val configurationRequired: Boolean = false) {
    fun connectivityResult() = AppOutboundConnectivityResult(source, connected && status != "failed", durationMs.takeIf { connected && status != "failed" }, reason, configurationRequired, unconfirmed = status == "unsupported")
}
object SourceProbeService {
    private val client by lazy {OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(30,TimeUnit.SECONDS).build()}
    private fun request(context: Context,source: String? = null): Request {
        val access=RuntimeApiAccessResolver.resolve(context,context.getSharedPreferences("runtime",Context.MODE_PRIVATE),9321)
        val url=RuntimeApiUrls.local(access.port,access.tokenPaths.firstOrNull().orEmpty(),"__source-probe")
        return Request.Builder().url(if(source==null)url else "$url?source=${java.net.URLEncoder.encode(source,"UTF-8")}")
            .applyRuntimeApiAuth(access).apply {if(source!=null)post(ByteArray(0).toRequestBody())}.build()
    }
    suspend fun catalog(context: Context): ProxySourceCatalog = withContext(Dispatchers.IO) {
        client.newCall(request(context)).useCancellableResponse {response ->
            check(response.isSuccessful) {"无法读取来源，请先启动服务并确认管理权限"}
            val o=JSONObject(response.body.string())
            fun list(key: String): List<String> {val a=o.optJSONArray(key) ?: return emptyList();return (0 until a.length()).map {a.getString(it)}}
            ProxySourceCatalog(list("sources"),list("enabled").toSet())
        }
    }
    fun externalNetwork(context: Context): String = runCatching {
        val manager=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val vpn=manager.allNetworks.any {manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true}
        val proxy=manager.defaultProxy!=null
        when {vpn&&proxy->"检测到系统 VPN 和 HTTP 代理，可能影响实际出口";vpn->"检测到系统 VPN，可能影响实际出口";proxy->"检测到系统 HTTP 代理；是否经过取决于运行模式与客户端";else->"未发现系统代理信号；无法排除透明代理或路由器分流"}
    }.getOrDefault("无法判断外部代理状态")
    suspend fun connectivity(context: Context, source: String): AppOutboundConnectivityResult {
        if (source == "github") return GithubConnectivityProbe.test(context, "github_raw").copy(source = source)
        if (source == "local") return AppOutboundConnectivityResult(source, true)
        return probe(context, source).connectivityResult()
    }
    suspend fun probe(context: Context,source: String): SourceProbeResult = withContext(Dispatchers.IO) {
        client.newCall(request(context,source)).useCancellableResponse {response ->
            check(response.isSuccessful) {"探测未完成：HTTP ${response.code}"}
            val o=JSONObject(response.body.string())
            decodeSourceProbeResult(source, o, externalNetwork(context))
        }
    }
}


internal fun decodeSourceProbeResult(source: String, o: JSONObject, external: String): SourceProbeResult =
    SourceProbeResult(source, o.optString("status", "failed"), o.optBoolean("connected"), o.optLong("durationMs", -1).takeIf { it >= 0 },
        o.optString("route", "未知"), o.optString("routeType"), o.optString("region", "未知"), o.optString("regionNote"), o.optString("reason"), external,
        regionEvidence = o.optString("regionEvidence", "unknown"), regionHost = o.optString("regionHost"), probeHost = o.optString("probeHost"),
        httpStatus = o.optInt("httpStatus", 0).takeIf { it > 0 }, configurationRequired = o.optBoolean("configurationRequired") || o.optString("status") == "unconfigured")
