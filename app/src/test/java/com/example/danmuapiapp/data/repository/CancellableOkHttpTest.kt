package com.example.danmuapiapp.data.repository

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class CancellableOkHttpTest {

    @Test
    fun `取消协程会取消阻塞中的 OkHttp Call`() = runBlocking {
        val call = BlockingCall()
        val job = launch(Dispatchers.IO) {
            call.executeCancellable().close()
        }
        assertTrue(call.started.await(2, TimeUnit.SECONDS))

        job.cancelAndJoin()

        assertTrue(call.isCanceled())
    }

    private class BlockingCall : Call {
        val started = CountDownLatch(1)
        private val cancelled = AtomicBoolean(false)
        private val executed = AtomicBoolean(false)
        private val request = Request.Builder().url("https://example.invalid/").build()

        override fun request(): Request = request

        override fun execute(): Response = error("not used")

        override fun enqueue(responseCallback: Callback) {
            executed.set(true)
            started.countDown()
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = cancelled.get()

        override fun timeout(): Timeout = Timeout.NONE

        override fun <T : Any> tag(type: KClass<T>): T? = null

        override fun <T> tag(type: Class<out T>): T? = null

        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()

        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            computeIfAbsent()

        override fun clone(): Call = BlockingCall()
    }
}
