package com.example.danmuapiapp.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.eclipse.jgit.transport.http.HttpConnection
import org.eclipse.jgit.transport.http.HttpConnectionFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Proxy
import java.net.URL
import java.security.KeyManagementException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManager

/** Per-transport JGit adapter; never changes JGit's global connection factory. */
internal class GithubJGitTransport(
    private val client: OkHttpClient,
    private val cacheDirectory: File,
    job: Job?
) : HttpConnectionFactory, AutoCloseable {
    private val connections = ConcurrentHashMap.newKeySet<Connection>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob(job))
    @Volatile private var closed = false
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { cancelConnections() }
        }
    }
    override fun create(url: URL): HttpConnection = create(url, Proxy.NO_PROXY)
    override fun create(url: URL, proxy: Proxy): HttpConnection {
        if (closed) throw IOException("Git operation cancelled")
        // GitHub's client selects the authenticated local route dynamically.
        // Existing JGit proxy behavior remains on the default factory otherwise.
        return Connection(url).also {
            connections.add(it)
            if (closed) { it.dispose(); throw IOException("Git operation cancelled") }
        }
    }
    private fun cancelConnections() {
        closed = true
        connections.forEach { it.dispose() }
        connections.clear()
    }
    override fun close() { cancelConnections(); scope.cancel() }

    private inner class Connection(private val address: URL) : HttpConnection {
        private val headers = okhttp3.Headers.Builder()
        private var method = "GET"
        private var connectMs = client.connectTimeoutMillis
        private var readMs = client.readTimeoutMillis
        private var redirects = false
        private var output = false
        private var fixedLength: Int? = null
        private var bodyFile: File? = null
        private var bodyOutput: OutputStream? = null
        @Volatile private var call: Call? = null
        @Volatile private var response: Response? = null

        private fun execute(): Response {
            response?.let { return it }
            if (closed) throw IOException("Git operation cancelled")
            bodyOutput?.close()
            val type = headers["Content-Type"]?.toMediaTypeOrNull()
            val body = if (output || method == "POST" || method == "PUT") {
                bodyFile?.let { file ->
                    if (fixedLength != null && file.length() != fixedLength!!.toLong()) throw IOException("Incomplete Git request body")
                    file.asRequestBody(type)
                } ?: ByteArray(0).toRequestBody(type)
            } else null
            val request = Request.Builder().url(address).headers(headers.build()).method(method, body).build()
            val requestClient = client.newBuilder().connectTimeout(connectMs.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(readMs.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(redirects).followSslRedirects(redirects).build()
            val active = requestClient.newOutboundCall(request)
            call = active
            if (closed) { active.cancel(); throw IOException("Git operation cancelled") }
            try {
                return active.execute().also { result ->
                    response = result
                    if (closed) { result.close(); throw IOException("Git operation cancelled") }
                }
            } finally { bodyFile?.delete() }
        }
        fun dispose() {
            call?.cancel()
            runCatching { response?.close() }
            runCatching { bodyOutput?.close() }
            bodyFile?.delete()
        }
        override fun getResponseCode(): Int = execute().code
        override fun getURL(): URL = address
        override fun getResponseMessage(): String = execute().message
        override fun getHeaderFields(): Map<String, List<String>> = execute().headers.toMultimap()
        override fun getHeaderFields(name: String): List<String> = execute().headers.values(name)
        override fun getHeaderField(name: String): String? = execute().header(name)
        override fun getContentType(): String? = execute().header("Content-Type")
        override fun getContentLength(): Int = execute().body.contentLength().takeIf { it in 0..Int.MAX_VALUE }?.toInt() ?: -1
        override fun getInputStream(): InputStream = execute().body.byteStream()
        override fun setRequestProperty(name: String, value: String) { headers.set(name, value) }
        override fun setRequestMethod(value: String) { method = value }
        override fun getRequestMethod(): String = method
        override fun setUseCaches(value: Boolean) { /* HTTP cache is disabled. */ }
        override fun setConnectTimeout(value: Int) { require(value >= 0); connectMs = value }
        override fun setReadTimeout(value: Int) { require(value >= 0); readMs = value }
        override fun setInstanceFollowRedirects(value: Boolean) { redirects = value }
        override fun setDoOutput(value: Boolean) { output = value }
        override fun setFixedLengthStreamingMode(value: Int) { require(value >= 0); fixedLength = value }
        override fun setChunkedStreamingMode(value: Int) { fixedLength = null }
        override fun getOutputStream(): OutputStream {
            bodyOutput?.let { return it }
            if (closed) throw IOException("Git operation cancelled")
            output = true
            cacheDirectory.mkdirs()
            val file = File.createTempFile("github-git-", ".request", cacheDirectory).also { bodyFile = it }
            return FileOutputStream(file).also { bodyOutput = it }
        }
        override fun usingProxy(): Boolean = true
        override fun connect() { execute() }
        override fun configure(km: Array<KeyManager>?, tm: Array<TrustManager>?, random: SecureRandom?) {
            throw KeyManagementException("GitHub 增强直连必须验证 TLS 证书")
        }
        override fun setHostnameVerifier(verifier: HostnameVerifier?) {
            throw KeyManagementException("GitHub 增强直连必须验证原始域名")
        }
    }
}
