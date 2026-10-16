package com.example.danmuapiapp.ui.common

import androidx.compose.runtime.*
import com.example.danmuapiapp.data.service.AppOutboundConnectivityResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One bounded batch per owner. Changing its configuration disposes old results and work. */
internal class ConnectivityChecks(private val scope: CoroutineScope) {
    val results = mutableStateMapOf<String, AppOutboundConnectivityResult>()
    val pending = mutableStateListOf<String>()
    var running by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var generation = 0

    fun start(ids: List<String>, probe: suspend (String) -> AppOutboundConnectivityResult) {
        if (running || ids.isEmpty()) return
        val keys = ids.distinct()
        val batch = ++generation
        running = true
        pending.addAll(keys)
        keys.forEach { results.remove(it) }
        job = scope.launch {
            val limit = Semaphore(3)
            try {
                coroutineScope {
                    keys.forEach { id -> launch {
                        try {
                            limit.withPermit {
                                val result = try { probe(id) }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { AppOutboundConnectivityResult(id, false, reason = "连接失败或超时") }
                                ensureActive()
                                if (generation == batch) results[id] = result
                            }
                        } finally { if (generation == batch) pending.remove(id) }
                    } }
                }
            } finally {
                if (generation == batch) { pending.clear(); running = false; job = null }
            }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        pending.clear()
        running = false
    }
}

@Composable
internal fun rememberConnectivityChecks(vararg configuration: Any?): ConnectivityChecks {
    val scope = rememberCoroutineScope()
    val checks = remember(*configuration) { ConnectivityChecks(scope) }
    DisposableEffect(checks) { onDispose { checks.cancel() } }
    return checks
}
