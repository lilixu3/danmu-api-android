package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CancellableResponseTest {
    @Test fun cancellationStopsAResponseBodyReadAlreadyInProgress() = runBlocking {
        val headersSent = CountDownLatch(1); val release = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow") { exchange ->
            exchange.sendResponseHeaders(200, 100)
            exchange.responseBody.write(byteArrayOf(1)); exchange.responseBody.flush()
            headersSent.countDown(); release.await(5, TimeUnit.SECONDS); exchange.close()
        }
        server.start()
        try {
            val call = OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:${server.address.port}/slow").build())
            val download = async(Dispatchers.IO) { call.useCancellableResponse { it.body.bytes() } }
            assertTrue(headersSent.await(2, TimeUnit.SECONDS))
            val start = System.nanoTime()
            download.cancelAndJoin()
            assertTrue("cancellation waited for read timeout", (System.nanoTime() - start) < TimeUnit.SECONDS.toNanos(2))
            assertTrue(call.isCanceled())
        } finally { release.countDown(); server.stop(0) }
    }
}
