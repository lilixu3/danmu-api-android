package com.example.danmuapiapp.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsItem
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.SettingsSwitchItem
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet

private const val REMOTE_PREF_GROUP = "app_danmu_injection"
private const val KEY_INJECTION_ENABLED = "injection_enabled"
private const val KEY_AUTO_PUSH_ENABLED = "auto_push_enabled"
private const val KEY_CORE_PORT = "core_port"
private const val KEY_CORE_TOKEN = "core_token"
private const val RUNTIME_PREF_GROUP = "runtime"
private const val TARGET_API_VERSION = 101
private val TARGET_PACKAGES = setOf("com.fongmi.android.tv", "com.github.tvbox.osc")

private data class VideoShellInjectionState(
    val loading: Boolean = true,
    val serviceConnected: Boolean = false,
    val apiVersion: Int? = null,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val scopePackages: Set<String> = emptySet(),
    val injectionEnabled: Boolean = false,
    val autoPushEnabled: Boolean = false,
    val message: String = "正在检查 LSPosed Service"
) {
    val apiSupported: Boolean get() = apiVersion == TARGET_API_VERSION
    val scopeReady: Boolean get() = scopePackages.any { it in TARGET_PACKAGES }
    val environmentReady: Boolean get() = serviceConnected && apiSupported && scopeReady
}

@Composable
fun VideoShellInjectionSettingsScreen(onBack: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(VideoShellInjectionState()) }
    var saving by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            state = state.copy(loading = true, message = "正在读取官方 libxposed Service…")
            state = withContext(Dispatchers.IO) { loadVideoShellInjectionState(appContext) }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    DisposableEffect(Unit) {
        val removeListener = VideoShellXposedServiceRegistry.addListener {
            scope.launch {
                state = state.copy(loading = true, message = "LSPosed Service 状态变化，正在刷新…")
                state = withContext(Dispatchers.IO) { loadVideoShellInjectionState(appContext) }
            }
        }
        onDispose { removeListener() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "影视壳注入",
                subtitle = "通过 LSPosed 官方 Service 检查 API/作用域，并用 Remote Preferences 控制播放页注入",
                onBack = onBack
            )

            SettingsGroup(title = "状态") {
                SettingsItem(
                    title = "LSPosed Service",
                    subtitle = stateStatusSubtitle(state),
                    icon = Icons.Rounded.PowerSettingsNew,
                    trailing = {
                        StatusChip(
                            when {
                                state.loading -> "检查中"
                                state.environmentReady -> "可用"
                                state.serviceConnected -> "受限"
                                else -> "未连接"
                            }
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "LSPosed API",
                    subtitle = when {
                        state.loading -> "正在读取官方 getApiVersion()"
                        !state.serviceConnected -> "未收到 LSPosed Service，无法确认 API"
                        state.apiSupported -> "官方 Service 返回 API ${state.apiVersion}：${state.frameworkName.ifBlank { "Xposed" }} ${state.frameworkVersion}"
                        else -> "当前 API ${state.apiVersion ?: "未知"}，本注入只允许 API $TARGET_API_VERSION"
                    },
                    icon = Icons.Rounded.Movie,
                    trailing = {
                        StatusChip(
                            when {
                                state.loading -> "检查中"
                                state.apiSupported -> "API 101"
                                state.serviceConnected -> "API ${state.apiVersion ?: "?"}"
                                else -> "未激活"
                            }
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "作用域 / Remote Preferences",
                    subtitle = scopeStatusSubtitle(state),
                    icon = Icons.Rounded.Search,
                    trailing = { StatusChip(if (state.scopeReady) "已勾选" else "未勾选") }
                )
            }

            SettingsGroup(title = "开关") {
                val canControlInjection = state.environmentReady && !state.loading && !saving
                val injectionChecked = state.environmentReady && state.injectionEnabled
                val autoChecked = injectionChecked && state.autoPushEnabled
                val missingEnvironment = missingEnvironmentMessage(state)
                SettingsSwitchItem(
                    title = "启用影视壳播放页注入",
                    subtitle = if (state.environmentReady) {
                        if (injectionChecked) "LSPosed API/作用域正常，播放页注入处于开启状态" else "已关闭：播放页不会插入 APP弹幕按钮"
                    } else missingEnvironment,
                    icon = Icons.Rounded.Search,
                    checked = injectionChecked,
                    enabled = canControlInjection,
                    disabledOnClick = {
                        scope.launch { snackbarHostState.showSnackbar(missingEnvironment) }
                    },
                    onCheckedChange = { checked ->
                        if (!state.environmentReady) {
                            scope.launch { snackbarHostState.showSnackbar(missingEnvironment) }
                            return@SettingsSwitchItem
                        }
                        saving = true
                        scope.launch {
                            val nextAutoPush = if (checked) state.autoPushEnabled else false
                            val result = withContext(Dispatchers.IO) {
                                saveVideoShellInjectionSwitches(
                                    context = appContext,
                                    injectionEnabled = checked,
                                    autoPushEnabled = nextAutoPush
                                )
                            }
                            saving = false
                            if (result.ok) {
                                state = state.copy(injectionEnabled = checked, autoPushEnabled = nextAutoPush)
                                snackbarHostState.showSnackbar(if (checked) "已开启播放页注入" else "已关闭播放页注入")
                            } else {
                                snackbarHostState.showSnackbar(result.message)
                                refresh()
                            }
                        }
                    }
                )
                SettingsDivider()
                SettingsSwitchItem(
                    title = "自动推送弹幕",
                    subtitle = when {
                        !state.environmentReady -> missingEnvironment
                        !injectionChecked -> "请先开启播放页注入；注入关闭时自动推送也保持关闭"
                        autoChecked -> "进入播放页后会自动匹配并在播放态推送"
                        else -> "已关闭：仍可点播放页 APP弹幕按钮手动搜索/推送"
                    },
                    icon = Icons.Rounded.Autorenew,
                    checked = autoChecked,
                    enabled = canControlInjection && injectionChecked,
                    disabledOnClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (!state.environmentReady) missingEnvironment else "请先开启播放页注入"
                            )
                        }
                    },
                    onCheckedChange = { checked ->
                        if (!state.environmentReady || !state.injectionEnabled) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (!state.environmentReady) missingEnvironment else "请先开启播放页注入"
                                )
                            }
                            return@SettingsSwitchItem
                        }
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                saveVideoShellInjectionSwitches(
                                    context = appContext,
                                    injectionEnabled = true,
                                    autoPushEnabled = checked
                                )
                            }
                            saving = false
                            if (result.ok) {
                                state = state.copy(autoPushEnabled = checked)
                                snackbarHostState.showSnackbar(if (checked) "已开启自动推送" else "已关闭自动推送")
                            } else {
                                snackbarHostState.showSnackbar(result.message)
                                refresh()
                            }
                        }
                    }
                )
            }

            if (state.loading || saving) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    Text(
                        text = if (saving) "正在保存…" else "正在检查…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                Text("重新检查")
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
    )
}

private fun loadVideoShellInjectionState(context: android.content.Context): VideoShellInjectionState {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return VideoShellInjectionState(
            loading = false,
            message = "libxposed Service 需要 Android 8.0 及以上"
        )
    }
    val service = VideoShellXposedServiceRegistry.currentService()
        ?: return VideoShellInjectionState(
            loading = false,
            message = "未收到 LSPosed Service：请确认模块已在 LSPosed 启用，并重启本 App"
        )
    return try {
        val apiVersion = service.apiVersion
        val frameworkName = runCatching { service.frameworkName }.getOrDefault("")
        val frameworkVersion = runCatching { service.frameworkVersion }.getOrDefault("")
        val scopePackages = runCatching { service.scope.toSet() }.getOrDefault(emptySet())
        val serviceReady = apiVersion == TARGET_API_VERSION && scopePackages.any { it in TARGET_PACKAGES }
        val remotePrefs = if (serviceReady) service.getRemotePreferences(REMOTE_PREF_GROUP) else null
        if (remotePrefs != null) syncRuntimeAccessToRemotePreferences(context, remotePrefs)
        val injectionEnabled = if (serviceReady && remotePrefs != null) {
            remotePrefs.getBoolean(KEY_INJECTION_ENABLED, true)
        } else false
        val autoPushEnabled = if (serviceReady && injectionEnabled && remotePrefs != null) {
            remotePrefs.getBoolean(KEY_AUTO_PUSH_ENABLED, true)
        } else false
        VideoShellInjectionState(
            loading = false,
            serviceConnected = true,
            apiVersion = apiVersion,
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            scopePackages = scopePackages,
            injectionEnabled = injectionEnabled,
            autoPushEnabled = autoPushEnabled,
            message = when {
                apiVersion != TARGET_API_VERSION -> "当前 LSPosed API $apiVersion，不是 API $TARGET_API_VERSION"
                !scopePackages.any { it in TARGET_PACKAGES } -> "LSPosed 作用域未勾选推荐影视壳"
                else -> "LSPosed API 101 和作用域正常"
            }
        )
    } catch (throwable: Throwable) {
        VideoShellInjectionState(
            loading = false,
            serviceConnected = true,
            message = throwable.message ?: "读取 LSPosed Service 失败"
        )
    }
}

private fun stateStatusSubtitle(state: VideoShellInjectionState): String {
    return when {
        state.loading -> "正在等待官方 XposedServiceHelper 回调"
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> "当前系统版本低于 libxposed Service 要求"
        !state.serviceConnected -> "未收到 LSPosed Service：模块未启用、LSPosed 未激活，或本 App 需重启"
        !state.apiSupported -> "已连接 Service，但 API 不是 $TARGET_API_VERSION"
        !state.scopeReady -> "已连接 Service，但模块作用域未勾选推荐影视壳"
        else -> "官方 Service 已连接，API 与作用域均满足"
    }
}

private fun scopeStatusSubtitle(state: VideoShellInjectionState): String {
    return when {
        state.loading -> "正在读取官方 getScope() 和 Remote Preferences"
        !state.serviceConnected -> "未连接 Service，无法读取作用域和远程配置"
        !state.scopeReady -> "推荐在 LSPosed 作用域中勾选正在使用的影视壳：${TARGET_PACKAGES.joinToString(" / ")}"
        else -> "已勾选推荐影视壳之一；开关状态来自 LSPosed Remote Preferences"
    }
}

private fun missingEnvironmentMessage(state: VideoShellInjectionState): String {
    return when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> "缺少环境：libxposed Service 需要 Android 8.0+"
        !state.serviceConnected -> "缺少环境：LSPosed Service 未连接，请在 LSPosed 启用模块并重启本 App"
        !state.apiSupported -> "缺少环境：当前 API ${state.apiVersion ?: "未知"}，需要 API $TARGET_API_VERSION"
        !state.scopeReady -> "缺少环境：LSPosed 作用域未勾选推荐影视壳"
        else -> "环境可用"
    }
}

private fun saveVideoShellInjectionSwitches(
    context: android.content.Context,
    injectionEnabled: Boolean,
    autoPushEnabled: Boolean
): SaveResult {
    val service = VideoShellXposedServiceRegistry.currentService()
        ?: return SaveResult(false, "缺少环境：LSPosed Service 未连接")
    return try {
        val apiVersion = service.apiVersion
        if (apiVersion != TARGET_API_VERSION) {
            return SaveResult(false, "缺少环境：当前 API $apiVersion，需要 API $TARGET_API_VERSION")
        }
        val scopeReady = service.scope.any { it in TARGET_PACKAGES }
        if (!scopeReady) {
            return SaveResult(false, "缺少环境：LSPosed 作用域未勾选推荐影视壳")
        }
        val remotePrefs = service.getRemotePreferences(REMOTE_PREF_GROUP)
        val access = resolveRuntimeAccess(context)
        val ok = remotePrefs
            .edit()
            .putBoolean(KEY_INJECTION_ENABLED, injectionEnabled)
            .putBoolean(KEY_AUTO_PUSH_ENABLED, autoPushEnabled && injectionEnabled)
            .putInt(KEY_CORE_PORT, access.port)
            .putString(KEY_CORE_TOKEN, access.runtimeToken)
            .commit()
        SaveResult(ok, if (ok) "已保存" else "保存 Remote Preferences 失败")
    } catch (throwable: Throwable) {
        SaveResult(false, throwable.message ?: "保存 Remote Preferences 失败")
    }
}

private fun resolveRuntimeAccess(context: android.content.Context) = RuntimeApiAccessResolver.resolve(
    context = context,
    prefs = context.getSharedPreferences(RUNTIME_PREF_GROUP, android.content.Context.MODE_PRIVATE),
    defaultPort = 9321
)

private fun syncRuntimeAccessToRemotePreferences(
    context: android.content.Context,
    remotePrefs: android.content.SharedPreferences
) {
    val access = resolveRuntimeAccess(context)
    remotePrefs.edit()
        .putInt(KEY_CORE_PORT, access.port)
        .putString(KEY_CORE_TOKEN, access.runtimeToken)
        .apply()
}

private data class SaveResult(
    val ok: Boolean,
    val message: String
)

private object VideoShellXposedServiceRegistry {
    @Volatile
    private var registered = false
    private val services = CopyOnWriteArraySet<XposedService>()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

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
        return services.maxByOrNull { service -> serviceScore(service) }
    }

    private fun serviceScore(service: XposedService): Int {
        var score = 0
        val apiVersion = runCatching { service.apiVersion }.getOrDefault(-1)
        if (apiVersion == TARGET_API_VERSION) score += 100
        val scopeReady = runCatching { service.scope.any { it in TARGET_PACKAGES } }.getOrDefault(false)
        if (scopeReady) score += 50
        val prefsReady = runCatching { service.getRemotePreferences(REMOTE_PREF_GROUP) }.isSuccess
        if (prefsReady) score += 10
        return score
    }

    private fun notifyListeners() {
        listeners.forEach { listener ->
            runCatching { listener() }
        }
    }
}
