package com.example.danmuapiapp.data.repository

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

/** Keeps an OkHttp request tied to the coroutine that owns it. */
internal suspend fun Call.executeCancellable(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

/** Keeps cancellation attached until the streamed response has been consumed. */
internal suspend fun <T> Call.useCancellableResponse(block: suspend (Response) -> T): T = kotlinx.coroutines.coroutineScope {
    val watcher = launch(kotlinx.coroutines.Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
        try { kotlinx.coroutines.awaitCancellation() } finally { cancel() }
    }
    try { executeCancellable().use { block(it) } } finally { watcher.cancel() }
}
