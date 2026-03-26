package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.remote.announcement.AnnouncementRemoteService
import com.example.danmuapiapp.domain.model.AppAnnouncement
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AppForegroundAnnouncementChecker @Inject constructor(
    @ApplicationContext context: Context,
    private val announcementRemoteService: AnnouncementRemoteService,
    private val runtimeRepository: RuntimeRepository
) {
    companion object {
        private const val SNOOZE_MS = 24 * 60 * 60 * 1000L
        private const val PREFS_NAME = "announcement_checker"
        private const val KEY_SNOOZE_UNTIL_TS = "snooze_until_ts"
        private const val KEY_SNOOZED_ANNOUNCEMENT_ID = "snoozed_announcement_id"
        private const val KEY_ACKNOWLEDGED_IDS = "acknowledged_ids"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snoozeUntilTime = AtomicLong(prefs.getLong(KEY_SNOOZE_UNTIL_TS, 0L))
    private val isAutoChecking = AtomicBoolean(false)

    private val _latestAnnouncement = MutableStateFlow<AppAnnouncement?>(null)
    val latestAnnouncement: StateFlow<AppAnnouncement?> = _latestAnnouncement.asStateFlow()
    private val _unreadAnnouncements = MutableStateFlow<List<AppAnnouncement>>(emptyList())
    val unreadAnnouncements: StateFlow<List<AppAnnouncement>> = _unreadAnnouncements.asStateFlow()

    fun onAppResume() {
        if (!isAutoChecking.compareAndSet(false, true)) return

        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val variant = runtimeRepository.runtimeState.value.variant
                val unreadAnnouncements = announcementRemoteService
                    .fetchActiveAnnouncements(variant)
                    .filterNot { announcement ->
                        isAcknowledged(announcement.id) ||
                            isSnoozedForAnnouncement(announcement.id, now)
                    }
                _unreadAnnouncements.value = unreadAnnouncements
                _latestAnnouncement.value = unreadAnnouncements.firstOrNull()
            } finally {
                isAutoChecking.set(false)
            }
        }
    }

    fun consumeLatestPrompt(announcementId: String?) {
        val current = _latestAnnouncement.value ?: return
        if (!announcementId.isNullOrBlank() && current.id != announcementId) return
        _latestAnnouncement.value = null
    }

    fun acknowledgeAnnouncement(announcementId: String?) {
        val normalizedId = announcementId?.trim().orEmpty()
        if (normalizedId.isBlank()) return
        val acknowledged = prefs.getStringSet(KEY_ACKNOWLEDGED_IDS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()
        acknowledged += normalizedId
        prefs.edit { putStringSet(KEY_ACKNOWLEDGED_IDS, acknowledged) }
        removeAnnouncementFromUnread(normalizedId)
        consumeLatestPrompt(normalizedId)
    }

    fun acknowledgeAnnouncements(announcementIds: Collection<String>) {
        val normalizedIds = announcementIds.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (normalizedIds.isEmpty()) return
        val acknowledged = prefs.getStringSet(KEY_ACKNOWLEDGED_IDS, emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()
        acknowledged += normalizedIds
        prefs.edit { putStringSet(KEY_ACKNOWLEDGED_IDS, acknowledged) }
        _unreadAnnouncements.value = _unreadAnnouncements.value.filterNot { it.id in normalizedIds }
        val currentId = _latestAnnouncement.value?.id
        if (currentId != null && currentId in normalizedIds) {
            _latestAnnouncement.value = null
        }
    }

    fun snoozeForToday(announcementId: String?) {
        val normalizedId = announcementId?.trim().orEmpty()
        if (normalizedId.isBlank()) return
        val until = System.currentTimeMillis() + SNOOZE_MS
        snoozeUntilTime.set(until)
        prefs.edit {
            putLong(KEY_SNOOZE_UNTIL_TS, until)
            putString(KEY_SNOOZED_ANNOUNCEMENT_ID, normalizedId)
        }
        removeAnnouncementFromUnread(normalizedId)
        consumeLatestPrompt(normalizedId)
    }

    private fun isAcknowledged(announcementId: String): Boolean {
        val acknowledged = prefs.getStringSet(KEY_ACKNOWLEDGED_IDS, emptySet()).orEmpty()
        return announcementId in acknowledged
    }

    private fun isSnoozedForAnnouncement(announcementId: String, now: Long): Boolean {
        if (now >= snoozeUntilTime.get()) return false
        val snoozedId = prefs.getString(KEY_SNOOZED_ANNOUNCEMENT_ID, "").orEmpty()
        return snoozedId == announcementId
    }

    private fun removeAnnouncementFromUnread(announcementId: String) {
        _unreadAnnouncements.value = _unreadAnnouncements.value.filterNot { it.id == announcementId }
    }
}
