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
private const val MIN_SUPPORTED_API_VERSION = 101
private const val API_102_VERSION = 102
private val TARGET_PACKAGES = setOf("com.fongmi.android.tv", "com.github.tvbox.osc")

private data class VideoShellInjectionState(
    val loading: Boolean = true,
    val serviceConnected: Boolean = false,
    val apiVersion: Int? = null,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val scopePackages: Set<String> = emptySet(),
    val remotePreferencesAvailable: Boolean = false,
    val remotePreferencesError: String = "",
    val api102FeaturesAvailable: Boolean = false,
    val api102FeatureMessage: String = "",
    val injectionEnabled: Boolean = false,
    val autoPushEnabled: Boolean = false,
    val message: String = "正在检查 LSPosed Service"
) {
    val apiSupported: Boolean get() = (apiVersion ?: 0) >= MIN_SUPPORTED_API_VERSION
    val scopeReady: Boolean get() = scopePackages.any { it in TARGET_PACKAGES }
    val environmentReady: Boolean get() = serviceConnected && apiSupported && scopeReady && remotePreferencesAvailable
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
                        else -> "当前 API ${state.apiVersion ?: "未知"}，最低需要 API $MIN_SUPPORTED_API_VERSION"
                    },
                    icon = Icons.Rounded.Movie,
                    trailing = {
                        StatusChip(
                            when {
                                state.loading -> "检查中"
                                state.apiSupported -> "API ${state.apiVersion}"
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
                    trailing = {
                        StatusChip(
                            when {
                                state.loading -> "检查中"
                                !state.serviceConnected -> "未连接"
                                !state.scopeReady -> "未勾选"
                                state.remotePreferencesAvailable -> "配置可用"
                                else -> "配置不可用"
                            }
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    title = "API 102 扩展能力",
                    subtitle = api102StatusSubtitle(state),
                    icon = Icons.Rounded.Autorenew,
                    trailing = {
                        StatusChip(
                            when {
                                state.loading -> "检查中"
                                !state.serviceConnected -> "未连接"
                                (state.apiVersion ?: 0) < API_102_VERSION -> "101 兼容"
                                state.api102FeaturesAvailable -> "102 可用"
                                else -> "受限"
                            }
                        )
                    }
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
                        if (injectionChecked) "LSPosed API 101+ / 作用域 / Remote Preferences 正常，播放页注入处于开启状态" else "已关闭：播放页不会插入 APP弹幕按钮"
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
        val apiSupported = apiVersion >= MIN_SUPPORTED_API_VERSION
        val scopeReady = scopePackages.any { it in TARGET_PACKAGES }
        val remotePreferencesCapable = hasRemotePreferencesCapability(service)
        val remotePrefsResult = if (apiSupported && scopeReady && remotePreferencesCapable) {
            runCatching { service.getRemotePreferences(REMOTE_PREF_GROUP) }
        } else null
        val remotePrefs = remotePrefsResult?.getOrNull()
        val remotePrefsError = when {
            apiSupported && scopeReady && !remotePreferencesCapable -> "框架未声明 Remote Preferences 能力"
            else -> remotePrefsResult?.exceptionOrNull()?.message.orEmpty()
        }
        val api102Probe = probeApi102Features(service, apiVersion)
        val injectionEnabled: Boolean
        val autoPushEnabled: Boolean
        if (apiSupported && scopeReady && remotePrefs != null) {
            syncRuntimeAccessToRemotePreferences(context, remotePrefs)
            injectionEnabled = remotePrefs.getBoolean(KEY_INJECTION_ENABLED, true)
            autoPushEnabled = if (injectionEnabled) {
                remotePrefs.getBoolean(KEY_AUTO_PUSH_ENABLED, true)
            } else false
        } else {
            injectionEnabled = false
            autoPushEnabled = false
        }
        VideoShellInjectionState(
            loading = false,
            serviceConnected = true,
            apiVersion = apiVersion,
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            scopePackages = scopePackages,
            remotePreferencesAvailable = remotePrefs != null,
            remotePreferencesError = remotePrefsError,
            api102FeaturesAvailable = api102Probe.available,
            api102FeatureMessage = api102Probe.message,
            injectionEnabled = injectionEnabled,
            autoPushEnabled = autoPushEnabled,
            message = when {
                !apiSupported -> "当前 LSPosed API $apiVersion，低于最低 API $MIN_SUPPORTED_API_VERSION"
                !scopeReady -> "LSPosed 作用域未勾选推荐影视壳"
                remotePrefs == null -> remotePreferencesUnavailableText(remotePrefsError)
                else -> "LSPosed API $apiVersion、作用域和 Remote Preferences 正常"
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
        !state.apiSupported -> "已连接 Service，但 API 低于 $MIN_SUPPORTED_API_VERSION"
        !state.scopeReady -> "已连接 Service，但模块作用域未勾选推荐影视壳"
        !state.remotePreferencesAvailable -> "已连接 Service，但 ${remotePreferencesUnavailableText(state.remotePreferencesError)}"
        else -> "官方 Service 已连接，API、作用域与 Remote Preferences 均满足"
    }
}

private fun scopeStatusSubtitle(state: VideoShellInjectionState): String {
    return when {
        state.loading -> "正在读取官方 getScope() 和 Remote Preferences"
        !state.serviceConnected -> "未连接 Service，无法读取作用域和远程配置"
        !state.apiSupported -> "当前 API 低于 $MIN_SUPPORTED_API_VERSION，无法使用本注入所需的现代 Remote Preferences"
        !state.scopeReady -> "推荐在 LSPosed 作用域中勾选正在使用的影视壳：${TARGET_PACKAGES.joinToString(" / ")}"
        !state.remotePreferencesAvailable -> "作用域已勾选，但 ${remotePreferencesUnavailableText(state.remotePreferencesError)}"
        else -> "已勾选推荐影视壳之一；开关状态来自 LSPosed Remote Preferences"
    }
}

private fun api102StatusSubtitle(state: VideoShellInjectionState): String {
    return when {
        state.loading -> "正在检测 API 102 运行目标 / 热重载扩展"
        !state.serviceConnected -> "未连接 Service，无法检测 API 102 扩展"
        (state.apiVersion ?: 0) < API_102_VERSION -> "当前 API ${state.apiVersion ?: "未知"}：保持 API 101 兼容模式，不调用 102-only 接口"
        else -> state.api102FeatureMessage.ifBlank { "API 102 扩展状态未知" }
    }
}

private fun missingEnvironmentMessage(state: VideoShellInjectionState): String {
    return when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O -> "缺少环境：libxposed Service 需要 Android 8.0+"
        !state.serviceConnected -> "缺少环境：LSPosed Service 未连接，请在 LSPosed 启用模块并重启本 App"
        !state.apiSupported -> "缺少环境：当前 API ${state.apiVersion ?: "未知"}，最低需要 API $MIN_SUPPORTED_API_VERSION"
        !state.scopeReady -> "缺少环境：LSPosed 作用域未勾选推荐影视壳"
        !state.remotePreferencesAvailable -> "缺少环境：${remotePreferencesUnavailableText(state.remotePreferencesError)}"
        else -> "环境可用"
    }
}

private fun remotePreferencesUnavailableText(error: String): String {
    return if (error.isBlank()) {
        "Remote Preferences 不可用"
    } else {
        "Remote Preferences 不可用：$error"
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
        if (apiVersion < MIN_SUPPORTED_API_VERSION) {
            return SaveResult(false, "缺少环境：当前 API $apiVersion，最低需要 API $MIN_SUPPORTED_API_VERSION")
        }
        val scopeReady = service.scope.any { it in TARGET_PACKAGES }
        if (!scopeReady) {
            return SaveResult(false, "缺少环境：LSPosed 作用域未勾选推荐影视壳")
        }
        if (!hasRemotePreferencesCapability(service)) {
            return SaveResult(false, "缺少环境：${remotePreferencesUnavailableText("框架未声明 Remote Preferences 能力")}")
        }
        val remotePrefs = try {
            service.getRemotePreferences(REMOTE_PREF_GROUP)
        } catch (throwable: Throwable) {
            return SaveResult(false, "缺少环境：${remotePreferencesUnavailableText(throwable.message.orEmpty())}")
        }
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

private fun hasRemotePreferencesCapability(service: XposedService): Boolean {
    return runCatching {
        (service.frameworkProperties and XposedService.PROP_CAP_REMOTE) != 0L
    }.getOrDefault(false)
}

private fun probeApi102Features(service: XposedService, apiVersion: Int): Api102Probe {
    if (apiVersion < API_102_VERSION) {
        return Api102Probe(false, "当前 API $apiVersion：保持 API 101 兼容模式")
    }
    return runCatching {
        val targets = service.runningTargets
        Api102Probe(
            available = true,
            message = "API 102 扩展可用；当前运行目标 ${targets.size} 个，支持 App 侧运行目标检测/热重载触发"
        )
    }.getOrElse { throwable ->
        Api102Probe(false, "API 102 扩展调用失败：${throwable.message ?: throwable.javaClass.simpleName}")
    }
}

private data class Api102Probe(
    val available: Boolean,
    val message: String
)

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
        if (apiVersion >= MIN_SUPPORTED_API_VERSION) {
            score += 1_000 + apiVersion.coerceAtMost(999)
        } else if (apiVersion > 0) {
            score += apiVersion
        }
        val scopeReady = runCatching { service.scope.any { it in TARGET_PACKAGES } }.getOrDefault(false)
        if (scopeReady) score += 200
        val prefsReady = apiVersion >= MIN_SUPPORTED_API_VERSION && scopeReady && hasRemotePreferencesCapability(service) &&
            runCatching { service.getRemotePreferences(REMOTE_PREF_GROUP) }.isSuccess
        if (prefsReady) score += 50
        return score
    }

    private fun notifyListeners() {
        listeners.forEach { listener ->
            runCatching { listener() }
        }
    }
}
