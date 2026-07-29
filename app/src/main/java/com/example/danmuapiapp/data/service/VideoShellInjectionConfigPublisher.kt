package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

internal object VideoShellInjectionRemoteConfig {
    const val PREF_GROUP = "app_danmu_injection"
    const val KEY_INJECTION_ENABLED = "injection_enabled"
    const val KEY_AUTO_PUSH_ENABLED = "auto_push_enabled"
    const val KEY_CORE_PORT = "core_port"
    const val KEY_CORE_TOKEN = "core_token"
    const val RUNTIME_PREF_GROUP = "runtime"
    const val MIN_SUPPORTED_API_VERSION = 101
    const val API_102_VERSION = 102
    val TARGET_PACKAGES = setOf("com.fongmi.android.tv", "com.github.tvbox.osc")
}

/**
 * App-lifetime libxposed service registry. The injected UI needs runtime access
 * before the settings page is ever opened, so registration cannot live in Compose.
 */
internal object VideoShellXposedServiceRegistry {
    @Volatile
    private var registered = false
    @Volatile
    private var appContext: Context? = null
    private val services = CopyOnWriteArraySet<XposedService>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun start(context: Context) {
        appContext = context.applicationContext
        ensureRegistered()
    }

    fun currentService(): XposedService? {
        ensureRegistered()
        return selectBestService()
    }

    fun addListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        ensureRegistered()
        if (selectBestService() != null) runCatching { listener() }
        return { listeners.remove(listener) }
    }

    private fun ensureRegistered() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || registered) return
        synchronized(this) {
            if (registered) return
            registered = true
            runCatching {
                XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        services.add(service)
                        appContext?.let(VideoShellInjectionConfigPublisher::publishAsync)
                        notifyListeners()
                    }

                    override fun onServiceDied(service: XposedService) {
                        services.remove(service)
                        notifyListeners()
                    }
                })
            }.onFailure {
                registered = false
            }
        }
    }

    private fun selectBestService(): XposedService? {
        return services.maxByOrNull(::serviceScore)
    }

    private fun serviceScore(service: XposedService): Int {
        var score = 0
        val apiVersion = runCatching { service.apiVersion }.getOrDefault(-1)
        if (apiVersion >= VideoShellInjectionRemoteConfig.MIN_SUPPORTED_API_VERSION) {
            score += 1_000 + apiVersion.coerceAtMost(999)
        } else if (apiVersion > 0) {
            score += apiVersion
        }
        val scopeReady = runCatching {
            service.scope.any { it in VideoShellInjectionRemoteConfig.TARGET_PACKAGES }
        }.getOrDefault(false)
        if (scopeReady) score += 200
        if (VideoShellInjectionConfigPublisher.canPublish(service, apiVersion, scopeReady)) score += 50
        return score
    }

    private fun notifyListeners() {
        listeners.forEach { listener -> runCatching { listener() } }
    }
}

internal object VideoShellInjectionConfigPublisher {
    private const val DEFAULT_CORE_PORT = 9321
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        VideoShellXposedServiceRegistry.start(appContext)
        publishAsync(appContext)
    }

    fun publishAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { publishNow(appContext) }
    }

    fun publishToPreferences(context: Context, remotePrefs: SharedPreferences): Boolean {
        val access = RuntimeApiAccessResolver.resolve(
            context = context,
            prefs = context.getSharedPreferences(
                VideoShellInjectionRemoteConfig.RUNTIME_PREF_GROUP,
                Context.MODE_PRIVATE
            ),
            defaultPort = DEFAULT_CORE_PORT
        )
        return remotePrefs.edit()
            .putInt(VideoShellInjectionRemoteConfig.KEY_CORE_PORT, access.port)
            .putString(VideoShellInjectionRemoteConfig.KEY_CORE_TOKEN, access.runtimeToken)
            .commit()
    }

    internal fun canPublish(
        service: XposedService,
        apiVersion: Int = runCatching { service.apiVersion }.getOrDefault(-1),
        scopeReady: Boolean = runCatching {
            service.scope.any { it in VideoShellInjectionRemoteConfig.TARGET_PACKAGES }
        }.getOrDefault(false)
    ): Boolean {
        if (apiVersion < VideoShellInjectionRemoteConfig.MIN_SUPPORTED_API_VERSION || !scopeReady) {
            return false
        }
        return runCatching {
            (service.frameworkProperties and XposedService.PROP_CAP_REMOTE) != 0L
        }.getOrDefault(false)
    }

    private fun publishNow(context: Context): Boolean {
        val service = VideoShellXposedServiceRegistry.currentService() ?: return false
        if (!canPublish(service)) return false
        val remotePrefs = runCatching {
            service.getRemotePreferences(VideoShellInjectionRemoteConfig.PREF_GROUP)
        }.getOrNull() ?: return false
        return runCatching { publishToPreferences(context, remotePrefs) }.getOrDefault(false)
    }
}
