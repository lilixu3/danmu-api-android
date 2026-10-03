package com.example.danmuapiapp.ui.compat

import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class CompatConfigEntry(val definition: EnvVarDef, val value: String, val configured: Boolean)
internal data class CompatManagementState(
    val admin: AdminSessionState = AdminSessionState(),
    val entries: List<CompatConfigEntry> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String = "",
    val fromApi: Boolean = false,
    val saveSequence: Int = 0
)

internal fun parseCompatConfig(root: JSONObject, explicit: Map<String, String>, defaults: Map<String, String>): List<CompatConfigEntry> {
    val schema = root.optJSONObject("envVarConfig") ?: error("核心未返回配置目录")
    val raw = root.optJSONObject("originalEnvVars") ?: JSONObject()
    val effective = root.optJSONObject("envs") ?: JSONObject()
    return schema.keys().asSequence().map { key ->
        val def = schema.optJSONObject(key) ?: JSONObject()
        val options = def.optJSONArray("options")
        val type = when (def.optString("type").lowercase()) {
            "boolean", "bool" -> EnvType.BOOLEAN
            "number", "int", "integer" -> EnvType.NUMBER
            "select" -> EnvType.SELECT
            "multi-select", "multiselect" -> EnvType.MULTI_SELECT
            "map" -> EnvType.MAP
            "color-list" -> EnvType.COLOR_LIST
            "custom-merge-rules" -> EnvType.CUSTOM_MERGE_RULES
            "timeline-offset" -> EnvType.TIMELINE_OFFSET
            else -> EnvType.TEXT
        }
        fun valueOf(value: Any?): String = when (value) {
            null, JSONObject.NULL -> ""
            is JSONObject -> if (value.has("value")) valueOf(value.opt("value")) else value.toString()
            else -> value.toString()
        }
        val value = when {
            raw.has(key) -> valueOf(raw.opt(key))
            explicit.containsKey(key) -> explicit.getValue(key)
            effective.has(key) -> valueOf(effective.opt(key))
            else -> defaults[key].orEmpty()
        }
        val sensitive = listOf("TOKEN", "PASSWORD", "SECRET", "COOKIE", "API_KEY").any { key.contains(it, true) }
        CompatConfigEntry(EnvVarDef(key, def.optString("category", "system"), type,
            def.optString("description", key), options?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            if (def.has("min")) def.optInt("min") else null,
            if (def.has("max")) def.optInt("max") else null, sensitive), value, explicit.containsKey(key))
    }.toList().sortedWith(compareBy<CompatConfigEntry> {
        listOf("api", "source", "match", "danmu", "cache", "system").indexOf(it.definition.category).let { n -> if (n < 0) 99 else n }
    }.thenBy { it.definition.key })
}

internal fun validateCompatConfigValue(def: EnvVarDef, value: String): String? {
    return when (def.type) {
        EnvType.BOOLEAN -> if (value !in listOf("true", "false")) "请选择开启或关闭" else null
        EnvType.NUMBER -> {
            val number = value.toDoubleOrNull()
            when {
                number == null || !number.isFinite() -> "请输入有效数字"
                def.min != null && number < def.min -> "不能小于 ${def.min}"
                def.max != null && number > def.max -> "不能大于 ${def.max}"
                else -> null
            }
        }
        EnvType.SELECT -> if (def.options.isNotEmpty() && value !in def.options) "请选择核心支持的值" else null
        else -> null
    }
}

internal class CompatManagementController(
    private val graph: CompatRuntimeGraph.Holder,
    private val scope: CoroutineScope,
    private val event: (String) -> Unit
) {
    private val _state = MutableStateFlow(CompatManagementState())
    val state = _state.asStateFlow()
    private val mutation = Mutex()
    private var refreshJob: Job? = null
    private val client = graph.httpClient.newBuilder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    init {
        scope.launch { graph.adminSessionRepository.sessionState.collect { admin -> _state.update { it.copy(admin = admin) } } }
    }

    fun refresh() {
        if (_state.value.saving) return
        val previous = refreshJob
        refreshJob = scope.launch { previous?.cancelAndJoin(); load() }
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true, error = "") }
        try {
            graph.envConfigRepository.reload()
            check(withTimeoutOrNull(30_000) {
                graph.envConfigRepository.isCatalogLoading.first { !it }
                true
            } == true) { "配置读取超时，请检查工作目录权限后重试" }
            val explicit = DotEnvCodec.parse(graph.envConfigRepository.readCurrentRawContent().getOrThrow())
            val defaults = graph.envConfigRepository.envVars.value
            val runtime = graph.runtimeRepository.runtimeState.value
            val admin = graph.adminSessionRepository.currentAdminTokenOrNull()
            if (runtime.status == ServiceStatus.Running && admin.isNotBlank()) {
                val json = request("/api/config", admin)
                val entries = parseCompatConfig(json, explicit, defaults)
                check(entries.isNotEmpty()) { "核心返回的配置目录为空" }
                _state.update { it.copy(entries = entries, fromApi = true) }
            } else {
                val entries = graph.envConfigRepository.catalog.value.map { def ->
                    CompatConfigEntry(def, explicit[def.key] ?: defaults[def.key].orEmpty(), explicit.containsKey(def.key))
                }
                _state.update { it.copy(entries = entries, fromApi = false) }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { _state.update { it.copy(error = error.message ?: "配置加载失败") } }
        finally { _state.update { it.copy(loading = false) } }
    }

    fun login(token: String) {
        if (_state.value.saving || _state.value.loading) return
        scope.launch {
            mutation.withLock {
                _state.update { it.copy(saving = true, error = "") }
                try {
                    val candidate = token.trim()
                    val existing = _state.value.admin.hasAdminTokenConfigured
                    if (existing) graph.adminSessionRepository.login(candidate).getOrThrow()
                    else if (candidate.isBlank()) {
                        graph.envConfigRepository.setValue("ADMIN_TOKEN", candidate)
                        val saved = DotEnvCodec.parse(graph.envConfigRepository.readCurrentRawContent().getOrThrow())
                        check(saved["ADMIN_TOKEN"].orEmpty().isBlank()) { "ADMIN_TOKEN 保存失败，请检查 .env 写入权限" }
                        graph.adminSessionRepository.logout()
                    } else graph.adminSessionRepository.setAdminTokenAndLogin(candidate).getOrThrow()
                    if (candidate.isNotBlank() && graph.runtimeRepository.runtimeState.value.status == ServiceStatus.Running) {
                        val verified = withTimeoutOrNull(12_000) {
                            var active = false
                            while (!active) {
                                try {
                                    val config = request("/api/config", candidate)
                                    check(config.optJSONObject("originalEnvVars")?.optString("ADMIN_TOKEN") == candidate) { "管理员密钥尚未生效" }
                                    active = true
                                }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { delay(500) }
                            }
                            true
                        }
                        check(verified == true) {
                            if (existing) "管理员密钥验证超时，请检查服务状态后重试"
                            else "ADMIN_TOKEN 已保存，但核心尚未加载新密钥，请刷新或重启服务后重试"
                        }
                    }
                    event(if (existing) "管理员权限已验证" else if (candidate.isBlank()) "ADMIN_TOKEN 已清空，当前未配置管理员密钥" else "ADMIN_TOKEN 已保存并生效")
                    load()
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { _state.update { it.copy(error = error.message ?: "管理员密钥验证失败") } }
                finally { _state.update { it.copy(saving = false) } }
            }
        }
    }

    fun save(entry: CompatConfigEntry, value: String, reset: Boolean) {
        if (_state.value.saving || !graph.adminSessionRepository.sessionState.value.isAdminMode) return
        scope.launch {
            mutation.withLock {
                _state.update { it.copy(saving = true, error = "") }
                try {
                    val key = entry.definition.key
                    if (!reset) validateCompatConfigValue(entry.definition, value)?.let { error(it) }
                    val runtime = graph.runtimeRepository.runtimeState.value
                    check(runtime.status !in setOf(ServiceStatus.Starting, ServiceStatus.Stopping) && runtime.transition == null) {
                        "服务正在切换，请稍后保存"
                    }
                    if (runtime.status == ServiceStatus.Running) {
                        val payload = JSONObject().put("key", key)
                        if (!reset) payload.put("value", value)
                        request(if (reset) "/api/env/del" else "/api/env/set",
                            graph.adminSessionRepository.currentAdminTokenOrNull(), payload)
                    } else {
                        if (reset) graph.envConfigRepository.deleteKey(key) else graph.envConfigRepository.setValue(key, value)
                    }
                    val verified = DotEnvCodec.parse(graph.envConfigRepository.readCurrentRawContent().getOrThrow())
                    check(if (reset) !verified.containsKey(key) else verified[key] == value) {
                        "核心没有写入当前工作目录，不能确认保存成功；请重启服务后重试"
                    }
                    if (key == "TOKEN") graph.runtimeRepository.applyServiceConfig(runtime.port,
                        if (reset) "" else value, restartIfRunning = false)
                    if (key == "ADMIN_TOKEN") {
                        if (reset || value.isBlank()) graph.adminSessionRepository.logout()
                        else graph.adminSessionRepository.setAdminTokenAndLogin(value).getOrThrow()
                    }
                    event(if (reset) "$key 已恢复默认值" else "$key 已保存${if (runtime.status == ServiceStatus.Running) "并热更新" else "，下次启动生效"}")
                    load()
                } catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { _state.update { it.copy(error = error.message ?: "配置保存失败") } }
                finally { _state.update { it.copy(saving = false, saveSequence = it.saveSequence + 1) } }
            }
        }
    }

    private suspend fun request(path: String, token: String, payload: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val runtime = graph.runtimeRepository.runtimeState.value
        val url = RuntimeApiUrls.local(runtime.port).toHttpUrl().newBuilder()
        if (token.isNotBlank()) url.addPathSegment(token)
        path.trim('/').split('/').filter(String::isNotBlank).forEach(url::addPathSegment)
        val builder = Request.Builder().url(url.build())
        if (payload != null) builder.post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "核心接口请求失败（HTTP ${response.code}）" }
            val body = JSONObject(response.body.string())
            check(!body.has("success") || body.optBoolean("success")) { body.optString("message", "核心拒绝配置修改") }
            body
        }
    }
}
