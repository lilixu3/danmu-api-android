package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.domain.repository.CoreRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val coreRepo: CoreRepository
) {
    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val lastAutoCheckTime = AtomicLong(0L)
    private val isAutoChecking = AtomicBoolean(false)
    private val isManualChecking = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called on app resume. Silently checks all variants if 5min elapsed. */
    fun onAppResume() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckTime.get() < AUTO_CHECK_INTERVAL_MS) return
        if (!isAutoChecking.compareAndSet(false, true)) return

        scope.launch {
            try {
                lastAutoCheckTime.set(now)
                coreRepo.checkAllUpdates()
            } finally {
                isAutoChecking.set(false)
            }
        }
    }

    /** Manual check for a specific variant. Returns true if update available. */
    suspend fun manualCheck(variant: com.example.danmuapiapp.domain.model.ApiVariant): Boolean {
        if (!isManualChecking.compareAndSet(false, true)) return false
        return try {
            coreRepo.checkAndMarkUpdate(variant)
            val info = coreRepo.coreInfoList.value.find { it.variant == variant }
            info?.hasUpdate == true
        } finally {
            isManualChecking.set(false)
        }
    }
}
