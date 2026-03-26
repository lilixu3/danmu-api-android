package com.example.danmuapiapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AnnouncementActionType {
    Link,
    AppRoute;

    companion object {
        fun fromRaw(raw: String?): AnnouncementActionType {
            return when (raw?.trim()?.lowercase()) {
                "app_route", "app" -> AppRoute
                else -> Link
            }
        }
    }
}

enum class AnnouncementSeverity {
    Info,
    Success,
    Warning,
    Danger;

    companion object {
        fun fromRaw(raw: String?): AnnouncementSeverity {
            return when (raw?.trim()?.lowercase()) {
                "success" -> Success
                "warning" -> Warning
                "danger", "error" -> Danger
                else -> Info
            }
        }
    }
}

enum class AnnouncementType {
    Short,
    Long;

    companion object {
        fun fromRaw(raw: String?): AnnouncementType {
            return when (raw?.trim()?.lowercase()) {
                "short" -> Short
                else -> Long
            }
        }
    }
}

data class AnnouncementAction(
    val text: String,
    val url: String,
    val type: AnnouncementActionType = AnnouncementActionType.Link
) {
    fun isAppRoute(): Boolean = type == AnnouncementActionType.AppRoute
    fun routeOrNull(): String? = url.trim().takeIf { isAppRoute() && it.isNotEmpty() }
}

data class AppAnnouncement(
    val id: String,
    val title: String,
    val summary: String,
    val contentPreview: String,
    val contentMarkdown: String,
    val coverImageUrl: String?,
    val announcementType: AnnouncementType,
    val severity: AnnouncementSeverity,
    val forcePopup: Boolean,
    val allowSnoozeToday: Boolean,
    val primaryAction: AnnouncementAction?,
    val secondaryAction: AnnouncementAction?,
    val publishedAt: String?
) {
    fun isShortTerm(): Boolean = announcementType == AnnouncementType.Short
}

@Serializable
data class ActiveAnnouncementEnvelope(
    val announcement: ActiveAnnouncementPayload? = null
)

@Serializable
data class ActiveAnnouncementPayload(
    val id: String,
    val title: String,
    val summary: String = "",
    @SerialName("content_preview")
    val contentPreview: String = "",
    @SerialName("content_markdown")
    val contentMarkdown: String = "",
    @SerialName("cover_image_url")
    val coverImageUrl: String? = null,
    @SerialName("announcement_type")
    val announcementType: String = "long",
    val severity: String = "info",
    @SerialName("force_popup")
    val forcePopup: Boolean = false,
    @SerialName("allow_snooze_today")
    val allowSnoozeToday: Boolean = true,
    @SerialName("primary_action")
    val primaryAction: ActiveAnnouncementActionPayload? = null,
    @SerialName("secondary_action")
    val secondaryAction: ActiveAnnouncementActionPayload? = null,
    @SerialName("published_at")
    val publishedAt: String? = null
) {
    fun toAppAnnouncement(): AppAnnouncement? {
        val normalizedId = id.trim()
        val normalizedTitle = title.trim()
        if (normalizedId.isBlank() || normalizedTitle.isBlank()) return null
        return AppAnnouncement(
            id = normalizedId,
            title = normalizedTitle,
            summary = summary.trim(),
            contentPreview = contentPreview.trim(),
            contentMarkdown = contentMarkdown.trim(),
            coverImageUrl = coverImageUrl?.trim()?.takeIf { it.isNotEmpty() },
            announcementType = AnnouncementType.fromRaw(announcementType),
            severity = AnnouncementSeverity.fromRaw(severity),
            forcePopup = forcePopup,
            allowSnoozeToday = allowSnoozeToday,
            primaryAction = primaryAction?.toAction(),
            secondaryAction = secondaryAction?.toAction(),
            publishedAt = publishedAt?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

@Serializable
data class ActiveAnnouncementActionPayload(
    val text: String = "",
    val url: String = "",
    val type: String = "link"
) {
    fun toAction(): AnnouncementAction? {
        val normalizedText = text.trim()
        val normalizedUrl = url.trim()
        if (normalizedText.isBlank() || normalizedUrl.isBlank()) return null
        return AnnouncementAction(
            text = normalizedText,
            url = normalizedUrl,
            type = AnnouncementActionType.fromRaw(type)
        )
    }
}
