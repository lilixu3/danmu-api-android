package com.example.danmuapiapp.domain.model

import java.net.URI

enum class AppBackgroundMode(val storageValue: Int) {
    Solid(0),
    LocalImage(1),
    OnlineImage(2),
    RandomOnlineImage(3);

    companion object {
        fun fromStorageValue(value: Int): AppBackgroundMode {
            return entries.firstOrNull { it.storageValue == value } ?: Solid
        }
    }
}

enum class AppBackgroundRefreshPolicy(
    val storageValue: Int,
    val intervalMillis: Long?
) {
    OnForeground(0, null),
    Seconds30(1, 30_000L),
    Minute1(2, 60_000L),
    Minutes3(3, 3 * 60_000L),
    Minutes5(4, 5 * 60_000L),
    Minutes10(5, 10 * 60_000L),
    Custom(6, null);

    companion object {
        fun fromStorageValue(value: Int): AppBackgroundRefreshPolicy {
            return entries.firstOrNull { it.storageValue == value } ?: OnForeground
        }
    }

    fun resolveIntervalMillis(customSeconds: Long): Long? {
        if (this == Custom) {
            if (customSeconds <= 0L) return null
            return if (customSeconds > Long.MAX_VALUE / 1_000L) {
                Long.MAX_VALUE
            } else {
                customSeconds * 1_000L
            }
        }
        return intervalMillis
    }
}

data class AppBackgroundPreference(
    val mode: AppBackgroundMode = AppBackgroundMode.Solid,
    val localImageUri: String = "",
    val onlineImageUrl: String = "",
    val randomImageUrl: String = DEFAULT_RANDOM_IMAGE_URL,
    val randomRefreshPolicy: AppBackgroundRefreshPolicy = AppBackgroundRefreshPolicy.OnForeground,
    val customRandomRefreshSeconds: Long = 0L
) {
    companion object {
        const val DEFAULT_RANDOM_IMAGE_URL = "https://www.loliapi.com/acg/pe"
        const val PICSUM_BACKUP_IMAGE_URL = "https://picsum.photos/1080/1920"
    }
}

fun AppBackgroundPreference.resolveImageData(foregroundKey: Long): String? {
    return when (mode) {
        AppBackgroundMode.Solid -> null
        AppBackgroundMode.LocalImage -> localImageUri.trim().ifBlank { null }
        AppBackgroundMode.OnlineImage -> onlineImageUrl.trim().takeIf(::isValidBackgroundImageUrl)
        AppBackgroundMode.RandomOnlineImage -> randomImageUrl
            .trim()
            .takeIf(::isValidBackgroundImageUrl)
            ?.withRandomForegroundKey(foregroundKey)
    }
}

fun isValidBackgroundImageUrl(value: String): Boolean {
    val uri = runCatching {
        URI(value.trim().replace("{random}", "1"))
    }.getOrNull() ?: return false
    val supportedScheme = uri.scheme.equals("https", ignoreCase = true) ||
        uri.scheme.equals("http", ignoreCase = true)
    return supportedScheme && uri.rawAuthority?.isNotBlank() == true
}

internal fun String.withRandomForegroundKey(foregroundKey: Long): String {
    val token = foregroundKey.toString()
    if (contains("{random}")) {
        return replace("{random}", token)
    }

    val fragmentIndex = indexOf('#')
    val base = if (fragmentIndex >= 0) substring(0, fragmentIndex) else this
    val fragment = if (fragmentIndex >= 0) substring(fragmentIndex) else ""
    val randomParameter = Regex("([?&])random=[^&#]*", RegexOption.IGNORE_CASE)
    val refreshedBase = if (randomParameter.containsMatchIn(base)) {
        randomParameter.replace(base) { match ->
            "${match.groupValues[1]}random=$token"
        }
    } else {
        val separator = when {
            base.endsWith('?') || base.endsWith('&') -> ""
            '?' in base -> "&"
            else -> "?"
        }
        "$base${separator}random=$token"
    }
    return refreshedBase + fragment
}
