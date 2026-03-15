package com.example.danmuapiapp.data.service

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

object TvConfigSyncCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun buildInviteUrl(host: String, port: Int, token: String, deviceName: String): String {
        val normalizedHost = host.trim()
        val normalizedToken = token.trim()
        val normalizedDevice = deviceName.trim()
        require(normalizedHost.isNotBlank()) { "局域网地址为空" }
        require(port in 1..65535) { "端口无效" }
        require(normalizedToken.isNotBlank()) { "配对令牌为空" }

        val query = buildString {
            append("token=")
            append(urlEncode(normalizedToken))
            append("&v=1")
            if (normalizedDevice.isNotBlank()) {
                append("&device=")
                append(urlEncode(normalizedDevice))
            }
        }
        return "http://${normalizeHostForUrl(normalizedHost)}:$port/sync/apply?$query"
    }

    fun parseTarget(raw: String): Result<TvConfigSyncTarget> = runCatching {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        require(scheme == "http" || scheme == "https") { "这不是可识别的电视同步码" }

        val host = uri.host?.trim().orEmpty()
        require(host.isNotBlank()) { "同步码缺少电视地址" }

        val path = uri.path?.trim().orEmpty()
        require(path == "/sync/apply") { "同步码路径不正确" }

        val port = when {
            uri.port > 0 -> uri.port
            scheme == "https" -> 443
            else -> 80
        }
        val params = parseQuery(uri.rawQuery)
        val token = params["token"].orEmpty().trim()
        require(token.isNotBlank()) { "同步码缺少配对令牌" }

        val rebuiltUrl = buildString {
            append(scheme)
            append("://")
            append(normalizeHostForUrl(host))
            append(':')
            append(port)
            append("/sync/apply?token=")
            append(urlEncode(token))
            append("&v=1")
        }
        TvConfigSyncTarget(
            applyUrl = rebuiltUrl,
            deviceName = params["device"].orEmpty()
        )
    }

    fun encodePayload(payload: TvConfigSyncPayload): String = json.encodeToString(payload)

    fun decodePayload(text: String): TvConfigSyncPayload = json.decodeFromString(text)

    fun encodeResponse(response: TvConfigSyncResponse): String = json.encodeToString(response)

    fun decodeResponse(text: String): TvConfigSyncResponse = json.decodeFromString(text)

    fun buildQrBitmap(content: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[y * sizePx + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }

    fun decodeQrText(bitmap: Bitmap): Result<String> = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        require(width > 0 && height > 0) { "图片无效" }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        MultiFormatReader().decode(binary).text.orEmpty().trim()
    }.mapCatching { decoded ->
        require(decoded.isNotBlank()) { "未识别到同步码" }
        decoded
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        rawQuery.split('&').forEach { segment ->
            if (segment.isBlank()) return@forEach
            val index = segment.indexOf('=')
            val keyPart = if (index >= 0) segment.substring(0, index) else segment
            val valuePart = if (index >= 0) segment.substring(index + 1) else ""
            val key = urlDecode(keyPart)
            if (key.isBlank()) return@forEach
            out[key] = urlDecode(valuePart)
        }
        return out
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun urlDecode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }

    private fun normalizeHostForUrl(host: String): String {
        val normalized = host.trim()
        return if (normalized.contains(':') && !normalized.startsWith("[")) {
            "[$normalized]"
        } else {
            normalized
        }
    }
}
