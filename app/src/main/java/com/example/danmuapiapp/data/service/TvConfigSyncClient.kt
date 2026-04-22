package com.example.danmuapiapp.data.service

import android.os.Build
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvConfigSyncClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val runtimeRepository: RuntimeRepository,
    private val settingsRepository: SettingsRepository,
    private val envConfigRepository: EnvConfigRepository
) {

    suspend fun syncToTarget(target: TvConfigSyncTarget): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload()
            val request = Request.Builder()
                .url(target.applyUrl)
                .post(
                    TvConfigSyncCodec.encodePayload(payload)
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .header("Accept", "application/json")
                .build()

            httpClient.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    val raw = response.body.string().trim()
                    if (!response.isSuccessful) {
                        val parsed = raw.takeIf { it.isNotBlank() }?.let {
                            runCatching { TvConfigSyncCodec.decodeResponse(it) }.getOrNull()
                        }
                        val message = parsed?.message?.takeIf { it.isNotBlank() }
                            ?: "电视端返回 ${response.code}"
                        error(message)
                    }

                    val parsed = raw.takeIf { it.isNotBlank() }?.let {
                        runCatching { TvConfigSyncCodec.decodeResponse(it) }.getOrNull()
                    }
                    if (parsed != null && !parsed.ok) {
                        error(parsed.message.ifBlank { "电视端未接受配置" })
                    }
                    parsed?.message?.ifBlank { "配置已同步到电视端" } ?: "配置已同步到电视端"
                }
        }
    }

    private fun buildPayload(): TvConfigSyncPayload {
        val runtime = runtimeRepository.runtimeState.value
        val envContent = envConfigRepository.rawContent.value.ifBlank { "# DanmuApiApp .env\n" }
        val displayNames = settingsRepository.coreDisplayNames.value
        return TvConfigSyncPayload(
            sourceDeviceName = buildDeviceName(),
            sentAtEpochMs = System.currentTimeMillis(),
            envContent = envContent,
            runtime = TvConfigSyncRuntime(
                port = runtime.port,
                token = runtime.token,
                variantKey = runtime.variant.key
            ),
            settings = TvConfigSyncSettings(
                githubProxy = settingsRepository.githubProxy.value,
                githubToken = settingsRepository.githubToken.value,
                stableRepoDisplayName = displayNames.stable,
                devRepoDisplayName = displayNames.dev,
                customRepo = settingsRepository.customRepo.value,
                customRepoBranch = settingsRepository.customRepoBranch.value,
                customRepoDisplayName = settingsRepository.customRepoDisplayName.value
            )
        )
    }

    private fun buildDeviceName(): String {
        val parts = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.orEmpty().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return parts.joinToString(" ").ifBlank { "DanmuApi 手机端" }
    }
}
