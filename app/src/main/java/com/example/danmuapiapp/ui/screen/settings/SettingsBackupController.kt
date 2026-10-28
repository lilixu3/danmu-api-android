package com.example.danmuapiapp.ui.screen.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.AppBackupPreview
import com.example.danmuapiapp.data.service.AppBackupSection
import com.example.danmuapiapp.data.service.AppBackupService
import com.example.danmuapiapp.data.service.BackupEnvironmentPolicy
import com.example.danmuapiapp.data.service.FavoriteCacheStore
import com.example.danmuapiapp.data.service.SystemHeartbeatScheduler
import com.example.danmuapiapp.data.service.WebDavService
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.WebDavConfig
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.parseEnvContentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页「备份与恢复」子控制器：.env / 收藏 / 完整备份导入导出与 WebDAV 云同步。
 *
 * 事务与回滚语义与拆分前完全一致，仅把状态与协程从 ViewModel 中搬出。
 */
internal class SettingsBackupController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val envConfigRepo: EnvConfigRepository,
    private val appBackupService: AppBackupService,
    private val webDavService: WebDavService,
    private val settingsRepo: SettingsRepository,
    private val coreRepo: CoreRepository,
    private val runtimeRepo: RuntimeRepository,
    private val postMessage: (String) -> Unit
) {
    var isFullBackupOperating by mutableStateOf(false)
        private set
    var isWebDavOperating by mutableStateOf(false)
        private set
    var webDavOperatingText by mutableStateOf("")
        private set
    var showWebDavConfigDialog by mutableStateOf(false)
        private set
    var webDavUrlInput by mutableStateOf("")
        private set
    var webDavUserInput by mutableStateOf("")
        private set
    var webDavPassInput by mutableStateOf("")
        private set
    var webDavPathInput by mutableStateOf("")
        private set

    fun envFilePath(): String = envConfigRepo.getEnvFilePath()

    fun buildExportFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu_api_$ts.env"
    }

    suspend fun exportEnvContent(): Result<String> {
        return withContext(Dispatchers.IO) {
            envConfigRepo.readCurrentRawContent()
                .map { it.ifBlank { "# DanmuApiApp .env\n" } }
        }
    }

    fun importEnvContent(content: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                envConfigRepo.saveRawContent(content)
            }
            result.fold(
                onSuccess = {
                    applyRuntimeFromEnv(content)
                    postMessage("导入成功，已覆盖当前 .env，建议重启服务")
                    runtimeRepo.addLog(LogLevel.Info, "已导入 .env 配置，建议重启服务")
                },
                onFailure = {
                    postMessage("导入失败：${it.message ?: "写入 .env 失败"}")
                    runtimeRepo.addLog(LogLevel.Error, "导入 .env 配置失败：${it.message ?: "写入失败"}")
                }
            )
        }
    }

    fun buildFavoriteExportFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu_api_favorites_$ts.json"
    }

    fun buildFullBackupFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu_api_app_backup_$ts.json"
    }

    suspend fun createFullBackup(sections: Set<AppBackupSection>): Result<String> {
        if (isFullBackupOperating) return Result.failure(IllegalStateException("备份任务正在进行"))
        isFullBackupOperating = true
        return try {
            withContext(Dispatchers.IO) {
                appBackupService.createBackup(sections)
            }
        } finally {
            isFullBackupOperating = false
        }
    }

    fun inspectFullBackup(content: String): Result<AppBackupPreview> = appBackupService.inspect(content)

    fun restoreFullBackup(content: String, sections: Set<AppBackupSection>) {
        if (isFullBackupOperating) return
        scope.launch {
            isFullBackupOperating = true
            try {
                val wasRunning = runtimeRepo.runtimeState.value.status == ServiceStatus.Running
                val result = withContext(Dispatchers.IO) {
                    appBackupService.restore(
                        raw = content,
                        selectedSections = sections
                    )
                }
                result.fold(
                    onSuccess = { restored ->
                        envConfigRepo.reload()
                        restored.mergedEnvironment?.let(::applyRuntimeFromEnv)
                        settingsRepo.reloadFromStorage()
                        SystemHeartbeatScheduler.refresh(context)
                        coreRepo.refreshCoreInfo()
                        if (wasRunning) runtimeRepo.restartService()
                        val favoriteText = restored.favoriteCount?.let { "，$it 项收藏" }.orEmpty()
                        postMessage(
                            "完整备份恢复成功$favoriteText" +
                                if (wasRunning) "，服务正在重启" else "；部分界面设置将在重启 App 后完全生效"
                        )
                        runtimeRepo.addLog(LogLevel.Info, "已恢复完整备份：${restored.restoredSections.joinToString()}")
                    },
                    onFailure = { error ->
                        postMessage("完整备份恢复失败：${error.message ?: "文件无效"}")
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                postMessage("完整备份恢复失败：${error.message ?: "无法读取当前配置"}")
            } finally {
                isFullBackupOperating = false
            }
        }
    }

    suspend fun exportFavoriteContent(): Result<FavoriteCacheStore.Snapshot> {
        return withContext(Dispatchers.IO) { FavoriteCacheStore.readCurrent(context) }
    }

    fun importFavoriteContent(content: String) {
        scope.launch {
            val wasRunning = runtimeRepo.runtimeState.value.status == ServiceStatus.Running
            val result = withContext(Dispatchers.IO) {
                FavoriteCacheStore.writeCurrent(context, content)
            }
            result.fold(
                onSuccess = { snapshot ->
                    if (wasRunning) runtimeRepo.restartService()
                    postMessage(
                        if (wasRunning) {
                            "已导入 ${snapshot.count} 项收藏，服务正在重启"
                        } else {
                            "已导入 ${snapshot.count} 项收藏，下次启动服务时生效"
                        }
                    )
                    runtimeRepo.addLog(LogLevel.Info, "已导入 ${snapshot.count} 项收藏数据")
                },
                onFailure = { error ->
                    postMessage("收藏导入失败：${error.message ?: "文件格式无效"}")
                    runtimeRepo.addLog(LogLevel.Error, "收藏导入失败：${error.message ?: "文件格式无效"}")
                }
            )
        }
    }

    fun openWebDavConfigDialog() {
        val config = webDavService.loadConfig()
        webDavUrlInput = config.url
        webDavUserInput = config.username
        webDavPassInput = config.password
        webDavPathInput = config.folderPath
        showWebDavConfigDialog = true
    }

    fun dismissWebDavConfigDialog() {
        showWebDavConfigDialog = false
    }

    fun updateWebDavUrl(value: String) {
        webDavUrlInput = value
    }

    fun updateWebDavUser(value: String) {
        webDavUserInput = value
    }

    fun updateWebDavPass(value: String) {
        webDavPassInput = value
    }

    fun updateWebDavPath(value: String) {
        webDavPathInput = value
    }

    fun saveWebDavConfig() {
        val config = WebDavConfig(
            url = webDavUrlInput.trim(),
            username = webDavUserInput.trim(),
            password = webDavPassInput,
            folderPath = webDavPathInput.trim()
        )
        webDavService.saveConfig(config)
        showWebDavConfigDialog = false
        postMessage("WebDAV 设置已保存")
    }

    fun webDavSummary(): String {
        val config = webDavService.loadConfig()
        if (!webDavService.isConfigured(config)) return "未配置"
        val host = config.url.trim().ifBlank { "-" }
        val folder = config.folderPath.trim().ifBlank { "DanmuApi" }
        return "$host  /  $folder"
    }

    fun backupToWebDav() {
        if (isWebDavOperating) return
        scope.launch {
            val config = webDavService.loadConfig()
            if (!webDavService.isConfigured(config)) {
                postMessage("请先配置 WebDAV 账户")
                openWebDavConfigDialog()
                return@launch
            }
            isWebDavOperating = true
            webDavOperatingText = "正在上传安全完整备份到 WebDAV..."
            try {
                val fullBundle = withContext(Dispatchers.IO) {
                    appBackupService.createBackup(AppBackupSection.entries.toSet())
                }
                if (fullBundle.isFailure) {
                    postMessage("WebDAV 备份失败：${fullBundle.exceptionOrNull()?.message}")
                    return@launch
                }
                val bundleUpload = webDavService.backupAppBundle(fullBundle.getOrThrow())
                if (bundleUpload.isFailure) {
                    postMessage("WebDAV 备份失败：${bundleUpload.exceptionOrNull()?.message}")
                    return@launch
                }
                postMessage("WebDAV 完整备份成功，凭据未上传")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                postMessage("WebDAV 备份失败：${error.message ?: "未知错误"}")
            } finally {
                isWebDavOperating = false
                webDavOperatingText = ""
            }
        }
    }

    fun restoreFromWebDav() {
        if (isWebDavOperating) return
        scope.launch {
            val config = webDavService.loadConfig()
            if (!webDavService.isConfigured(config)) {
                postMessage("请先配置 WebDAV 账户")
                openWebDavConfigDialog()
                return@launch
            }
            isWebDavOperating = true
            webDavOperatingText = "正在从 WebDAV 下载完整备份..."
            try {
                val bundleResult = webDavService.restoreAppBundle()
                if (bundleResult.isFailure) {
                    postMessage("WebDAV 恢复失败：${bundleResult.exceptionOrNull()?.message}")
                    return@launch
                }
                val fullBundle = bundleResult.getOrNull()
                if (fullBundle != null) {
                    val preview = withContext(Dispatchers.Default) {
                        appBackupService.inspect(fullBundle)
                    }
                    val sections = preview.getOrElse { error ->
                        postMessage("云端完整备份无效：${error.message}")
                        return@launch
                    }.sections
                    restoreFullBackup(fullBundle, sections)
                    return@launch
                }
                val envResult = webDavService.restoreEnv()
                val favoriteResult = if (envResult.isSuccess) {
                    webDavService.restoreFavorites()
                } else {
                    Result.success(null)
                }
                when {
                    envResult.isFailure -> {
                        postMessage("WebDAV 恢复失败：${envResult.exceptionOrNull()?.message}")
                    }
                    favoriteResult.isFailure -> {
                        postMessage("WebDAV 恢复失败：${favoriteResult.exceptionOrNull()?.message}")
                    }
                    else -> {
                        restoreLegacyWebDavBackup(
                            envContent = envResult.getOrThrow(),
                            favoriteContent = favoriteResult.getOrNull()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                postMessage("WebDAV 恢复失败：${error.message ?: "未知错误"}")
            } finally {
                isWebDavOperating = false
                webDavOperatingText = ""
            }
        }
    }

    private suspend fun restoreLegacyWebDavBackup(
        envContent: String,
        favoriteContent: String?
    ) {
        val validatedFavorite = favoriteContent?.let { FavoriteCacheStore.snapshotOf(it) }
        val previousEnv = withContext(Dispatchers.IO) {
            envConfigRepo.readCurrentRawContent().getOrThrow()
        }
        val safeEnvContent = BackupEnvironmentPolicy.merge(
            current = previousEnv,
            restored = BackupEnvironmentPolicy.exportValues(envContent)
        )
        val previousFavorite = if (validatedFavorite != null) {
            withContext(Dispatchers.IO) { FavoriteCacheStore.readCurrent(context).getOrThrow() }
        } else null

        try {
            withContext(Dispatchers.IO) {
                envConfigRepo.saveRawContent(safeEnvContent).getOrThrow()
            }
            val restoredFavorite = validatedFavorite?.let { snapshot ->
                withContext(Dispatchers.IO) {
                    FavoriteCacheStore.writeCurrent(context, snapshot.content).getOrThrow()
                }
            }
            applyRuntimeFromEnv(safeEnvContent)
            val wasRunning = runtimeRepo.runtimeState.value.status == ServiceStatus.Running
            if (wasRunning) runtimeRepo.restartService()
            val favoriteSummary = restoredFavorite?.let { "，恢复 ${it.count} 项收藏" }
                ?: "，云端无收藏备份，已保留本地收藏"
            postMessage(
                if (wasRunning) {
                    "WebDAV 恢复成功$favoriteSummary，服务正在重启"
                } else {
                    "WebDAV 恢复成功$favoriteSummary"
                }
            )
            runtimeRepo.addLog(LogLevel.Info, "已从 WebDAV 恢复配置与收藏")
        } catch (error: Throwable) {
            val rollbackErrors = mutableListOf<Throwable>()
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { envConfigRepo.saveRawContent(previousEnv).getOrThrow() }
                    .exceptionOrNull()?.let(rollbackErrors::add)
                if (previousFavorite != null) {
                    runCatching {
                        FavoriteCacheStore.writeCurrent(context, previousFavorite.content).getOrThrow()
                    }.exceptionOrNull()?.let(rollbackErrors::add)
                }
            }
            rollbackErrors.forEach(error::addSuppressed)
            throw error
        }
    }

    private fun applyRuntimeFromEnv(content: String) {
        val env = parseEnvContentMap(content)

        val current = runtimeRepo.runtimeState.value
        val port = env["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: current.port
        val listenMode = RuntimeListenMode.fromBindHost(env[RuntimeListenMode.ENV_KEY])
            ?: current.listenMode
        runtimeRepo.applyServiceConfig(
            port = port,
            token = RuntimeTokenNormalizer.normalizeInput(env["TOKEN"]),
            restartIfRunning = false,
            listenMode = listenMode
        )
        env["DANMU_API_VARIANT"]?.lowercase()?.let { raw ->
            ApiVariant.entries.firstOrNull { it.key == raw }?.let { runtimeRepo.updateVariant(it) }
        }
        settingsRepo.setFileLogEnabled(false)
    }
}
