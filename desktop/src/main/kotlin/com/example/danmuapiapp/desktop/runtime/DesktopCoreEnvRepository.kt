package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.core.CORE_ENV_HOST_KEYS
import com.example.danmuapiapp.desktop.core.CoreEnvDefinition
import com.example.danmuapiapp.desktop.core.CoreEnvSnapshot
import com.example.danmuapiapp.desktop.core.CoreEnvType
import com.example.danmuapiapp.desktop.core.CoreEnvValue
import com.example.danmuapiapp.desktop.core.CoreEnvValueSource
import com.example.danmuapiapp.desktop.core.CoreEnvCatalog
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import java.io.File

/** Reads and writes the current core's shared config/.env without copying values into DesktopSettings. */
class DesktopCoreEnvRepository(
    private val scriptDir: File,
    private val systemEnvironment: Map<String, String> = System.getenv(),
) {
    fun readSnapshot(variant: DesktopCoreVariant): CoreEnvSnapshot {
        require(scriptDir.isDirectory) { "核心运行目录不存在：${scriptDir.absolutePath}" }
        val coreDir = File(scriptDir, "danmu_api_${variant.key}")
        require(coreDir.isDirectory) { "当前核心变体尚未安装：${coreDir.absolutePath}" }
        require(File(coreDir, "worker.js").isFile) { "当前核心文件不完整，缺少 worker.js：${coreDir.absolutePath}" }
        val envsJsFile = File(coreDir, "configs/envs.js")
        val definitions = CoreEnvCatalog.parseFile(envsJsFile)
        require(definitions.isNotEmpty()) { "当前核心没有可编辑的环境变量定义：${envsJsFile.absolutePath}" }
        require(definitions.none { it.key in CORE_ENV_HOST_KEYS }) { "核心定义包含 Desktop 宿主变量，拒绝加载" }

        val envFile = File(scriptDir, "config/.env")
        val rawContent = if (envFile.isFile) envFile.readText(Charsets.UTF_8) else ""
        val dotEnvValues = DesktopRuntimeEnv.readValues(envFile)
        val values = definitions.associate { definition ->
            val systemValue = systemEnvironment[definition.key]
            val configuredValue = dotEnvValues[definition.key]
            val source = when {
                systemValue != null -> CoreEnvValueSource.SystemEnvironment
                configuredValue != null -> CoreEnvValueSource.DotEnv
                definition.defaultValue != null -> CoreEnvValueSource.CoreDefault
                else -> CoreEnvValueSource.Unconfigured
            }
            definition.key to CoreEnvValue(
                definition = definition,
                configuredValue = configuredValue,
                effectiveValue = systemValue ?: configuredValue ?: definition.defaultValue,
                isConfigured = configuredValue != null,
                source = source,
            )
        }
        return CoreEnvSnapshot(variant, scriptDir, coreDir, envsJsFile, envFile, rawContent, definitions, values)
    }

    fun updateValue(snapshot: CoreEnvSnapshot, key: String, value: String) {
        val definition = snapshot.definition(key)
        val normalized = validate(definition, value)
        DesktopRuntimeEnv.updateCoreValue(snapshot.envFile, definition.key, normalized)
        val actual = DesktopRuntimeEnv.readValue(snapshot.envFile, definition.key)
        require(actual == normalized) { "写入 .env 后校验失败：${snapshot.envFile.absolutePath}" }
    }

    fun deleteValue(snapshot: CoreEnvSnapshot, key: String) {
        val definition = snapshot.definition(key)
        DesktopRuntimeEnv.deleteValue(snapshot.envFile, definition.key)
        require(DesktopRuntimeEnv.readValue(snapshot.envFile, definition.key) == null) {
            "删除 .env 显式变量后校验失败：${snapshot.envFile.absolutePath}"
        }
    }

    private fun validate(definition: CoreEnvDefinition, raw: String): String {
        val value = raw.trimEnd('\r', '\n')
        when (definition.type) {
            CoreEnvType.Number -> {
                val number = value.toIntOrNull() ?: throw IllegalArgumentException("${definition.key} 必须是整数")
                definition.min?.let { require(number >= it) { "${definition.key} 不能小于 $it" } }
                definition.max?.let { require(number <= it) { "${definition.key} 不能大于 $it" } }
            }
            CoreEnvType.Boolean -> require(value == "true" || value == "false") { "${definition.key} 必须是 true 或 false" }
            CoreEnvType.Select -> require(value in definition.options) { "${definition.key} 必须从已声明选项中选择" }
            CoreEnvType.MultiSelect -> {
                val selected = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                require(selected.all { it in definition.options }) { "${definition.key} 包含未声明的选项" }
                require(selected.distinct().size == selected.size) { "${definition.key} 不允许重复选项" }
            }
            CoreEnvType.Text, CoreEnvType.Map -> Unit
        }
        return value
    }

    private fun CoreEnvSnapshot.definition(key: String): CoreEnvDefinition {
        val normalized = key.trim().uppercase()
        require(normalized !in CORE_ENV_HOST_KEYS) { "不允许编辑 Desktop 宿主变量：$normalized" }
        return definitions.firstOrNull { it.key == normalized }
            ?: throw IllegalArgumentException("核心未声明环境变量：$normalized")
    }
}
