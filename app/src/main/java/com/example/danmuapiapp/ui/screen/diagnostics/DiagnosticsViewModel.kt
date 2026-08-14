package com.example.danmuapiapp.ui.screen.diagnostics

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.data.service.GithubAccountService
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.SensitiveDataRedactor
import com.example.danmuapiapp.domain.model.CacheClearSupport
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CacheRepository
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DiagnosticLevel { Good, Info, Warning, Error }

data class DiagnosticCheck(
    val title: String,
    val detail: String,
    val level: DiagnosticLevel
)

data class DiagnosticsUiState(
    val isRunning: Boolean = false,
    val generatedAtMs: Long? = null,
    val checks: List<DiagnosticCheck> = emptyList(),
    val report: String = ""
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeRepository: RuntimeRepository,
    private val coreRepository: CoreRepository,
    private val cacheRepository: CacheRepository,
    private val githubAccountService: GithubAccountService
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        runDiagnostics()
    }

    fun runDiagnostics() {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRunning = true)
            val result = withContext(Dispatchers.IO) {
                val cacheRefresh = async { runCatching { cacheRepository.refresh() } }
                val githubRefresh = async { runCatching { githubAccountService.refresh() } }
                runtimeRepository.refreshRuntimeState()
                coreRepository.refreshCoreInfo()
                cacheRefresh.await()
                githubRefresh.await()
                delay(350L)
                buildSnapshot()
            }
            _uiState.value = result.copy(isRunning = false)
        }
    }

    private fun buildSnapshot(): DiagnosticsUiState {
        val now = System.currentTimeMillis()
        val runtime = runtimeRepository.runtimeState.value
        val selectedCore = coreRepository.coreInfoList.value.firstOrNull { it.variant == runtime.variant }
        val candidate = coreRepository.candidateState.value
        val cache = cacheRepository.cacheStats.value
        val cacheCapability = cacheRepository.clearCapability.value
        val github = githubAccountService.status.value
        val projectDir = RuntimePaths.normalProjectDir(context)
        val freeMb = projectDir.parentFile?.usableSpace?.div(1024L * 1024L) ?: 0L
        val portReachable = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", runtime.port), 900)
            }
            true
        }.getOrDefault(false)

        val checks = buildList {
            add(
                DiagnosticCheck(
                    title = "运行状态",
                    detail = when {
                        runtime.status == ServiceStatus.Running && portReachable ->
                            "服务运行中，本机端口 ${runtime.port} 可达"
                        runtime.status == ServiceStatus.Running ->
                            "界面显示运行中，但本机端口 ${runtime.port} 暂不可达"
                        runtime.status == ServiceStatus.Error ->
                            runtime.errorMessage ?: "服务处于异常状态"
                        else -> "服务${runtime.status.displayText()}，未执行端口可达性要求"
                    },
                    level = when {
                        runtime.status == ServiceStatus.Running && portReachable -> DiagnosticLevel.Good
                        runtime.status == ServiceStatus.Error -> DiagnosticLevel.Error
                        runtime.status == ServiceStatus.Running -> DiagnosticLevel.Warning
                        else -> DiagnosticLevel.Info
                    }
                )
            )
            add(
                DiagnosticCheck(
                    title = "当前核心",
                    detail = when {
                        selectedCore?.sourceMismatch == true -> "${runtime.variant.label}来源与当前配置不一致"
                        selectedCore?.isInstalled == true ->
                            "${runtime.variant.label} ${selectedCore.version?.let { "v$it" } ?: "版本未知"}，文件完整"
                        else -> "${runtime.variant.label}未安装或文件不完整"
                    },
                    level = when {
                        selectedCore?.sourceMismatch == true -> DiagnosticLevel.Warning
                        selectedCore?.isInstalled == true -> DiagnosticLevel.Good
                        else -> DiagnosticLevel.Error
                    }
                )
            )
            if (candidate != null) {
                add(
                    DiagnosticCheck(
                        title = "核心启动观察",
                        detail = "${candidate.variant.label}${candidate.actionLabel}后等待稳定确认" +
                            if (candidate.hasRecoveryPoint) "，可自动恢复" else "，这是首次安装且没有旧版本",
                        level = DiagnosticLevel.Info
                    )
                )
            }
            add(
                DiagnosticCheck(
                    title = "工作目录",
                    detail = "目录${if (projectDir.isDirectory) "可读取" else "尚未创建"}，可用空间 ${freeMb} MB",
                    level = when {
                        projectDir.isDirectory && projectDir.canRead() && projectDir.canWrite() && freeMb >= 100 -> DiagnosticLevel.Good
                        freeMb < 100 -> DiagnosticLevel.Warning
                        else -> DiagnosticLevel.Info
                    }
                )
            )
            add(
                DiagnosticCheck(
                    title = "缓存接口",
                    detail = when {
                        cache.isAvailable && cacheCapability.support == CacheClearSupport.Selective ->
                            "缓存数据可读取，支持 8 项选择清理"
                        cache.isAvailable -> "缓存数据可读取，当前核心仅支持全部清理"
                        runtime.status != ServiceStatus.Running -> "服务未运行，暂未检查缓存接口"
                        else -> "缓存接口暂不可用"
                    },
                    level = when {
                        cache.isAvailable -> DiagnosticLevel.Good
                        runtime.status == ServiceStatus.Running -> DiagnosticLevel.Warning
                        else -> DiagnosticLevel.Info
                    }
                )
            )
            add(
                DiagnosticCheck(
                    title = "GitHub API",
                    detail = buildString {
                        append(if (github.tokenConfigured) {
                            when (github.tokenValid) {
                                true -> "Token 已验证"
                                false -> "Token 无效"
                                null -> "Token 状态暂未确认"
                            }
                        } else "匿名访问")
                        if (github.coreRemaining != null && github.coreLimit != null) {
                            append("，本小时剩余 ${github.coreRemaining}/${github.coreLimit}")
                        }
                        github.error?.lineSequence()?.firstOrNull()?.let { append("；$it") }
                    },
                    level = when {
                        github.tokenValid == false -> DiagnosticLevel.Error
                        github.coreRemaining != null && github.coreRemaining <= 5 -> DiagnosticLevel.Warning
                        github.error != null && github.coreRemaining == null -> DiagnosticLevel.Warning
                        else -> DiagnosticLevel.Good
                    }
                )
            )
            val recentErrors = runtimeRepository.logs.value.takeLast(100).filter { it.level == LogLevel.Error }
            add(
                DiagnosticCheck(
                    title = "近期错误",
                    detail = if (recentErrors.isEmpty()) "最近 100 条日志中没有错误" else "发现 ${recentErrors.size} 条错误，已写入脱敏报告",
                    level = if (recentErrors.isEmpty()) DiagnosticLevel.Good else DiagnosticLevel.Warning
                )
            )
        }

        return DiagnosticsUiState(
            generatedAtMs = now,
            checks = checks,
            report = buildReport(now, checks)
        )
    }

    private fun buildReport(now: Long, checks: List<DiagnosticCheck>): String {
        val runtime = runtimeRepository.runtimeState.value
        val recentProblems = runtimeRepository.logs.value.takeLast(100)
            .filter { it.level != LogLevel.Info }
            .takeLast(20)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))
        val raw = buildString {
            appendLine("弹幕 App 脱敏诊断报告")
            appendLine("生成时间: $time")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("运行模式: ${runtime.runMode.label}")
            appendLine("监听模式: ${runtime.listenMode.label}")
            appendLine()
            appendLine("检查结果")
            checks.forEach { appendLine("[${it.level.name}] ${it.title}: ${it.detail}") }
            if (recentProblems.isNotEmpty()) {
                appendLine()
                appendLine("近期警告与错误")
                recentProblems.forEach { log -> appendLine("[${log.level}] ${log.message}") }
            }
        }
        return SensitiveDataRedactor.redact(raw, redactNetworkAddresses = true).trimEnd() + "\n"
    }

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu-api-diagnostics-$stamp.txt"
    }
}

private fun ServiceStatus.displayText(): String = when (this) {
    ServiceStatus.Stopped -> "已停止"
    ServiceStatus.Starting -> "正在启动"
    ServiceStatus.Running -> "正在运行"
    ServiceStatus.Stopping -> "正在停止"
    ServiceStatus.Error -> "异常"
}
