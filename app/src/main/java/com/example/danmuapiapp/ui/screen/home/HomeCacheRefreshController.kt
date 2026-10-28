package com.example.danmuapiapp.ui.screen.home

import android.content.Context
import android.os.Build
import android.os.FileObserver
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CacheRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 首页「缓存与请求记录刷新」子控制器：运行时状态驱动的刷新调度、缓存目录监听与去抖。
 *
 * 只组合运行时状态、缓存/请求记录仓库与协程作用域，对外由 [HomeViewModel] 转发，
 * 行为与拆分前一致。
 */
internal class HomeCacheRefreshController(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val runtimeState: StateFlow<RuntimeState>,
    private val cacheRepo: CacheRepository,
    private val requestRecordRepo: RequestRecordRepository
) {
    companion object {
        private const val CACHE_FILE_REFRESH_DEBOUNCE_MS = 420L
        private const val REQUEST_RECORD_REFRESH_DELAY_MS = 900L
        private const val CACHE_RUNTIME_REFRESH_DELAY_MS = 1200L
        private const val CONSERVATIVE_REQUEST_RECORD_REFRESH_DELAY_MS = 1600L
        private const val CONSERVATIVE_CACHE_RUNTIME_REFRESH_DELAY_MS = 2200L
        private const val CACHE_FILE_OBSERVER_MASK = FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.CREATE or
            FileObserver.MOVED_TO
    }

    private var cacheRefreshJob: Job? = null
    private var requestRecordRefreshJob: Job? = null
    private var runtimeCacheRefreshJob: Job? = null
    private var cacheFileRefreshDebounceJob: Job? = null
    private var cacheFileObserver: FileObserver? = null
    private var cacheObserverRootPath: String? = null

    /** 注册运行时状态驱动的请求记录与缓存刷新监听。 */
    fun observeRuntimeDrivenRefreshes() {
        observeRuntimeDrivenRequestRecordRefresh()
        observeRuntimeDrivenCacheRefresh()
    }

    /** 强制刷新缓存（下拉刷新与清理前调用）。 */
    fun refreshCache() {
        refreshCacheInternal(force = true)
    }

    /** ViewModel 清理时停止目录监听并取消缓存刷新任务。 */
    fun stop() {
        stopCacheFileObserver()
        cacheRefreshJob?.cancel()
    }

    private fun observeRuntimeDrivenRequestRecordRefresh() {
        scope.launch {
            runtimeState
                .map { it.status }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == ServiceStatus.Running) {
                        scheduleRequestRecordRefresh()
                    } else {
                        requestRecordRefreshJob?.cancel()
                        requestRecordRefreshJob = null
                    }
                }
        }
    }

    private fun observeRuntimeDrivenCacheRefresh() {
        scope.launch {
            runtimeState
                .map { it.runMode to it.status }
                .distinctUntilChanged()
                .collect { (runMode, status) ->
                    if (status == ServiceStatus.Running &&
                        runMode == RunMode.Normal &&
                        !normalRuntimeProfile().conservativeMode
                    ) {
                        startCacheFileObserver(runMode)
                    } else {
                        stopCacheFileObserver()
                    }
                    if (status == ServiceStatus.Running) {
                        scheduleRuntimeCacheRefresh()
                    } else {
                        runtimeCacheRefreshJob?.cancel()
                        runtimeCacheRefreshJob = null
                    }
                }
        }
    }

    private fun normalRuntimeProfile() = NormalModeRuntimeProfiles.current(appContext)

    private fun requestRecordRefreshDelayMs(): Long {
        val state = runtimeState.value
        return if (state.runMode == RunMode.Normal && normalRuntimeProfile().conservativeMode) {
            CONSERVATIVE_REQUEST_RECORD_REFRESH_DELAY_MS
        } else {
            REQUEST_RECORD_REFRESH_DELAY_MS
        }
    }

    private fun runtimeCacheRefreshDelayMs(): Long {
        val state = runtimeState.value
        return if (state.runMode == RunMode.Normal && normalRuntimeProfile().conservativeMode) {
            CONSERVATIVE_CACHE_RUNTIME_REFRESH_DELAY_MS
        } else {
            CACHE_RUNTIME_REFRESH_DELAY_MS
        }
    }

    private fun scheduleRequestRecordRefresh(delayMs: Long = requestRecordRefreshDelayMs()) {
        requestRecordRefreshJob?.cancel()
        requestRecordRefreshJob = scope.launch {
            delay(delayMs)
            refreshRequestRecords()
        }
    }

    private fun refreshRequestRecords() {
        scope.launch(Dispatchers.IO) {
            if (runtimeState.value.status != ServiceStatus.Running) return@launch
            runCatching {
                requestRecordRepo.refreshFromService()
            }
        }
    }

    private fun scheduleRuntimeCacheRefresh(delayMs: Long = runtimeCacheRefreshDelayMs()) {
        runtimeCacheRefreshJob?.cancel()
        runtimeCacheRefreshJob = scope.launch {
            delay(delayMs)
            refreshCacheInternal(force = true)
        }
    }

    private fun refreshCacheInternal(force: Boolean = false) {
        if (runtimeState.value.status != ServiceStatus.Running) return
        if (force) {
            cacheRefreshJob?.cancel()
        } else if (cacheRefreshJob?.isActive == true) {
            return
        }
        cacheRefreshJob = scope.launch(Dispatchers.IO) {
            runCatching { cacheRepo.refresh() }
        }
    }

    private fun startCacheFileObserver(runMode: RunMode) {
        val cacheDir = File(RuntimePaths.projectDir(appContext, runMode), ".cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        if (!cacheDir.exists() || !cacheDir.isDirectory) {
            stopCacheFileObserver()
            return
        }
        val rootPath = cacheDir.absolutePath
        if (cacheFileObserver != null && cacheObserverRootPath == rootPath) {
            return
        }
        stopCacheFileObserver()
        val observer = createFileObserver(cacheDir) { event, path ->
            if (event and CACHE_FILE_OBSERVER_MASK == 0) return@createFileObserver
            if (!shouldHandleHomeCacheFileChange(path)) return@createFileObserver
            scheduleCacheRefreshFromFileEvent()
        }
        observer.startWatching()
        cacheFileObserver = observer
        cacheObserverRootPath = rootPath
    }

    private fun stopCacheFileObserver() {
        cacheFileObserver?.let { observer ->
            runCatching { observer.stopWatching() }
        }
        cacheFileObserver = null
        cacheObserverRootPath = null
        cacheFileRefreshDebounceJob?.cancel()
        cacheFileRefreshDebounceJob = null
    }

    private fun scheduleCacheRefreshFromFileEvent() {
        cacheFileRefreshDebounceJob?.cancel()
        cacheFileRefreshDebounceJob = scope.launch {
            delay(CACHE_FILE_REFRESH_DEBOUNCE_MS)
            refreshCacheInternal()
        }
    }

    private fun createFileObserver(
        dir: File,
        onEvent: (Int, String?) -> Unit
    ): FileObserver {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, CACHE_FILE_OBSERVER_MASK) {
                override fun onEvent(event: Int, path: String?) {
                    onEvent.invoke(event, path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, CACHE_FILE_OBSERVER_MASK) {
                override fun onEvent(event: Int, path: String?) {
                    onEvent.invoke(event, path)
                }
            }
        }
    }
}

/** 缓存目录文件事件过滤：只有请求记录相关文件的变化才触发刷新。 */
internal fun shouldHandleHomeCacheFileChange(path: String?): Boolean {
    val name = path
        ?.substringAfterLast('/')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (name.isBlank()) return false
    return name == "reqrecords" || name == "todayreqnum"
}
