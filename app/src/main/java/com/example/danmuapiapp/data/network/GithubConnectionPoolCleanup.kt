package com.example.danmuapiapp.data.network

import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** TLS close may write close_notify; selection callbacks must never do it on UI. */
internal class GithubConnectionPoolCleanup(private val closeConnections: () -> Unit) {
    fun schedule() {
        worker.execute { runCatching { closeConnections() } }
    }

    private companion object {
        val worker: Executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "github-connection-cleanup").apply { isDaemon = true }
        }
    }
}
