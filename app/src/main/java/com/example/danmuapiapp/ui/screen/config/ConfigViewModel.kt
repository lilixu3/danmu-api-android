package com.example.danmuapiapp.ui.screen.config

import android.content.Context
import com.example.danmuapiapp.data.util.TokenDefaults
import androidx.lifecycle.ViewModel
import com.example.danmuapiapp.domain.model.EnvVarDef
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val envConfigRepo: EnvConfigRepository,
    private val adminSessionRepository: AdminSessionRepository,
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : ViewModel() {

    companion object {
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    val envVars = envConfigRepo.envVars
    val catalog = envConfigRepo.catalog
    val isCatalogLoading = envConfigRepo.isCatalogLoading
    val rawContent = envConfigRepo.rawContent
    val adminSessionState = adminSessionRepository.sessionState

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _editingVar = MutableStateFlow<EnvVarDef?>(null)
    val editingVar: StateFlow<EnvVarDef?> = _editingVar.asStateFlow()

    private val _isRawMode = MutableStateFlow(false)
    val isRawMode: StateFlow<Boolean> = _isRawMode.asStateFlow()

    fun reload() = envConfigRepo.reload()
    fun setSearch(query: String) { _searchQuery.value = query }
    fun toggleRawMode() { _isRawMode.value = !_isRawMode.value }
    fun openEditor(def: EnvVarDef) { _editingVar.value = def }
    fun closeEditor() { _editingVar.value = null }

    fun setValue(key: String, value: String) {
        if (!ensureAdminMode()) return
        envConfigRepo.setValue(key, value)
        _operationMessage.value = "已保存 $key"
    }

    fun deleteKey(key: String) {
        if (!ensureAdminMode()) return
        envConfigRepo.deleteKey(key)
        _operationMessage.value = "已删除 $key"
    }

    fun saveRawContent(content: String) {
        if (!ensureAdminMode()) return
        envConfigRepo.saveRawContent(content)
        _operationMessage.value = "源码已保存"
    }

    fun getEnvFilePath(): String = envConfigRepo.getEnvFilePath()

    suspend fun generateBilibiliQr(): Result<BilibiliQrGenerateResult> {
        return runCatching {
            val root = postJson("/api/cookie/qr/generate")
            if (root.has("success") && !root.optBoolean("success", false)) {
                error(extractMessage(root, "生成二维码失败"))
            }
            val data = root.optJSONObject("data") ?: root
            val qrUrl = data.optString("url").trim()
            val qrKey = data.optString("qrcode_key").trim()
            if (qrUrl.isBlank() || qrKey.isBlank()) {
                error("生成二维码失败：服务端返回数据不完整")
            }
            BilibiliQrGenerateResult(
                qrUrl = qrUrl,
                qrcodeKey = qrKey
            )
        }
    }

    suspend fun pollBilibiliQr(qrcodeKey: String): Result<BilibiliQrPollResult> {
        return runCatching {
            val payload = JSONObject().put("qrcode_key", qrcodeKey)
            val root = postJson("/api/cookie/qr/check", payload)
            if (root.has("success") && !root.optBoolean("success", false)) {
                error(extractMessage(root, "扫码状态检查失败"))
            }
            val data = root.optJSONObject("data") ?: root
            val code = data.optInt("code", -1)
            val message = data.optString("message").ifBlank {
                root.optString("message").ifBlank { "未知状态" }
            }
            val cookie = data.optString("cookie").trim().ifBlank { null }
            val refreshToken = data.optString("refresh_token").trim().ifBlank { null }

            val rawExpires = listOf(
                data.optLong("cookie_expires_at", 0L),
                data.optLong("cookieExpiresAt", 0L),
                data.optLong("expiresAt", 0L),
                root.optLong("cookie_expires_at", 0L),
                root.optLong("expiresAt", 0L),
            ).firstOrNull { it > 0L }

            BilibiliQrPollResult(
                code = code,
                message = message,
                cookie = cookie,
                refreshToken = refreshToken,
                expiresAtMs = normalizeEpochMillis(rawExpires)
            )
        }
    }

    suspend fun verifyBilibiliCookie(cookie: String): Result<BilibiliCookieVerifyResult> {
        return runCatching {
            val payload = JSONObject().put("cookie", cookie)
            val root = postJson("/api/cookie/verify", payload)

            if (root.has("success") && !root.optBoolean("success", false)) {
                BilibiliCookieVerifyResult(
                    isValid = false,
                    uname = null,
                    expiresAtMs = null,
                    message = extractMessage(root, "Cookie 无效")
                )
            } else {
                val data = root.optJSONObject("data") ?: root
                val isValid = data.optBoolean("isValid", true)
                val uname = data.optString("uname")
                    .ifBlank { data.optString("username") }
                    .trim()
                    .ifBlank { null }

                val rawExpires = listOf(
                    data.optLong("expiresAt", 0L),
                    data.optLong("expires_at", 0L),
                    data.optLong("cookie_expires_at", 0L),
                    root.optLong("expiresAt", 0L),
                ).firstOrNull { it > 0L }

                val message = data.optString("message").ifBlank {
                    root.optString("message").ifBlank {
                        if (isValid) "有效" else "无效"
                    }
                }

                BilibiliCookieVerifyResult(
                    isValid = isValid,
                    uname = uname,
                    expiresAtMs = normalizeEpochMillis(rawExpires),
                    message = message
                )
            }
        }
    }

    suspend fun verifyAiConnectivity(apiKey: String): Result<AiConnectivityVerifyResult> {
        return runCatching {
            val key = apiKey.trim()
            if (key.isBlank()) {
                error("请先输入 API Key")
            }

            val payload = JSONObject().put("aiApiKey", key)
            val root = postJson("/api/ai/verify", payload)
            val data = root.optJSONObject("data")
            val rootExplicitFailure = root.has("success") && !root.optBoolean("success", false)
            val reachable = resolveAiConnectivityResult(root, data, rootExplicitFailure)

            AiConnectivityVerifyResult(
                isReachable = reachable,
                message = extractAiVerifyMessage(root, data, reachable),
                provider = readFirstStringField(data, root, listOf("provider", "vendor", "platform")),
                model = readFirstStringField(data, root, listOf("model", "modelName", "aiModel")),
                latencyMs = readFirstLongField(data, root, listOf("latencyMs", "durationMs", "elapsedMs"))
            )
        }
    }

    private suspend fun postJson(path: String, payload: JSONObject? = null): JSONObject {
        return withContext(Dispatchers.IO) {
            val bodyText = (payload ?: JSONObject()).toString()
            val requestBody = bodyText.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(buildLocalApiUrl(path))
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(raw).optString("message").trim().ifBlank { null }
                    }.getOrNull()
                    error(msg ?: "请求失败：HTTP ${response.code}")
                }
                if (raw.isBlank()) return@withContext JSONObject()
                runCatching { JSONObject(raw) }.getOrElse {
                    error("服务返回了无效 JSON")
                }
            }
        }
    }

    private fun buildLocalApiUrl(path: String): String {
        val prefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
        val port = prefs.getInt("port", DEFAULT_PORT)
        val token = TokenDefaults.resolveTokenFromPrefs(prefs, context)
        val tokenPath = if (token.isBlank()) "" else "/$token"
        return "http://127.0.0.1:$port$tokenPath$path"
    }

    private fun normalizeEpochMillis(raw: Long?): Long? {
        val value = raw ?: return null
        if (value <= 0L) return null
        return when {
            value < 10_000_000_000L -> value * 1000L
            value > 10_000_000_000_000L -> value / 1000L
            else -> value
        }
    }

    private fun extractMessage(root: JSONObject, fallback: String): String {
        return root.optString("message").trim().ifBlank { fallback }
    }

    private fun resolveAiConnectivityResult(
        root: JSONObject,
        data: JSONObject?,
        rootExplicitFailure: Boolean
    ): Boolean {
        if (rootExplicitFailure) return false

        val keys = listOf("isConnected", "connected", "isReachable", "reachable", "ok", "success")
        keys.forEach { key ->
            readBooleanField(data, key)?.let { return it }
        }
        keys.forEach { key ->
            readBooleanField(root, key)?.let { return it }
        }
        return true
    }

    private fun readBooleanField(target: JSONObject?, key: String): Boolean? {
        if (target == null || !target.has(key)) return null
        return when (val raw = target.opt(key)) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> {
                when (raw.trim().lowercase()) {
                    "true", "1", "yes", "ok", "success" -> true
                    "false", "0", "no", "fail", "failed" -> false
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractAiVerifyMessage(
        root: JSONObject,
        data: JSONObject?,
        reachable: Boolean
    ): String {
        val dataMsg = data?.optString("message").orEmpty().trim()
        if (dataMsg.isNotBlank()) return dataMsg

        val rootMsg = root.optString("message").trim()
        if (rootMsg.isNotBlank()) return rootMsg

        return if (reachable) "AI 连通性正常" else "AI 连通性测试失败"
    }

    private fun readFirstStringField(
        data: JSONObject?,
        root: JSONObject,
        keys: List<String>
    ): String? {
        keys.forEach { key ->
            if (data != null && data.has(key)) {
                val value = data.optString(key).trim()
                if (value.isNotBlank()) return value
            }
            if (root.has(key)) {
                val value = root.optString(key).trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun readFirstLongField(
        data: JSONObject?,
        root: JSONObject,
        keys: List<String>
    ): Long? {
        keys.forEach { key ->
            parseLongField(data, key)?.let { return it }
            parseLongField(root, key)?.let { return it }
        }
        return null
    }

    private fun parseLongField(target: JSONObject?, key: String): Long? {
        if (target == null || !target.has(key)) return null
        val raw = target.opt(key)
        val value = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
            else -> null
        } ?: return null
        return value.takeIf { it > 0L }
    }

    fun dismissMessage() {
        _operationMessage.value = null
    }

    private fun ensureAdminMode(): Boolean {
        if (adminSessionRepository.sessionState.value.isAdminMode) return true
        _operationMessage.value = "当前为只读模式，请先到 设置 > 管理员权限 开启管理员模式"
        return false
    }
}

data class BilibiliQrGenerateResult(
    val qrUrl: String,
    val qrcodeKey: String,
)

data class BilibiliQrPollResult(
    val code: Int,
    val message: String,
    val cookie: String?,
    val refreshToken: String?,
    val expiresAtMs: Long?,
)

data class BilibiliCookieVerifyResult(
    val isValid: Boolean,
    val uname: String?,
    val expiresAtMs: Long?,
    val message: String,
)

data class AiConnectivityVerifyResult(
    val isReachable: Boolean,
    val message: String,
    val provider: String?,
    val model: String?,
    val latencyMs: Long?,
)
