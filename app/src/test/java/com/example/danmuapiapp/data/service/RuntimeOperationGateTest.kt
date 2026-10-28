package com.example.danmuapiapp.data.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RuntimeOperationGateTest {
    @Test fun `mode switch waits for an active upload and nested operations do not deadlock`() = runBlocking {
        val gate = RuntimeOperationGate()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val upload = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.exclusive {
                events += "upload"
                gate.exclusive { events += "register-source" }
                release.await()
                events += "complete"
            }
        }
        val transition = launch(start = CoroutineStart.UNDISPATCHED) { gate.exclusive { events += "switch" } }
        assertEquals(listOf("upload", "register-source"), events)
        release.complete(Unit)
        upload.join()
        transition.join()
        assertEquals(listOf("upload", "register-source", "complete", "switch"), events)
    }

    @Test fun `cancelled reads release the gate for forced refresh`() = runBlocking {
        val gate = RuntimeOperationGate()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            gate.exclusive { CompletableDeferred<Unit>().await() }
        }
        pending.cancelAndJoin()
        assertEquals("fresh", gate.exclusive { "fresh" })
    }
}
