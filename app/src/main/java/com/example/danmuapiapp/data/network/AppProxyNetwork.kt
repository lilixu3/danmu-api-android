package com.example.danmuapiapp.data.network

import android.content.Context
import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.example.danmuapiapp.data.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

private val insecureProxyTls: SSLSocketFactory by lazy {
    val trust=object: javax.net.ssl.X509TrustManager {
        override fun getAcceptedIssuers()=emptyArray<java.security.cert.X509Certificate>()
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>,authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>,authType: String) {}
    }
    javax.net.ssl.SSLContext.getInstance("TLS").apply {init(null,arrayOf<javax.net.ssl.TrustManager>(trust),java.security.SecureRandom())}.socketFactory
}

/** Explicit HTTP CONNECT/SOCKS5, including per-node auth without JVM global authenticators. */
internal class AppProxySocketFactory(private val node: AppProxyNode, private val proxyTls: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory) : SocketFactory() {
    override fun createSocket(): Socket = TunnelSocket(node, proxyTls)
    override fun createSocket(host: String, port: Int): Socket = createSocket().apply { connect(InetSocketAddress.createUnresolved(host,port),12000) }
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = createSocket(host,port)
    override fun createSocket(host: InetAddress, port: Int): Socket = createSocket(host.hostName,port)
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = createSocket(host,port)
}
private class TunnelSocket(private val selectedNode: AppProxyNode, private val proxyTls: SSLSocketFactory) : Socket() {
    @Volatile private var transport = Socket(Proxy.NO_PROXY)
    private var readTimeout = 12000
    @Volatile private var closing = false
    override fun connect(endpoint: SocketAddress) = connect(endpoint,12000)
    override fun connect(endpoint: SocketAddress, timeout: Int) {
        val target=endpoint as? InetSocketAddress ?: throw IOException("无效目标地址")
        try {
            val node = selectedNode.validate()
            if(closing) throw IOException("连接已取消")
            transport.connect(InetSocketAddress(node.host,node.port),timeout.takeIf { it > 0 } ?: 12000)
            transport.soTimeout=timeout.takeIf { it > 0 } ?: 12000
            if(node.usesTls) {
                val tlsHost=node.tlsServerName.ifBlank {node.host}
                val factory=if(node.skipCertVerify) insecureProxyTls else proxyTls
                val ssl=factory.createSocket(transport,tlsHost,node.port,true) as SSLSocket
                transport=ssl
                ssl.sslParameters=ssl.sslParameters.apply { endpointIdentificationAlgorithm=if(node.skipCertVerify) null else "HTTPS" }
                ssl.startHandshake()
            }
            val input=transport.getInputStream();val output=transport.getOutputStream()
            fun bytes(n: Int): ByteArray = ByteArray(n).also { data -> var offset=0;while(offset<n) {val read=input.read(data,offset,n-offset);if(read<0)throw IOException("代理握手提前结束");offset+=read} }
            if(node.type == "socks5") {
                output.write(if(node.username.isNotEmpty()) byteArrayOf(5,2,0,2) else byteArrayOf(5,1,0));output.flush()
                val greeting=bytes(2)
                if(greeting[0].toInt()!=5 || greeting[1].toInt() !in listOf(0,2))throw IOException("SOCKS5 认证方式不受支持")
                if(greeting[1].toInt()==2) {
                    val user=node.username.toByteArray();val pass=node.password.toByteArray()
                    output.write(byteArrayOf(1,user.size.toByte())+user+byteArrayOf(pass.size.toByte())+pass);output.flush()
                    val auth=bytes(2);if(auth[0].toInt()!=1 || auth[1].toInt()!=0)throw IOException("SOCKS5 认证失败")
                }
                val host=target.hostString.toByteArray(Charsets.UTF_8)
                if(host.size>255)throw IOException("目标域名过长")
                output.write(byteArrayOf(5,1,0,3,host.size.toByte())+host+byteArrayOf((target.port shr 8).toByte(),target.port.toByte()));output.flush()
                val reply=bytes(4)
                if(reply[0].toInt()!=5 || reply[1].toInt()!=0)throw IOException("SOCKS5 无法连接目标 (${reply[1]})")
                when(reply[3].toInt()) {1->bytes(6);4->bytes(18);3->bytes((bytes(1)[0].toInt() and 255)+2);else->throw IOException("SOCKS5 响应无效")}
            } else {
                val authority="${target.hostString.let { if(it.contains(':')) "[$it]" else it }}:${target.port}"
                val auth=if(node.username.isEmpty()) "" else "Proxy-Authorization: Basic ${java.util.Base64.getEncoder().encodeToString("${node.username}:${node.password}".toByteArray())}\r\n"
                output.write("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n$auth\r\n".toByteArray(Charsets.ISO_8859_1));output.flush()
                val header=StringBuilder()
                while(!header.endsWith("\r\n\r\n")) {if(header.length>=16384)throw IOException("代理响应头过大");val c=input.read();if(c<0)throw IOException("代理连接已断开");header.append(c.toChar())}
                val status=Regex("^HTTP/1\\.[01] (\\d{3})").find(header)?.groupValues?.get(1)
                if(status!="200")throw IOException(if(status=="407") "HTTP 代理认证失败" else "HTTP 代理隧道失败 ($status)")
            }
            transport.soTimeout=readTimeout
        } catch(error: Exception) {close();throw error}
    }
    override fun getInputStream(): InputStream = transport.getInputStream()
    override fun getOutputStream(): OutputStream = transport.getOutputStream()
    override fun close() { closing=true;transport.close() }
    override fun isConnected() = transport.isConnected
    override fun isClosed() = transport.isClosed
    override fun isInputShutdown() = transport.isInputShutdown
    override fun isOutputShutdown() = transport.isOutputShutdown
    override fun shutdownInput() = transport.shutdownInput()
    override fun shutdownOutput() = transport.shutdownOutput()
    override fun getInetAddress(): InetAddress? = transport.inetAddress
    override fun getLocalAddress(): InetAddress = transport.localAddress
    override fun getPort() = transport.port
    override fun getLocalPort() = transport.localPort
    override fun getRemoteSocketAddress(): SocketAddress? = transport.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress? = transport.localSocketAddress
    override fun setSoTimeout(timeout: Int) {readTimeout=timeout;transport.soTimeout=timeout}
    override fun getSoTimeout() = transport.soTimeout
    override fun setTcpNoDelay(on: Boolean) {transport.tcpNoDelay=on}
    override fun getTcpNoDelay() = transport.tcpNoDelay
    override fun setKeepAlive(on: Boolean) {transport.keepAlive=on}
    override fun getKeepAlive() = transport.keepAlive
}
internal fun OkHttpClient.withProxyNode(node: AppProxyNode, proxyTls: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory): OkHttpClient = newBuilder()
    .proxy(Proxy.NO_PROXY).socketFactory(AppProxySocketFactory(node, proxyTls))
    // Preserve the hostname without resolving the target on the local network.
    .dns(Dns { host -> listOf(InetAddress.getByAddress(host,byteArrayOf(192.toByte(),0,2,1))) })
    .connectionPool(okhttp3.ConnectionPool(0,1,TimeUnit.SECONDS)).build()

object AppProxyNetwork {
    private val base by lazy { OkHttpClient.Builder().proxy(Proxy.NO_PROXY).connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build() }
    suspend fun probeNode(node: AppProxyNode): AppOutboundConnectivityResult = withContext(Dispatchers.IO) {
        val start=System.nanoTime()
        base.withProxyNode(node).newCall(Request.Builder().url("https://cp.cloudflare.com/generate_204").build()).useCancellableResponse { response ->
            check(response.code==204) { "检测站返回 HTTP ${response.code}" }
            AppOutboundConnectivityResult(node.id, true, (System.nanoTime()-start)/1_000_000)
        }
    }
}
