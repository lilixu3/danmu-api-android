package com.example.danmuapiapp.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.danmuapiapp.data.parser.EnvVarConfigLoader
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnvConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : EnvConfigRepository {
    companion object {
        private const val TAG = "EnvConfigRepo"
    }

    private val _envVars = MutableStateFlow<Map<String, String>>(emptyMap())
    override val envVars: StateFlow<Map<String, String>> = _envVars.asStateFlow()

    private val _catalog = MutableStateFlow<List<EnvVarDef>>(emptyList())
    override val catalog: StateFlow<List<EnvVarDef>> = _catalog.asStateFlow()
    private val _isCatalogLoading = MutableStateFlow(true)
    override val isCatalogLoading: StateFlow<Boolean> = _isCatalogLoading.asStateFlow()

    private val _rawContent = MutableStateFlow("")
    override val rawContent: StateFlow<String> = _rawContent.asStateFlow()
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reloadJob: Job? = null
    private val reloadTicket = AtomicLong(0L)

    init { reload() }

    override fun reload() {
        reloadJob?.cancel()
        _isCatalogLoading.value = true
        val ticket = reloadTicket.incrementAndGet()
        reloadJob = repoScope.launch {
            try {
                val file = envFile()
                val text = readEnvText(file).getOrElse {
                    Log.w(TAG, "读取 .env 失败：${file.absolutePath}", it)
                    ""
                }
                val userValues = parseEnvFile(text)
                val defaultValues = runCatching { EnvVarConfigLoader.loadDefaultValues(context) }
                    .getOrElse {
                        Log.w(TAG, "加载核心默认值失败", it)
                        emptyMap()
                    }
                _rawContent.value = text
                _envVars.value = mergeEffectiveEnv(defaultValues, userValues)
                _catalog.value = runCatching { EnvVarConfigLoader.loadCatalog(context) }
                    .getOrElse {
                        Log.w(TAG, "加载环境变量目录失败", it)
                        emptyList()
                    }
            } finally {
                if (reloadTicket.get() == ticket) {
                    _isCatalogLoading.value = false
                }
            }
        }
    }

    override fun setValue(key: String, value: String) {
        val file = envFile()
        val lines = readEnvText(file).map { text ->
            if (text.isBlank()) mutableListOf() else text.split('\n').toMutableList()
        }.getOrElse {
            Log.w(TAG, "读取 .env 失败，改为基于空内容写入：${file.absolutePath}", it)
            mutableListOf()
        }

        val formatted = formatValue(value)
        var found = false
        for (idx in lines.indices) {
            val line = lines[idx]
            if (line.startsWith("#")) continue
            val eqIdx = line.indexOf('=')
            if (eqIdx > 0 && line.substring(0, eqIdx).trim() == key) {
                lines[idx] = "$key=$formatted"
                found = true
                break
            }
        }
        if (!found) lines.add("$key=$formatted")

        val out = lines.joinToString("\n").trimEnd() + "\n"
        writeEnvText(file, out).onFailure {
            Log.w(TAG, "写入 .env 失败：${file.absolutePath}", it)
        }
        reload()
    }

    override fun deleteKey(key: String) {
        val file = envFile()
        val lines = readEnvText(file)
            .map { text -> if (text.isBlank()) emptyList() else text.split('\n') }
            .getOrElse {
                Log.w(TAG, "读取 .env 失败，跳过删除：${file.absolutePath}", it)
                return
            }
            .filterNot { line ->
            !line.startsWith("#") && line.indexOf('=').let { idx ->
                idx > 0 && line.substring(0, idx).trim() == key
            }
        }
        val out = lines.joinToString("\n").trimEnd() + "\n"
        writeEnvText(file, out).onFailure { Log.w(TAG, "写入 .env 失败：${file.absolutePath}", it) }
        reload()
    }

    override fun saveRawContent(content: String) {
        val file = envFile()
        val normalized = content.replace("\r\n", "\n")
        val out = if (normalized.endsWith('\n')) normalized else "$normalized\n"
        writeEnvText(file, out).onFailure {
            Log.w(TAG, "写入 .env 失败：${file.absolutePath}", it)
        }
        reload()
    }

    override fun getEnvFilePath(): String = envFile().absolutePath

    private fun envFile(): File {
        val mode = currentRunMode()
        if (mode == RunMode.Normal) {
            runCatching { NodeProjectManager.ensureProjectExtracted(context) }
        }
        return File(NodeProjectManager.projectDir(context, mode), "config/.env")
    }

    private fun currentRunMode(): RunMode {
        return RuntimeModePrefs.get(context)
    }

    private fun readEnvText(file: File): Result<String> {
        return if (currentRunMode() != RunMode.Normal) {
            runCatching { rootReadText(file.absolutePath) ?: "" }
        } else {
            runCatching { if (file.exists()) file.readText(Charsets.UTF_8) else "" }
        }
    }

    private fun writeEnvText(file: File, text: String): Result<Unit> {
        return if (currentRunMode() != RunMode.Normal) {
            runCatching {
                if (!rootWriteText(file.absolutePath, text)) {
                    throw IllegalStateException("Root 写入失败")
                }
            }
        } else {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(text, Charsets.UTF_8)
            }
        }
    }

    private fun rootReadText(path: String): String? {
        val script = """
            FILE=${shellQuote(path)}
            if [ ! -f "${'$'}FILE" ]; then
              exit 0
            fi
            cat "${'$'}FILE"
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        if (!result.ok) return null
        return result.stdout
    }

    private fun rootWriteText(path: String, text: String): Boolean {
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val script = """
            FILE=${shellQuote(path)}
            DIR=${'$'}(dirname "${'$'}FILE")
            mkdir -p "${'$'}DIR" >/dev/null 2>&1 || true
            if command -v base64 >/dev/null 2>&1; then
              printf '%s' ${shellQuote(encoded)} | base64 -d > "${'$'}FILE"
            elif command -v toybox >/dev/null 2>&1; then
              printf '%s' ${shellQuote(encoded)} | toybox base64 -d > "${'$'}FILE"
            else
              exit 1
            fi
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 7000L).ok
    }

    private fun shellQuote(input: String): String {
        return "'" + input.replace("'", "'\"'\"'") + "'"
    }

    private fun parseEnvFile(text: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) continue
            val key = trimmed.substring(0, eqIdx).trim()
            var value = trimmed.substring(eqIdx + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length - 1)
            }
            map[key] = value
        }
        return map
    }

    private fun mergeEffectiveEnv(
        defaults: Map<String, String>,
        userValues: Map<String, String>
    ): Map<String, String> {
        val merged = LinkedHashMap<String, String>(defaults.size + userValues.size)

        defaults.forEach { (key, value) ->
            if (value.isNotBlank()) {
                merged[key] = value
            }
        }
        userValues.forEach { (key, value) ->
            if (key.equals("TOKEN", ignoreCase = true) && value.isBlank()) {
                return@forEach
            }
            merged[key] = value
        }
        return merged
    }

    private fun formatValue(value: String): String {
        return if (value.contains(' ') || value.contains('=') || value.contains('#')) {
            "\"$value\""
        } else value
    }
}
