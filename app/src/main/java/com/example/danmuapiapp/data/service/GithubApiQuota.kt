package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.SecureStringStore
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Singleton

data class GithubQuotaNotice(val resetEpochSeconds: Long, val resource: String = "core") {
    fun message(nowSeconds: Long = System.currentTimeMillis() / 1000): String {
        val minutes = ((resetEpochSeconds - nowSeconds).coerceAtLeast(1) + 59) / 60
        val quota = if (resource == "search") "搜索额度" else "API 额度"
        return "未配置 GitHub Token，匿名 $quota 已用完。预计约 $minutes 分钟后重置，请稍后重试，或点击配置 GitHub Token。自动检查会在额度恢复后继续，本地功能和播放器服务不受影响。"
    }
}

class GithubQuotaExhaustedException(val notice: GithubQuotaNotice) : IOException(notice.message())

internal object GithubQuotaRequestContext {
    private val automatic = ThreadLocal<Boolean>()
    fun isAutomatic(): Boolean = automatic.get() == true
    fun element(isAutomatic: Boolean) = automatic.asContextElement(isAutomatic)
    suspend fun <T> automatically(block: suspend CoroutineScope.() -> T): T =
        withContext(automatic.asContextElement(true), block)
}

internal class GithubQuotaTracker(private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }) {
    private val exhausted = mutableMapOf<String, Long>()

    @Synchronized
    fun restore(resource: String, resetEpochSeconds: Long) {
        if (resetEpochSeconds > nowSeconds()) exhausted[resource] = resetEpochSeconds
    }

    @Synchronized
    fun observe(resource: String, remaining: Int, resetEpochSeconds: Long?) {
        if (resource !in setOf("core", "search") || remaining < 0) return
        if (remaining == 0) {
            exhausted[resource] = resetEpochSeconds?.takeIf { it > nowSeconds() } ?: (nowSeconds() + 60)
        } else {
            val previousReset = exhausted[resource]
            if (previousReset == null || previousReset <= nowSeconds() ||
                (resetEpochSeconds != null && resetEpochSeconds > previousReset)) {
                exhausted.remove(resource)
            }
        }
    }

    @Synchronized
    fun blocked(resource: String = "core", tokenConfigured: Boolean = false): GithubQuotaNotice? {
        if (tokenConfigured) return null
        exhausted.entries.removeAll { it.value <= nowSeconds() }
        val blockedResource = if (exhausted.containsKey("core")) "core" else resource
        return exhausted[blockedResource]?.let { GithubQuotaNotice(it, blockedResource) }
    }

    @Synchronized
    fun resetFor(resource: String): Long = exhausted[resource] ?: 0L
}

@Singleton
class GithubApiQuota private constructor(context: Context) {
    companion object {
        @Volatile private var instance: GithubApiQuota? = null
        fun get(context: Context): GithubApiQuota = instance ?: synchronized(this) {
            instance ?: GithubApiQuota(context.applicationContext).also { instance = it }
        }
    }
    private val tokenStore = SecureStringStore(
        context.getSharedPreferences("github_auth_prefs", Context.MODE_PRIVATE),
        "danmuapi_github_auth_v1",
        allowPlaintextFallback = false
    )
    private val prefs = context.getSharedPreferences("github_anonymous_quota", Context.MODE_PRIVATE)
    private val tracker = GithubQuotaTracker()
    private val _notice = MutableStateFlow<GithubQuotaNotice?>(null)
    val notice = _notice.asStateFlow()

    init {
        listOf("core", "search").forEach { tracker.restore(it, prefs.getLong(it, 0L)) }
        if (!prefs.contains("core")) {
            val snapshot = GithubAccountSnapshotCodec.decode(
                context.getSharedPreferences("github_quota_snapshot", Context.MODE_PRIVATE).getString("status", null), ""
            )
            if (snapshot?.status?.coreRemaining == 0) {
                tracker.restore("core", snapshot.status.coreResetEpochSeconds ?: 0L)
            }
        }
    }

    fun blocked(resource: String = "core"): GithubQuotaNotice? =
        tracker.blocked(resource, tokenStore.get("github_token").isNotBlank())

    fun allowManualOperation(): Boolean {
        val blocked = blocked() ?: return true
        _notice.value = blocked
        return false
    }

    fun requireAvailable(resource: String = "core", notify: Boolean = !GithubQuotaRequestContext.isAutomatic()) {
        val blocked = blocked(resource) ?: return
        if (notify) _notice.value = blocked
        throw GithubQuotaExhaustedException(blocked)
    }

    fun dismissNotice() { _notice.value = null }

    @Synchronized
    internal fun observe(resource: String, remaining: Int, resetEpochSeconds: Long?) {
        tracker.observe(resource, remaining, resetEpochSeconds)
        prefs.edit().putLong(resource, tracker.resetFor(resource)).apply()
    }

    internal fun observeAccount(token: String, status: GithubAccountStatus) {
        if (token.isNotBlank()) return
        val remaining = status.coreRemaining ?: return
        observe("core", remaining, status.coreResetEpochSeconds)
    }
}
