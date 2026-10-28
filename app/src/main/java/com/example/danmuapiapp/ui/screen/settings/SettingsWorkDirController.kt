package com.example.danmuapiapp.ui.screen.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.common.parseEnvContentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页「工作目录」子控制器：目录切换、核心变体同步与目录信息展示。
 *
 * 切换事务仍由 CoreRepository 承担，这里只保留编排逻辑，行为与拆分前一致。
 */
internal class SettingsWorkDirController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val coreRepo: CoreRepository,
    private val runtimeRepo: RuntimeRepository,
    private val envConfigRepo: EnvConfigRepository,
    private val postMessage: (String) -> Unit
) {
    var workDirInfo by mutableStateOf(defaultWorkDirInfo())
        private set
    var isApplyingWorkDir by mutableStateOf(false)
        private set

    fun refreshWorkDirInfo() {
        scope.launch {
            val info = withContext(Dispatchers.IO) { loadWorkDirInfoSafe() }
            workDirInfo = info
        }
    }

    fun applyWorkDirPath(inputPath: String, migrateSelectedCore: Boolean = false) {
        val path = inputPath.trim().ifBlank { null }
        applyWorkDirInternal(
            targetPath = path,
            switchMode = if (migrateSelectedCore) {
                RuntimePaths.WorkDirSwitchMode.MigrateSelectedCore
            } else {
                RuntimePaths.WorkDirSwitchMode.SwitchOnly
            }
        )
    }

    fun restoreDefaultWorkDir() {
        applyWorkDirInternal(null, RuntimePaths.WorkDirSwitchMode.SwitchOnly)
    }

    fun applyWorkDirFromTreeUri(uri: Uri?) {
        if (uri == null) {
            postMessage("未选择目录")
            return
        }
        val resolvedPath = RuntimePaths.resolveTreeUriToPath(uri)
        if (resolvedPath.isNullOrBlank()) {
            postMessage("无法解析所选目录，请改用手动输入")
            return
        }
        applyWorkDirPath(resolvedPath)
    }

    private fun applyWorkDirInternal(
        targetPath: String?,
        switchMode: RuntimePaths.WorkDirSwitchMode
    ) {
        if (isApplyingWorkDir) return
        scope.launch {
            isApplyingWorkDir = true
            try {
                val result = coreRepo.applyWorkDirectoryChange(
                    targetPath = targetPath,
                    migrateSelectedCore = switchMode == RuntimePaths.WorkDirSwitchMode.MigrateSelectedCore
                ).fold(
                    onSuccess = { RuntimePaths.ApplyResult(ok = true, message = it) },
                    onFailure = {
                        RuntimePaths.ApplyResult(
                            ok = false,
                            message = it.message ?: "切换工作目录失败"
                        )
                    }
                )
                if (!result.ok) {
                    postMessage(result.message)
                    return@launch
                }
                val previousVariant = runtimeRepo.runtimeState.value.variant
                var resolvedVariant: ApiVariant? = null
                val storageHint = if (NormalModeRuntimeProfiles.current(context).slowStorageWorkDir) {
                    "共享存储目录启动会更慢，低端机建议优先使用默认目录"
                } else {
                    null
                }
                withContext(Dispatchers.IO) {
                    runCatching {
                        val projectDir = RuntimePaths.normalProjectDir(context)
                        resolvedVariant = syncRuntimeVariantFromEnv(projectDir)
                        if (NodeProjectManager.hasProjectEntry(projectDir)) {
                            NodeProjectManager.writeRuntimeEnv(context, projectDir)
                        }
                    }
                }
                coreRepo.refreshCoreInfo()
                envConfigRepo.reload()
                refreshWorkDirInfo()
                val selectedVariant = resolvedVariant
                val dependencyCheck = runCatching {
                    if (selectedVariant != null) {
                        coreRepo.prepareInstalledCoreDependencyRepair(selectedVariant)
                    } else {
                        null
                    }
                }
                val dependencyRepair = dependencyCheck.getOrNull()
                val dependencyCheckError = dependencyCheck.exceptionOrNull()
                val variantMessage = when {
                    selectedVariant == null -> "当前目录未检测到可用核心，请先下载核心"
                    selectedVariant != previousVariant -> "已自动切换核心为 ${selectedVariant.label}"
                    else -> null
                }
                if (dependencyCheckError != null) {
                    runtimeRepo.addLog(
                        LogLevel.Error,
                        "工作目录依赖检测失败：${dependencyCheckError.message ?: "未知错误"}"
                    )
                    postMessage(
                        buildString {
                            append(result.message)
                            append("，依赖检测失败，已跳过自动重启：")
                            append(dependencyCheckError.message ?: "未知错误")
                        }
                    )
                } else if (dependencyRepair != null) {
                    runtimeRepo.addLog(
                        LogLevel.Warn,
                        "工作目录已切换，但 ${selectedVariant?.label ?: "当前核心"} 缺少运行时依赖，等待修复"
                    )
                    postMessage(
                        buildString {
                            append(result.message)
                            if (!variantMessage.isNullOrBlank()) {
                                append("，")
                                append(variantMessage)
                            }
                            append("，检测到缺失依赖，请点击修复依赖")
                            if (!storageHint.isNullOrBlank()) {
                                append("。")
                                append(storageHint)
                            }
                        }
                    )
                } else if (runtimeRepo.runtimeState.value.status == ServiceStatus.Running && selectedVariant != null) {
                    if (selectedVariant != previousVariant) {
                        runtimeRepo.addLog(LogLevel.Info, "已根据新目录自动切换核心到 ${selectedVariant.label}")
                    }
                    runtimeRepo.addLog(LogLevel.Info, "工作目录已变更，正在重启服务应用新目录")
                    runtimeRepo.restartService()
                    postMessage(
                        buildString {
                            append(result.message)
                            if (!variantMessage.isNullOrBlank()) {
                                append("，")
                                append(variantMessage)
                            }
                            append("，服务正在重启，请稍候")
                            if (!storageHint.isNullOrBlank()) {
                                append("。")
                                append(storageHint)
                            }
                        }
                    )
                } else {
                    if (runtimeRepo.runtimeState.value.status == ServiceStatus.Running && selectedVariant == null) {
                        runtimeRepo.addLog(LogLevel.Warn, "工作目录已切换，但新目录没有可用核心，正在停止旧目录服务")
                        runtimeRepo.stopService()
                    }
                    postMessage(
                        buildString {
                            append(result.message)
                            if (!variantMessage.isNullOrBlank()) {
                                append("，")
                                append(variantMessage)
                            }
                            if (!storageHint.isNullOrBlank()) {
                                append("。")
                                append(storageHint)
                            }
                        }
                    )
                }
            } finally {
                isApplyingWorkDir = false
            }
        }
    }

    private fun syncRuntimeVariantFromEnv(projectDir: java.io.File): ApiVariant? {
        val installedVariants = ApiVariant.entries.filter { variant ->
            NodeProjectManager.hasValidCore(java.io.File(projectDir, "danmu_api_${variant.key}"))
        }
        if (installedVariants.isEmpty()) return null

        val envFile = java.io.File(projectDir, "config/.env")
        val preferredVariant = if (envFile.exists() && envFile.isFile) {
            val text = runCatching { envFile.readText(Charsets.UTF_8) }.getOrDefault("")
            val env = parseEnvContentMap(text)
            val rawVariant = env["DANMU_API_VARIANT"]?.trim()?.lowercase().orEmpty()
            ApiVariant.entries.firstOrNull { it.key == rawVariant }
        } else {
            null
        }

        val currentVariant = runtimeRepo.runtimeState.value.variant
        val resolvedVariant = when {
            preferredVariant != null && installedVariants.contains(preferredVariant) -> preferredVariant
            installedVariants.contains(currentVariant) -> currentVariant
            installedVariants.contains(ApiVariant.Stable) -> ApiVariant.Stable
            else -> installedVariants.first()
        }

        runtimeRepo.updateVariant(resolvedVariant)
        return resolvedVariant
    }

    private fun loadWorkDirInfoSafe(): RuntimePaths.WorkDirInfo {
        return runCatching { RuntimePaths.buildWorkDirInfo(context) }
            .getOrElse {
                defaultWorkDirInfo()
            }
    }

    private fun defaultWorkDirInfo(): RuntimePaths.WorkDirInfo {
        val runMode = runtimeRepo.runtimeState.value.runMode
        val defaultBase = RuntimePaths.defaultBaseDir(context)
        val rootBase = RuntimePaths.rootBaseDir(context)
        val normalBase = defaultBase
        return RuntimePaths.WorkDirInfo(
            runMode = runMode,
            currentBaseDir = if (runMode != RunMode.Normal) rootBase else normalBase,
            normalBaseDir = normalBase,
            defaultBaseDir = defaultBase,
            customBaseDir = null,
            rootBaseDir = rootBase,
            isCustomEnabled = false
        )
    }
}
