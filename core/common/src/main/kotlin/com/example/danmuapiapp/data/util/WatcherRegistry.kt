package com.example.danmuapiapp.data.util

/** Registration and permanent shutdown share one lock; callbacks cannot resurrect a stopped watcher. */
internal class WatcherRegistry<T>(private val dispose: (T) -> Unit) {
    private val lock = Any()
    private val entries = LinkedHashMap<String, T>()
    private var stopped = false

    val isStopped: Boolean get() = synchronized(lock) { stopped }

    fun register(key: String, createAndStart: () -> T): Boolean = synchronized(lock) {
        if (stopped || entries.containsKey(key)) return false
        entries[key] = createAndStart()
        true
    }

    fun remove(key: String) {
        val removed = synchronized(lock) { entries.remove(key) }
        if (removed != null) runCatching { dispose(removed) }
    }

    fun stop() {
        val removed = synchronized(lock) {
            stopped = true
            entries.values.toList().also { entries.clear() }
        }
        removed.forEach { runCatching { dispose(it) } }
    }
}
