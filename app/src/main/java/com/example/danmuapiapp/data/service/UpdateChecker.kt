package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext context: Context,
    private val coreRepo: CoreRepository,
    private val settingsRepo: SettingsRepository
) {
    companion object {
        private const val PREFS_NAME = "core_update_checker"
        private const val KEY_LAST_AUTO_CHECK_TIME = "last_auto_check_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val isAutoChecking = AtomicBoolean(false)
    private val isManualChecking = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called on app resume. Checks only when the user-selected cooldown has elapsed. */
    fun onAppResume() {
        val now = System.currentTimeMillis()
        val lastCheck = runCatching { prefs.getLong(KEY_LAST_AUTO_CHECK_TIME, 0L) }
            .getOrDefault(0L)
        if (!CoreUpdateCheckPolicy.shouldCheck(
                nowEpochMillis = now,
                lastCheckEpochMillis = lastCheck,
                intervalMinutes = settingsRepo.coreUpdateCheckIntervalMinutes.value
            )
        ) return
        if (!isAutoChecking.compareAndSet(false, true)) return

        prefs.edit().putLong(KEY_LAST_AUTO_CHECK_TIME, now).apply()
        scope.launch {
            try {
                coreRepo.refreshCoreInfoAndAwait()
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
            info?.needsAttention == true
        } finally {
            isManualChecking.set(false)
        }
    }
}
