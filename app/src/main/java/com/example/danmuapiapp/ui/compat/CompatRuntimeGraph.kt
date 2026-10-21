package com.example.danmuapiapp.ui.compat

import android.annotation.SuppressLint
import android.content.Context
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.repository.AdminSessionRepositoryImpl
import com.example.danmuapiapp.data.repository.CoreRepositoryImpl
import com.example.danmuapiapp.data.repository.EnvConfigRepositoryImpl
import com.example.danmuapiapp.data.repository.RuntimeDependencyPackManager
import com.example.danmuapiapp.data.repository.RuntimeRepositoryImpl
import com.example.danmuapiapp.data.repository.SettingsRepositoryImpl
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.data.service.GithubPullRequestService
import com.example.danmuapiapp.data.service.PullRequestMergeService
import com.example.danmuapiapp.data.service.UpdateChecker
import okhttp3.OkHttpClient
import com.example.danmuapiapp.data.network.GithubOutboundNetwork

@SuppressLint("StaticFieldLeak") // Holder 持有的是 applicationContext，不会泄漏 Activity
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
        val envConfigRepository = EnvConfigRepositoryImpl(context)
        val adminSessionRepository = AdminSessionRepositoryImpl(context, envConfigRepository)
        val apiQuota = com.example.danmuapiapp.data.service.GithubApiQuota.get(context)
        val httpClient = GithubOutboundNetwork.createClient(context).newBuilder()
            .addInterceptor(com.example.danmuapiapp.data.network.GithubQuotaInterceptor(apiQuota)).build()
        val githubProxyService = GithubProxyService(context, httpClient, apiQuota)
        val githubAccountService = com.example.danmuapiapp.data.service.GithubAccountService(context, httpClient, apiQuota)
        val githubProxySpeedTester = GithubProxySpeedTester(githubProxyService)
        val githubRemoteService = GithubRemoteService(httpClient, githubProxyService)
        val githubPullRequestService = GithubPullRequestService(githubRemoteService)
        val pullRequestMergeService = PullRequestMergeService(
            context,
            githubPullRequestService,
            githubProxyService
        )
        val runtimeDependencyPackManager = RuntimeDependencyPackManager(
            context = context,
            httpClient = httpClient,
            githubRemoteService = githubRemoteService,
            githubProxyService = githubProxyService
        )
        val coreRepository = CoreRepositoryImpl(
            context = context,
            httpClient = httpClient,
            githubProxyService = githubProxyService,
            githubRemoteService = githubRemoteService,
            settingsRepository = settingsRepository,
            runtimeDependencyPackManager = runtimeDependencyPackManager,
            githubPullRequestService = githubPullRequestService,
            pullRequestMergeService = pullRequestMergeService
        )
        val runtimeRepository = RuntimeRepositoryImpl(
            context = context,
            settingsRepository = settingsRepository,
            adminSessionRepository = adminSessionRepository,
            coreRepository = coreRepository,
            runtimeData = com.example.danmuapiapp.data.service.RuntimeDataAccess(context, httpClient)
        )
        val appUpdateService = AppUpdateService(
            context = context,
            httpClient = httpClient,
            githubProxyService = githubProxyService,
            githubRemoteService = githubRemoteService
        )
        val updateChecker = UpdateChecker(
            context = context,
            coreRepo = coreRepository,
            settingsRepo = settingsRepository,
            apiQuota = apiQuota
        )
        return Holder(
            context = context,
            settingsRepository = settingsRepository,
            runtimeRepository = runtimeRepository,
            coreRepository = coreRepository,
            appUpdateService = appUpdateService,
            updateChecker = updateChecker,
            githubProxyService = githubProxyService,
            githubAccountService = githubAccountService,
            githubProxySpeedTester = githubProxySpeedTester,
            envConfigRepository = envConfigRepository,
            adminSessionRepository = adminSessionRepository,
            httpClient = httpClient
        )
    }

    class Holder(
        private val context: Context,
        val settingsRepository: SettingsRepositoryImpl,
        val runtimeRepository: RuntimeRepositoryImpl,
        val coreRepository: CoreRepositoryImpl,
        val appUpdateService: AppUpdateService,
        val updateChecker: UpdateChecker,
        val githubProxyService: GithubProxyService,
        val githubAccountService: com.example.danmuapiapp.data.service.GithubAccountService,
        val githubProxySpeedTester: GithubProxySpeedTester,
        val envConfigRepository: EnvConfigRepositoryImpl,
        val adminSessionRepository: AdminSessionRepositoryImpl,
        val httpClient: OkHttpClient
    )
}
