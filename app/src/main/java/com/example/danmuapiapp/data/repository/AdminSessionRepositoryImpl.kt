package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.SecureStringStore
import com.example.danmuapiapp.domain.model.AdminSessionState
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminSessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val envConfigRepository: EnvConfigRepository
) : AdminSessionRepository {

    companion object {
        private const val SESSION_PREFS_NAME = "admin_session"
        private const val SESSION_KEY = "session_token"
        private const val SESSION_KEY_ALIAS = "danmuapi_admin_session_key"
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _sessionState = MutableStateFlow(AdminSessionState())
    override val sessionState = _sessionState.asStateFlow()
    private val sessionPrefs = context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    private val secureStore = SecureStringStore(sessionPrefs, SESSION_KEY_ALIAS)

    @Volatile
    private var configuredToken: String = ""

    @Volatile
    private var sessionToken: String = secureStore.get(SESSION_KEY).trim()

    @Volatile
    private var envLoadedOnce: Boolean = false

    init {
        repoScope.launch {
            combine(
                envConfigRepository.envVars,
                envConfigRepository.isCatalogLoading
            ) { env, loading ->
                env to loading
            }.collectLatest { (env, loading) ->
                val latest = env["ADMIN_TOKEN"]?.trim().orEmpty()
                configuredToken = latest
                if (!loading) {
                    envLoadedOnce = true
                }
                if (envLoadedOnce &&
                    (latest.isBlank() || (sessionToken.isNotBlank() && sessionToken != latest))
                ) {
                    clearSessionToken()
                }
                publishState()
            }
        }
        refresh()
    }

    override fun refresh() {
        envConfigRepository.reload()
    }

    override suspend fun login(inputToken: String): Result<Unit> {
        return withContext(Dispatchers.Default) {
            runCatching {
                val candidate = inputToken.trim()
                val current = configuredToken.trim()
                when {
                    candidate.isBlank() -> error("请输入 ADMIN_TOKEN")
                    current.isBlank() -> error("当前未配置 ADMIN_TOKEN，请先在本页完成配置")
                    candidate != current -> error("ADMIN_TOKEN 不正确")
                }
                setSessionToken(current)
                publishState()
            }
        }
    }

    override suspend fun logout() {
        withContext(Dispatchers.Default) {
            clearSessionToken()
            publishState()
        }
    }

    override suspend fun setAdminTokenAndLogin(token: String): Result<Unit> {
        return withContext(Dispatchers.Default) {
            runCatching {
                val candidate = token.trim()
                if (candidate.isBlank()) {
                    error("ADMIN_TOKEN 不能为空")
                }
                envConfigRepository.setValue("ADMIN_TOKEN", candidate)
                envConfigRepository.reload()

                val confirmed = withTimeoutOrNull(2200L) {
                    while (true) {
                        if (configuredToken == candidate) {
                            return@withTimeoutOrNull candidate
                        }
                        kotlinx.coroutines.delay(80L)
                    }
                } ?: configuredToken

                if (confirmed != candidate) {
                    error("ADMIN_TOKEN 保存失败，请检查 .env 写入权限")
                }
                setSessionToken(candidate)
                publishState()
            }
        }
    }

    override fun currentAdminTokenOrNull(): String {
        val state = _sessionState.value
        if (!state.isAdminMode) return ""
        return sessionToken.trim()
    }

    private fun publishState() {
        val token = configuredToken.trim()
        _sessionState.value = AdminSessionState(
            isAdminMode = sessionToken.isNotBlank() && sessionToken == token,
            hasAdminTokenConfigured = token.isNotBlank(),
            tokenHint = maskToken(token)
        )
    }

    private fun maskToken(token: String): String {
        val value = token.trim()
        if (value.isBlank()) return "未配置"
        if (value.length <= 4) {
            return value.take(1) + "***"
        }
        return value.take(2) + "***" + value.takeLast(2)
    }

    private fun setSessionToken(value: String) {
        sessionToken = value.trim()
        secureStore.put(SESSION_KEY, sessionToken)
    }

    private fun clearSessionToken() {
        sessionToken = ""
        secureStore.put(SESSION_KEY, "")
    }
}
