package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.service.AppProxyNode
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.Socket
import javax.net.ssl.*
import java.security.KeyStore

class AppProxyNetworkTest {
    private fun exercise(type: String,reject: Boolean=false) {
        val server=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        val worker=Executors.newSingleThreadExecutor()
        val future=worker.submit<String> {
            server.accept().use {socket ->
                socket.soTimeout=5000
                val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
                var target=""
                if(type=="socks5") {
                    assertEquals(5,input.readUnsignedByte());val count=input.readUnsignedByte();input.readNBytes(count)
                    output.write(byteArrayOf(5,2));output.flush()
                    assertEquals(1,input.readUnsignedByte());assertEquals("user",String(input.readNBytes(input.readUnsignedByte())))
                    assertEquals("pass",String(input.readNBytes(input.readUnsignedByte())))
                    output.write(byteArrayOf(1,if(reject)1 else 0));output.flush()
                    if(reject)return@submit "rejected"
                    assertEquals(5,input.readUnsignedByte());assertEquals(1,input.readUnsignedByte());input.readUnsignedByte();assertEquals(3,input.readUnsignedByte())
                    target=String(input.readNBytes(input.readUnsignedByte()));assertEquals(80,input.readUnsignedShort())
                    output.write(byteArrayOf(5,0,0,1,127,0,0,1,0,80));output.flush()
                } else {
                    val header=StringBuilder();while(!header.endsWith("\r\n\r\n"))header.append(input.readUnsignedByte().toChar())
                    assertTrue(header.contains("CONNECT source.invalid:80"));assertTrue(header.contains("Proxy-Authorization: Basic dXNlcjpwYXNz"))
                    output.write(if(reject)"HTTP/1.1 407 Proxy Authentication Required\r\n\r\n".toByteArray() else "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray());output.flush()
                    if(reject)return@submit "rejected"
                    target="source.invalid"
                }
                val request=StringBuilder();while(!request.endsWith("\r\n\r\n"))request.append(input.readUnsignedByte().toChar())
                assertFalse(request.contains("Proxy-Authorization",ignoreCase=true))
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray());output.flush()
                target
            }
        }
        try {
            val node=AppProxyNode(name="test",type=type,host="127.0.0.1",port=server.localPort,username="user",password="pass")
            val client=OkHttpClient.Builder().callTimeout(6,TimeUnit.SECONDS).build().withProxyNode(node)
            val call=client.newCall(Request.Builder().url("http://source.invalid/check").build())
            if(reject)assertThrows(java.io.IOException::class.java) {call.execute().close()}
            else call.execute().use {assertEquals("OK",it.body.string())}
            assertEquals(if(reject)"rejected" else "source.invalid",future.get(8,TimeUnit.SECONDS))
        } finally {server.close();worker.shutdownNow()}
    }
    @Test fun `HTTP CONNECT uses node auth and remote target resolution`() = exercise("http")
    @Test fun `SOCKS5 authenticates without global JVM credentials and resolves remotely`() = exercise("socks5")
    @Test fun `HTTP auth rejection cannot fall back to direct`() = exercise("http",true)
    @Test fun `SOCKS auth rejection cannot fall back to direct`() = exercise("socks5",true)

    @Test fun `HTTPS target works through verified HTTPS proxy and rejects wrong hostname`() {
        // This bundled key is a public, test-only self-signed CA.
        val store=KeyStore.getInstance("PKCS12").apply {AppProxyNetworkTest::class.java.getResourceAsStream("/proxy-test.p12").use {load(it,"test-only".toCharArray())}}
        val keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {init(store,"test-only".toCharArray())}
        val trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {init(store)}.trustManagers.filterIsInstance<X509TrustManager>().single()
        val ssl=SSLContext.getInstance("TLS").apply {init(keys.keyManagers,arrayOf(trust),null)}
        val origin=ssl.serverSocketFactory.createServerSocket(0,5,InetAddress.getByName("127.0.0.1"))
        val proxy=ssl.serverSocketFactory.createServerSocket(0,5,InetAddress.getByName("127.0.0.1"))
        val pool=Executors.newCachedThreadPool()
        val sockets=java.util.concurrent.ConcurrentLinkedQueue<Socket>()
        fun header(socket: Socket): String {
            val input=socket.getInputStream();val text=StringBuilder()
            while(!text.endsWith("\r\n\r\n")){val c=input.read();if(c<0)throw java.io.IOException("closed");text.append(c.toChar())}
            return text.toString()
        }
        val successes=java.util.concurrent.atomic.AtomicInteger()
        repeat(6) {
            pool.submit {
                runCatching {origin.accept().use {socket ->
                    sockets+=socket;socket.soTimeout=5000
                    header(socket)
                    successes.incrementAndGet()
                    socket.getOutputStream().apply {write("HTTP/1.1 200 OK\r\nContent-Length: 6\r\nConnection: close\r\n\r\nTLS-OK".toByteArray());flush()}
                }}
            }
            pool.submit {
                runCatching {proxy.accept().use {socket ->
                    sockets+=socket;socket.soTimeout=5000
                    val connect=header(socket)
                    assertTrue(connect.startsWith("CONNECT source.invalid:443") || connect.startsWith("CONNECT wrong.invalid:443"))
                    Socket("127.0.0.1",origin.localPort).use {remote ->
                        sockets+=remote
                        socket.getOutputStream().apply {write("HTTP/1.1 200 OK\r\n\r\n".toByteArray());flush()}
                        val copy=pool.submit {runCatching {socket.getInputStream().copyTo(remote.getOutputStream())};runCatching {remote.shutdownOutput()}}
                        runCatching {remote.getInputStream().copyTo(socket.getOutputStream())}
                        copy.cancel(true)
                    }
                }}
            }
        }
        try {
            val node=AppProxyNode(name="TLS proxy",type="https",host="127.0.0.1",port=proxy.localPort)
            val client=OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory,trust).callTimeout(8,TimeUnit.SECONDS).build().withProxyNode(node,ssl.socketFactory)
            client.newCall(Request.Builder().url("https://source.invalid/check").build()).execute().use {assertEquals("TLS-OK",it.body.string())}
            assertThrows(java.io.IOException::class.java) {client.newCall(Request.Builder().url("https://wrong.invalid/check").build()).execute().close()}
            val named=client.withProxyNode(node.copy(tlsServerName="source.invalid"),ssl.socketFactory)
            named.newCall(Request.Builder().url("https://source.invalid/check").build()).execute().use {assertEquals("TLS-OK",it.body.string())}
            val wrongProxy=client.newBuilder().retryOnConnectionFailure(false).build().withProxyNode(node.copy(tlsServerName="wrong.invalid"),ssl.socketFactory)
            assertThrows(java.io.IOException::class.java) {wrongProxy.newCall(Request.Builder().url("https://source.invalid/check").build()).execute().close()}
            val explicitlyInsecure=client.withProxyNode(node.copy(tlsServerName="wrong.invalid",skipCertVerify=true),ssl.socketFactory)
            explicitlyInsecure.newCall(Request.Builder().url("https://source.invalid/check").build()).execute().use {assertEquals("TLS-OK",it.body.string())}
            assertThrows(java.io.IOException::class.java) {explicitlyInsecure.newCall(Request.Builder().url("https://wrong.invalid/check").build()).execute().close()}
            assertEquals(3,successes.get())
        } finally {sockets.forEach {runCatching {it.close()}};proxy.close();origin.close();pool.shutdownNow()}
    }
}
