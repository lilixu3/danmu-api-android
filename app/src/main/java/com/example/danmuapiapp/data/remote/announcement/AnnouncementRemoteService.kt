package com.example.danmuapiapp.data.remote.announcement

import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.domain.model.ActiveAnnouncementEnvelope
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.AppAnnouncement
import com.example.danmuapiapp.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AnnouncementRemoteService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val USER_AGENT = "DanmuApiApp"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchActiveAnnouncement(variant: ApiVariant): AppAnnouncement? {
        val baseUrl = settingsRepository.announcementBaseUrl.value.trim()
        if (baseUrl.isBlank()) return null
        val requestUrl = baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("api/app/announcements/active")
            ?.addQueryParameter("platform", "android")
            ?.addQueryParameter("variant", variant.key)
            ?.addQueryParameter("version", BuildConfig.VERSION_NAME)
            ?.build()
            ?: return null

        return runCatching {
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "$USER_AGENT/${BuildConfig.VERSION_NAME}")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 204 || response.code == 404) return@use null
                if (!response.isSuccessful) return@use null
                val body = response.body.string().trim()
                if (body.isBlank()) return@use null
                val payload = json.decodeFromString<ActiveAnnouncementEnvelope>(body)
                payload.announcement?.toAppAnnouncement()
            }
        }.getOrNull()
    }
}
