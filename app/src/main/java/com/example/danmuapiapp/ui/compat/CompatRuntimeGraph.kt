package com.example.danmuapiapp.ui.compat

import android.content.Context
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.repository.CoreRepositoryImpl
import com.example.danmuapiapp.data.repository.EnvConfigRepositoryImpl
import com.example.danmuapiapp.data.repository.RuntimeRepositoryImpl
import com.example.danmuapiapp.data.repository.SettingsRepositoryImpl
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.AdminSessionState
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object CompatRuntimeGraph {
    @Volatile
    private var holder: Holder? = null

    fun get(context: Context): Holder {
        holder?.let { return it }
        return synchronized(this) {
            holder ?: buildHolder(context.applicationContext).also { holder = it }
        }
    }

    private fun buildHolder(context: Context): Holder {
        val settingsRepository = SettingsRepositoryImpl(context)
        val adminSessionRepository = NoOpAdminSessionRepository()
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val githubProxyService = GithubProxyService(context, httpClient)
        val githubRemoteService = GithubRemoteService(httpClient, githubProxyService)
        val runtimeRepository = RuntimeRepositoryImpl(
            context = context,
            settingsRepository = settingsRepository,
            adminSessionRepository = adminSessionRepository
        )
        val coreRepository = CoreRepositoryImpl(
            context = context,
            httpClient = httpClient,
            githubRemoteService = githubRemoteService
        )
        val appUpdateService = AppUpdateService(
            context = context,
            httpClient = httpClient,
            githubProxyService = githubProxyService,
            githubRemoteService = githubRemoteService
        )
        return Holder(
            context = context,
            settingsRepository = settingsRepository,
            runtimeRepository = runtimeRepository,
            coreRepository = coreRepository,
            appUpdateService = appUpdateService
        )
    }

    class Holder(
        private val context: Context,
        val settingsRepository: SettingsRepositoryImpl,
        val runtimeRepository: RuntimeRepositoryImpl,
        val coreRepository: CoreRepositoryImpl,
        val appUpdateService: AppUpdateService
    ) {
        val envConfigRepository: EnvConfigRepositoryImpl by lazy {
            EnvConfigRepositoryImpl(context)
        }
    }

    private class NoOpAdminSessionRepository : AdminSessionRepository {
        private val state = MutableStateFlow(AdminSessionState())
        override val sessionState: StateFlow<AdminSessionState> = state

        override fun refresh() = Unit

        override suspend fun login(inputToken: String): Result<Unit> {
            return Result.failure(IllegalStateException("兼容模式不支持管理员登录"))
        }

        override suspend fun logout() = Unit

        override suspend fun setAdminTokenAndLogin(token: String): Result<Unit> {
            return Result.failure(IllegalStateException("兼容模式不支持管理员登录"))
        }

        override fun currentAdminTokenOrNull(): String = ""
    }
}
