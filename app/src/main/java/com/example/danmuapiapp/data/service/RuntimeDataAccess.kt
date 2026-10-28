package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.SharedPreferences
import com.example.danmuapiapp.data.repository.executeCancellable
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.RunMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class RuntimeDataContext(
    val mode: RunMode,
    val projectDir: File,
    val variant: String,
    val access: RuntimeApiAccess,
    val generation: Long,
    val selectionKey: String,
    val libraryId: String,
    val sessionId: String = "",
    val coreRevision: String = "",
    val cacheClearSupport: com.example.danmuapiapp.domain.model.CacheClearSupport = com.example.danmuapiapp.domain.model.CacheClearSupport.Unknown
) {
    val sessionKey: String get() = "$selectionKey:$generation:$sessionId:$coreRevision"
}

private class RuntimeDataOperation(val owner: RuntimeDataAccess, val target: RuntimeDataContext) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RuntimeDataOperation>
}

@Singleton
class RuntimeDataAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    httpClient: OkHttpClient
) {
    private val gate = RuntimeOperationGate()
    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
    private val client = httpClient.newBuilder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private val epoch = MutableStateFlow(0L)
    val changes = epoch.asStateFlow()
    @Volatile private var onlineTarget: RuntimeDataContext? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("port", "token", "variant", "run_mode", "root_mode", "custom_path", "custom_uri")) invalidate()
    }
    private val watchedPreferences = listOf(prefs,
        context.getSharedPreferences(RuntimeModePrefs.PREFS_NAME, Context.MODE_PRIVATE),
        context.getSharedPreferences(RuntimePaths.PREFS_WORK_DIR, Context.MODE_PRIVATE))

    init { watchedPreferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) } }

    @Synchronized fun invalidate() {
        onlineTarget = null
        epoch.value += 1
    }

    fun capture(): RuntimeDataContext {
        val mode = RuntimePaths.currentRunMode(context)
        val project = RuntimePaths.projectDir(context, mode)
        val variant = prefs.getString("variant", "stable").orEmpty().ifBlank { "stable" }
        val access = RuntimeApiAccessResolver.resolve(context, prefs, 9321)
        val selection = RuntimeDataIdentity.digest(listOf(mode.key, RuntimeDataIdentity.normalizeHome(project.absolutePath),
            variant, access.port.toString(), access.runtimeToken).joinToString("|"))
        return RuntimeDataContext(mode, project, variant, access, epoch.value, selection,
            RuntimeDataIdentity.fileLibrary(project.absolutePath))
    }

    fun isCurrent(target: RuntimeDataContext): Boolean {
        val current = capture()
        return current.generation == target.generation && current.selectionKey == target.selectionKey
    }

    fun isCurrentSession(sessionKey: String): Boolean = onlineTarget?.let {
        it.sessionKey == sessionKey && isCurrent(it)
    } == true

    fun requireCurrent(target: RuntimeDataContext) {
        check(isCurrent(target)) { "运行实例已变化，请刷新后重试；不会自动改投其他运行实例" }
    }

    suspend fun <T> withRuntime(expectedSessionKey: String? = null, block: suspend (RuntimeDataContext) -> T): T {
        val active = currentCoroutineContext()[RuntimeDataOperation]
        if (active?.owner === this) {
            requireCurrent(active.target)
            check(expectedSessionKey.isNullOrBlank() || active.target.sessionKey == expectedSessionKey) {
                "导入目标已变化，请重新打开导入页面"
            }
            return block(active.target)
        }
        return withContext(Dispatchers.IO) {
            gate.exclusive {
                val target = verify(capture())
                check(expectedSessionKey.isNullOrBlank() || target.sessionKey == expectedSessionKey) {
                    "运行实例已变化，请刷新后重新操作"
                }
                withContext(RuntimeDataOperation(this@RuntimeDataAccess, target)) {
                    block(target).also { requireCurrent(target) }
                }
            }
        }
    }

    suspend fun <T> withOffline(block: suspend (RuntimeDataContext) -> T): T = withContext(Dispatchers.IO) {
        gate.exclusive {
            val target = capture()
            block(target).also { requireCurrent(target) }
        }
    }

    suspend fun <T> transition(block: suspend () -> T): T = gate.exclusive {
        invalidate()
        try { block() } finally { invalidate() }
    }

    private suspend fun verify(target: RuntimeDataContext): RuntimeDataContext {
        val request = Request.Builder().url(RuntimeApiUrls.local(target.access.port, "", "__health"))
            .applyRuntimeApiAuth(target.access).build()
        val health = client.newCall(request).executeCancellable().use { response ->
            check(response.isSuccessful) { "无法确认当前运行实例（HTTP ${response.code}）" }
            JSONObject(response.body.string())
        }
        check(RuntimeDataIdentity.matches(target.projectDir.absolutePath, health.optString("resolvedHome"),
            target.variant, health.optString("variant"))) { "接口运行目录或核心与当前选择不一致，已阻止跨实例操作" }
        check(health.optString("runtimeIdentity") == RuntimeIdentityStore.ensureInstanceId(context)) {
            "接口不属于当前应用，已阻止操作"
        }
        val sessionId = health.optString("runtimeSessionId")
        check(sessionId.isNotBlank()) { "运行时尚未支持实例校验，请重启服务后重试" }
        check(health.optBoolean("coreReady", false)) { "核心文件已变化或尚未就绪，请等待重载或重启服务" }
        val libraryId = health.optString("localDanmuLibraryId")
        check(libraryId.isNotBlank()) { "无法确认当前弹幕库的存储归属，请重启服务" }
        requireCurrent(target)
        return target.copy(sessionId = sessionId, libraryId = libraryId,
            coreRevision = health.optString("coreRevision"),
            cacheClearSupport = com.example.danmuapiapp.domain.model.CacheClearSupport.entries
                .firstOrNull { it.name == health.optString("cacheClearSupport") }
                ?: com.example.danmuapiapp.domain.model.CacheClearSupport.Unknown,
            access = target.access.copy(expectedSessionId = sessionId)).also { onlineTarget = it }
    }
}
