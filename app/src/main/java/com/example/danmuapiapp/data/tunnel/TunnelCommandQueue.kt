package com.example.danmuapiapp.data.tunnel

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** All Service instances in :node share ordering, including an old instance's final stop. */
internal class TunnelCommandQueue(private val executor: Executor = worker, private val onFailure: (Exception) -> Unit = {}) {
    private var closed = false
    @Synchronized fun execute(task: () -> Unit) {
        if (!closed) executor.execute { safely(task) }
    }
    @Synchronized fun close(finalStop: () -> Unit) {
        if (closed) return
        closed = true
        executor.execute { safely(finalStop) }
    }
    private fun safely(task: () -> Unit) {
        try { task() } catch (error: Exception) { runCatching { onFailure(error) } }
    }
    private companion object {
        val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "danmu-frpc-control").apply { isDaemon = true } }
    }
}
