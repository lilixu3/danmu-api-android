package com.example.danmuapiapp.data.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppForegroundUpdateChecker @Inject constructor(
    @ApplicationContext context: Context,
    private val appUpdateService: AppUpdateService
) {
    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 分钟
        private const val REMIND_SNOOZE_MS = 24 * 60 * 60 * 1000L // 24 小时
        private const val PREFS_NAME = "app_update_checker"
        private const val KEY_LAST_AUTO_CHECK_TS = "foreground_last_auto_check_ts"
        private const val KEY_REMIND_SNOOZE_UNTIL_TS = "foreground_remind_snooze_until_ts"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastAutoCheckTime = AtomicLong(prefs.getLong(KEY_LAST_AUTO_CHECK_TS, 0L))
    private val remindSnoozeUntilTime = AtomicLong(prefs.getLong(KEY_REMIND_SNOOZE_UNTIL_TS, 0L))
    private val isAutoChecking = AtomicBoolean(false)

    private val _latestUpdate = MutableStateFlow<AppUpdateService.CheckResult?>(null)
    val latestUpdate: StateFlow<AppUpdateService.CheckResult?> = _latestUpdate.asStateFlow()

    fun onAppResume() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckTime.get() < AUTO_CHECK_INTERVAL_MS) return
        if (!isAutoChecking.compareAndSet(false, true)) return

        scope.launch {
            try {
                persistLastAutoCheckTime(now)
                appUpdateService.checkLatestRelease().onSuccess { info ->
                    if (!info.hasUpdate) {
                        _latestUpdate.value = null
                        return@onSuccess
                    }
                    if (isInRemindSnooze(now)) {
                        _latestUpdate.value = null
                        return@onSuccess
                    }
                    val current = _latestUpdate.value
                    if (current == null || current.latestVersion != info.latestVersion) {
                        _latestUpdate.value = info
                    }
                }
            } finally {
                isAutoChecking.set(false)
            }
        }
    }

    fun consumeLatestPrompt(version: String?) {
        val current = _latestUpdate.value ?: return
        if (version.isNullOrBlank() || current.latestVersion == version) {
            _latestUpdate.value = null
        }
    }

    fun snoozeReminderForToday() {
        val until = System.currentTimeMillis() + REMIND_SNOOZE_MS
        remindSnoozeUntilTime.set(until)
        prefs.edit().putLong(KEY_REMIND_SNOOZE_UNTIL_TS, until).apply()
        _latestUpdate.value = null
    }

    private fun persistLastAutoCheckTime(ts: Long) {
        lastAutoCheckTime.set(ts)
        prefs.edit().putLong(KEY_LAST_AUTO_CHECK_TS, ts).apply()
    }

    private fun isInRemindSnooze(now: Long): Boolean {
        return now < remindSnoozeUntilTime.get()
    }
}
