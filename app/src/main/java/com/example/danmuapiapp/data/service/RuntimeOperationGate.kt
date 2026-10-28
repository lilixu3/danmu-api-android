package com.example.danmuapiapp.data.service

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class RuntimeOperationGate {
    private val mutex = Mutex()
    private class Lease(val gate: RuntimeOperationGate) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Lease>
    }

    suspend fun <T> exclusive(block: suspend () -> T): T {
        if (currentCoroutineContext()[Lease]?.gate === this) return block()
        return mutex.withLock { withContext(Lease(this@RuntimeOperationGate)) { block() } }
    }
}
