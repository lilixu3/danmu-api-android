package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal fun networkRuntimeNeedsRestart(expected: String, running: String): Boolean =
    expected.isNotBlank() && running != expected

object NetworkRuntimeStatus {
    private val client by lazy {OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(4,TimeUnit.SECONDS).build()}
    suspend fun notice(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val hash=MessageDigest.getInstance("SHA-256")
            for(name in AppRuntimeAssets.names(context)) {hash.update((name+'\u0000').toByteArray());context.assets.open("nodejs-project/$name").use {input->
                val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;hash.update(buffer,0,count)}
            }}
            val expected=hash.digest().joinToString("") {"%02x".format(it)}
            val access=RuntimeApiAccessResolver.resolve(context,context.getSharedPreferences("runtime",Context.MODE_PRIVATE),9321)
            val request=Request.Builder().url(RuntimeApiUrls.local(access.port,access.tokenPaths.firstOrNull().orEmpty(),"__health")).applyRuntimeApiAuth(access).build()
            client.newCall(request).useCancellableResponse {response ->
                if(!response.isSuccessful) return@useCancellableResponse "服务尚未连接；来源线路会在服务启动后使用。"
                val health=JSONObject(response.body.string())
                if(networkRuntimeNeedsRestart(expected,health.optString("networkRuntimeFingerprint")))
                    "服务仍在使用旧版运行脚本。请在首页停止后重新启动服务，让新版网络与下载逻辑生效。"
                else null
            }
        } catch(cancel: CancellationException) {throw cancel}
        catch(_:Exception) {"服务尚未连接；来源线路会在服务启动后使用。"}
    }
}
