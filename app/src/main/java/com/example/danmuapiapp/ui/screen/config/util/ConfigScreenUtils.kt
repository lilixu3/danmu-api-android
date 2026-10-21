package com.example.danmuapiapp.ui.screen.config


import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun parseCookieSnapshot(rawCookie: String): CookieSnapshot {
    val map = LinkedHashMap<String, String>()
    rawCookie.split(';').forEach { segment ->
        val part = segment.trim()
        val idx = part.indexOf('=')
        if (idx > 0) {
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                map[key] = value
            }
        }
    }
    val hasSessdata = map.keys.any { it.equals("SESSDATA", true) }
    val hasBiliJct = map.keys.any { it.equals("bili_jct", true) }
    return CookieSnapshot(
        keys = map.keys.toList(),
        hasRequired = hasSessdata && hasBiliJct
    )
}

internal fun buildCookieWithRefreshToken(cookie: String?, refreshToken: String?): String {
    val base = cookie?.trim().orEmpty()
    if (base.isBlank()) return ""
    val refresh = refreshToken?.trim().orEmpty()
    if (refresh.isBlank()) return base
    if (base.contains("refresh_token=")) return base
    return if (base.endsWith(";")) "$base refresh_token=$refresh" else "$base; refresh_token=$refresh"
}

internal fun inferCookieExpiryMs(cookie: String): Long? {
    if (cookie.isBlank()) return null

    val sessRaw = Regex("(^|;\\s*)SESSDATA=([^;]+)", RegexOption.IGNORE_CASE)
        .find(cookie)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()

    if (!sessRaw.isNullOrBlank()) {
        val decoded = runCatching { URLDecoder.decode(sessRaw, "UTF-8") }.getOrElse { sessRaw }
        val parts = decoded.split(',')
        if (parts.size >= 2) {
            val ts = parts[1].trim().toLongOrNull()
            val ms = ts?.let {
                when {
                    it < 10_000_000_000L -> it * 1000L
                    it > 10_000_000_000_000L -> it / 1000L
                    else -> it
                }
            }
            if (ms != null && ms > 0L) return ms
        }
    }

    val expires = Regex("(?i)Expires=([^;]+)")
        .find(cookie)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!expires.isNullOrBlank()) {
        return runCatching {
            val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            parser.parse(expires)?.time
        }.getOrNull()
    }
    return null
}

internal fun formatEpochMs(epochMs: Long?): String {
    val value = epochMs ?: return "未知"
    if (value <= 0L) return "未知"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateText = runCatching { sdf.format(Date(value)) }.getOrElse { "未知" }
    val delta = value - System.currentTimeMillis()
    if (delta <= 0L) return "$dateText（已过期）"

    val days = delta / (24 * 60 * 60 * 1000L)
    val hours = (delta / (60 * 60 * 1000L)) % 24
    val tail = when {
        days >= 1 -> "（约 ${days} 天 ${hours} 小时后）"
        hours >= 1 -> "（约 ${hours} 小时后）"
        else -> "（不到 1 小时）"
    }
    return dateText + tail
}

internal fun formatLatencyMs(latencyMs: Long?): String {
    val value = latencyMs ?: return "--"
    if (value <= 0L) return "--"
    return if (value < 1000L) {
        "${value}ms"
    } else {
        String.format(Locale.getDefault(), "%.2fs", value / 1000f)
    }
}

internal fun buildQrBitmap(content: String, sizePx: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}

internal fun parseCsvTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    raw.split(',').forEach { token ->
        val t = token.trim()
        if (t.isNotBlank() && seen.add(t)) {
            out += t
        }
    }
    return out
}

internal fun parsePlatformTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    raw.split(Regex("[&,]"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val normalized = if (token == "*") "all" else token
            val key = normalized.lowercase(Locale.getDefault())
            if (seen.add(key)) out += normalized
        }
    return out
}

internal fun normalizePlatforms(raw: String): String {
    val tokens = parsePlatformTokens(raw)
    if (tokens.isEmpty()) return ""
    return if (tokens.any { it.equals("all", true) }) "all" else tokens.joinToString("&")
}

internal fun normalizePlatformOptions(options: List<String>): List<String> {
    val normalized = mutableListOf<String>()
    val seen = linkedSetOf<String>()
    options.map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { option ->
            val normalizedOption = if (option == "*") "all" else option
            val key = normalizedOption.lowercase(Locale.getDefault())
            if (seen.add(key)) normalized += normalizedOption
        }

    val withAll = if (normalized.any { it.equals("all", true) }) normalized else listOf("all") + normalized
    val all = withAll.firstOrNull { it.equals("all", true) } ?: "all"
    val others = withAll.filterNot { it.equals("all", true) }
    return listOf(all) + others
}

internal fun <T> List<T>.replace(index: Int, value: T): List<T> {
    if (index !in indices) return this
    val list = toMutableList()
    list[index] = value
    return list
}

internal fun String?.ifNullOrBlank(fallback: String): String {
    val value = this?.trim().orEmpty()
    return if (value.isBlank()) fallback else value
}

internal fun cleanCacheAnimeTitle(rawTitle: String): String {
    return rawTitle.orEmpty()
        .replace(Regex("[\\u200B-\\u200F\\uFEFF]"), "")
        .replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*[（(〔\\[]\\s*[0-9０-９]{4}\\s*年?\\s*[）)〕\\]]"), "")
        .replace(Regex("(.+?)\\s*【[^】]+】$"), "\$1")
        .trim()
}

internal fun normalizeNumericInput(raw: String?): String {
    val digits = raw?.trim().orEmpty().filter { it.isDigit() }
    if (digits.isBlank()) return ""
    return digits.toIntOrNull()?.toString() ?: digits
}

internal fun formatIndexedSegment(prefix: String, raw: String?): String {
    val numeric = normalizeNumericInput(raw)
    if (numeric.isBlank()) return ""
    val padded = numeric.toIntOrNull()?.toString()?.padStart(2, '0') ?: numeric
    return prefix.uppercase(Locale.getDefault()) + padded
}
