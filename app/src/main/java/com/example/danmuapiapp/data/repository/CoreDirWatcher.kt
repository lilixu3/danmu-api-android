package com.example.danmuapiapp.data.repository

import android.os.Build
import android.os.FileObserver
import com.example.danmuapiapp.data.util.WatcherRegistry
import java.io.File

internal class CoreDirWatcher(
    rootDir: File,
    private val onChanged: (String) -> Unit
) {
    val rootPath: String = rootDir.absolutePath
    private val rootCanonical = runCatching { rootDir.canonicalFile }.getOrElse { rootDir }
    private val observers = WatcherRegistry<FileObserver> { it.stopWatching() }
    private val mask = FileObserver.CLOSE_WRITE or
        FileObserver.MODIFY or
        FileObserver.CREATE or
        FileObserver.MOVED_TO or
        FileObserver.MOVED_FROM or
        FileObserver.DELETE or
        FileObserver.DELETE_SELF or
        FileObserver.MOVE_SELF

    fun start() {
        watchRecursively(rootCanonical)
    }

    fun stop() {
        observers.stop()
    }

    private fun createFileObserver(path: String, onEvent: (Int, String?) -> Unit): FileObserver {
        val watchFile = File(path)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(watchFile, mask) {
                override fun onEvent(event: Int, relativePath: String?) {
                    onEvent.invoke(event, relativePath)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(path, mask) {
                override fun onEvent(event: Int, relativePath: String?) {
                    onEvent.invoke(event, relativePath)
                }
            }
        }
    }

    private fun watchRecursively(dir: File) {
        if (observers.isStopped || !dir.exists() || !dir.isDirectory || shouldIgnore(dir)) return
        val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        val registered = observers.register(key) {
            val observer = createFileObserver(key) { event, path ->
                if (observers.isStopped) return@createFileObserver
                val target = if (path.isNullOrBlank()) {
                    File(key)
                } else {
                    File(key, path)
                }
                if (shouldIgnore(target)) return@createFileObserver

                if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0 && target.isDirectory) {
                    watchRecursively(target)
                }

                val rel = toRelative(target).ifBlank { target.name }
                onChanged(rel)

                if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                    observers.remove(key)
                }
            }

            try { observer.startWatching() } catch (error: Exception) {
                runCatching { observer.stopWatching() }
                throw error
            }
            observer
        }
        if (!registered) return
        dir.listFiles()?.filter { it.isDirectory }?.forEach { child ->
            watchRecursively(child)
        }
    }

    private fun toRelative(target: File): String {
        return CoreDirWatchPolicy.relativePath(rootCanonical, target)
    }

    private fun shouldIgnore(target: File): Boolean {
        // 核心目录允许用户手工替换；这里不能在覆盖过程中递归介入，
        // 否则 refreshCoreInfo() -> normalizeCoreLayout() 会对半写入目录做结构整理。
        return CoreDirWatchPolicy.shouldIgnore(rootCanonical, target)
    }
}
