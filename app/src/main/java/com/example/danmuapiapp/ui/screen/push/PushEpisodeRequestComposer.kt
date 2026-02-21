package com.example.danmuapiapp.ui.screen.push

import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * 推送请求拼装器：
 * - 保持“临时参数”策略，不写入 .env
 * - 使用 OK 影视可识别参数：offset / fontSize
 */
object PushEpisodeRequestComposer {

    enum class ErrorField {
        TARGET,
        OFFSET,
        FONT,
    }

    data class ValidationError(
        val field: ErrorField,
        val fieldMessage: String,
    )

    data class PreparedInput(
        val target: String,
        val offsetSec: Double,
        val fontOverride: Int?,
    )

    sealed class PrepareResult {
        data class Success(val input: PreparedInput) : PrepareResult()
        data class Error(val error: ValidationError) : PrepareResult()
    }

    data class BuildResult(
        val pushUrl: String,
        val extraHint: String,
    )

    fun prepareInput(
        targetRaw: String,
        offsetRaw: String,
        fontRaw: String,
        envDanmuFontSize: Int?,
    ): PrepareResult {
        val target = targetRaw.trim()
        if (target.isBlank()) {
            return PrepareResult.Error(
                ValidationError(
                    field = ErrorField.TARGET,
                    fieldMessage = "请输入推送地址",
                )
            )
        }

        val offsetText = offsetRaw.trim()
        val offsetSec = if (offsetText.isBlank()) 0.0 else offsetText.toDoubleOrNull()
        if (offsetSec == null) {
            return PrepareResult.Error(
                ValidationError(
                    field = ErrorField.OFFSET,
                    fieldMessage = "时间偏移格式不正确，请输入数字（可为负）",
                )
            )
        }

        val fontText = fontRaw.trim()
        val fontValue = if (fontText.isBlank()) null else fontText.toIntOrNull()
        if (fontText.isNotBlank() && (fontValue == null || fontValue <= 0)) {
            return PrepareResult.Error(
                ValidationError(
                    field = ErrorField.FONT,
                    fieldMessage = "弹幕大小格式不正确，请输入正整数",
                )
            )
        }

        val fontOverride = if (
            fontValue != null &&
            envDanmuFontSize != null &&
            fontValue == envDanmuFontSize
        ) {
            null
        } else {
            fontValue
        }

        return PrepareResult.Success(
            PreparedInput(
                target = target,
                offsetSec = offsetSec,
                fontOverride = fontOverride
            )
        )
    }

    fun buildPushRequest(
        sourceBase: String,
        episodeId: Long,
        input: PreparedInput,
    ): BuildResult {
        val commentUrl = buildString {
            append(sourceBase.trimEnd('/'))
            append("/api/v2/comment/")
            append(episodeId)
            append("?format=xml")

            if (abs(input.offsetSec) > 1e-6) {
                append("&offset=")
                append(formatOffsetSeconds(input.offsetSec))
            }
            if (input.fontOverride != null) {
                append("&fontSize=")
                append(input.fontOverride)
            }
        }

        val pushUrl = buildPushUrl(
            targetInput = input.target,
            sourceUrl = commentUrl
        )

        val extraHint = buildString {
            if (abs(input.offsetSec) > 1e-6) {
                append("偏移")
                append(formatOffsetSeconds(input.offsetSec))
                append("s")
            }
            if (input.fontOverride != null) {
                if (isNotBlank()) append(" · ")
                append("大小")
                append(input.fontOverride)
            }
        }

        return BuildResult(pushUrl = pushUrl, extraHint = extraHint)
    }

    private fun buildPushUrl(targetInput: String, sourceUrl: String): String {
        val targetRaw = targetInput.trim()
        require(targetRaw.isNotBlank()) { "请输入推送地址" }

        val normalizedTarget = ensureHttpPrefix(targetRaw)
        val encodedSource = urlEncode(sourceUrl)
        val pathIndex = normalizedTarget.lowercase(Locale.ROOT).indexOf("path=")

        if (pathIndex >= 0) {
            val prefix = normalizedTarget.substring(0, pathIndex + "path=".length)
            return prefix + encodedSource
        }

        val base = normalizedTarget.trimEnd('/')
        return "$base/action?do=refresh&type=danmaku&path=$encodedSource"
    }

    private fun ensureHttpPrefix(url: String): String {
        val trimmed = url.trim()
        return if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun urlEncode(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name())
    }

    private fun formatOffsetSeconds(value: Double): String {
        val formatted = String.format(Locale.US, "%.3f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }
}
